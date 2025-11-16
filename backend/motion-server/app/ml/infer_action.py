"""
학습된 동작 분류 모델을 사용하여 동작을 추론하는 모듈 및 스크립트

모듈로 사용:
    from app.ml.infer_action import extract_landmarks_from_image, predict_action_from_frames

스크립트로 사용:
    python -m app.ml.infer_action --model_path ./app/trained_model/best_model.pt --data_dir ./data
    
또는:
    cd backend/motion-server
    python -m app.ml.infer_action --data_dir ./data
"""

from __future__ import annotations

import argparse
import logging
import os
from pathlib import Path
from typing import Dict, List, Tuple, Mapping, Sequence, TYPE_CHECKING
import sys
import re

# MediaPipe/TensorFlow 로그 억제
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
os.environ['MP_VERBOSE'] = '0'

try:
    import absl.logging
    absl.logging.set_verbosity(absl.logging.ERROR)
except ImportError:
    pass

if TYPE_CHECKING:  # pragma: no cover - 정적 타입 체크 용도
    import numpy as np  # type: ignore[import-not-found]

import torch
import numpy as np
from tqdm import tqdm
from collections import defaultdict

# motion-server 모듈 import
# 스크립트로 실행될 때를 위한 경로 설정
if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).parent.parent.parent))
    from app.ml.models import PoseGCNTemporalModel
    from app.ml.constants import BODY_LANDMARK_INDICES
    from app.ml.model_loader import ModelArtifacts, ModelLoaderError, get_motion_model
else:
    # 모듈로 import될 때는 상대 import 사용
    from .models import PoseGCNTemporalModel
    from .constants import BODY_LANDMARK_INDICES
    from .model_loader import ModelArtifacts, ModelLoaderError, get_motion_model

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

DEFAULT_LABEL_MAP: Mapping[str, str] = {
    "CLAP": "박수 동작",
    "ELBOW": "팔꿈치 동작",
    "UNDERARM": "겨드랑이 동작",
    "STRETCH": "팔 뻗기 동작",
    "TILT": "기울이기 동작",
}


class PoseExtractionError(RuntimeError):
    """MediaPipe Pose 추출 과정에서 발생하는 예외."""


def _import_cv2():
    try:
        import cv2  # type: ignore[import-not-found]
    except ModuleNotFoundError as exc:  # pragma: no cover - 설치 환경 의존
        raise PoseExtractionError(
            "OpenCV(cv2) 패키지가 설치되어 있지 않습니다. `pip install opencv-python` 명령으로 설치해 주세요."
        ) from exc
    return cv2


def _import_mediapipe():
    try:
        import mediapipe as mp  # type: ignore[import-not-found]
    except ModuleNotFoundError as exc:  # pragma: no cover - 설치 환경 의존
        raise PoseExtractionError(
            "MediaPipe 패키지가 설치되어 있지 않습니다. `pip install mediapipe` 명령으로 설치해 주세요."
        ) from exc
    return mp


def _resolve_device(device_arg: str):
    normalized = device_arg.lower()

    if normalized in {"auto", "cuda"}:
        if not torch.cuda.is_available():
            if normalized == "auto":
                if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
                    return torch.device("mps")
                return torch.device("cpu")
            raise RuntimeError("CUDA 장치를 사용할 수 없습니다. GPU 환경을 확인해 주세요.")
        return torch.device("cuda")

    if normalized.startswith("cuda"):
        return torch.device(normalized)

    if normalized == "mps":
        if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
            return torch.device("mps")
        logger.warning("MPS를 사용할 수 없어 CPU를 사용합니다.")
        return torch.device("cpu")

    if normalized == "cpu":
        return torch.device("cpu")

    return torch.device(device_arg)


def extract_landmarks_from_image(
    image_path: Path | str,
    *,
    scale_factor: int = 1,
    padding_ratio: float = 0.2,
    model_complexity: int = 1,
    min_detection_confidence: float = 0.2,
) -> "np.ndarray":
    """단일 이미지에서 MediaPipe Pose 랜드마크를 추출합니다."""
    cv2 = _import_cv2()
    np = _import_numpy()
    mp = _import_mediapipe()

    path = Path(image_path)
    image = cv2.imread(str(path))
    if image is None:
        raise PoseExtractionError(f"이미지를 불러올 수 없습니다: {path}")

    if scale_factor > 1:
        image = cv2.resize(
            image,
            (image.shape[1] * scale_factor, image.shape[0] * scale_factor),
            interpolation=cv2.INTER_LINEAR,
        )

    pad = int(padding_ratio * max(image.shape[0], image.shape[1]))
    if pad > 0:
        image = cv2.copyMakeBorder(
            image,
            pad,
            pad,
            pad,
            pad,
            borderType=cv2.BORDER_CONSTANT,
            value=(0, 0, 0),
        )

    with mp.solutions.pose.Pose(
        static_image_mode=True,
        model_complexity=model_complexity,
        enable_segmentation=False,
        min_detection_confidence=min_detection_confidence,
    ) as pose:
        rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        results = pose.process(rgb_image)

    if not results.pose_landmarks:
        raise PoseExtractionError("MediaPipe Pose가 인체를 탐지하지 못했습니다.")

    landmarks = np.array([[lm.x, lm.y] for lm in results.pose_landmarks.landmark], dtype=np.float32)
    return landmarks


def _import_numpy():
    try:
        import numpy as np  # type: ignore[import-not-found]
    except ModuleNotFoundError as exc:  # pragma: no cover - 설치 환경 의존
        raise PoseExtractionError(
            "NumPy 패키지가 설치되어 있지 않습니다. `pip install numpy` 명령으로 설치해 주세요."
        ) from exc
    return np


def _prepare_sequence(landmarks: "np.ndarray", sequence_length: int) -> "np.ndarray":
    """단일 랜드마크를 시퀀스로 변환"""
    np = _import_numpy()
    selected = landmarks[BODY_LANDMARK_INDICES, :]
    window = np.tile(selected[None, :, :], (sequence_length, 1, 1))
    return window.astype(np.float32)


def _prepare_sequence_from_landmark_list(
    landmark_list: Sequence["np.ndarray"],
    sequence_length: int,
) -> "np.ndarray":
    """랜드마크 리스트를 시퀀스로 변환 (모듈용)"""
    if not landmark_list:
        raise PoseExtractionError("시퀀스를 구성할 랜드마크가 없습니다.")

    if len(landmark_list) == 1:
        return _prepare_sequence(landmark_list[0], sequence_length)

    np = _import_numpy()
    stacked = np.stack([landmarks[BODY_LANDMARK_INDICES, :] for landmarks in landmark_list], axis=0)

    frame_count = stacked.shape[0]
    if frame_count < sequence_length:
        pad_count = sequence_length - frame_count
        pad_source = stacked[-1:, :, :]
        padding = np.repeat(pad_source, pad_count, axis=0)
        stacked = np.concatenate([stacked, padding], axis=0)
    elif frame_count > sequence_length:
        indices = np.linspace(0, frame_count - 1, num=sequence_length, dtype=int)
        stacked = stacked[indices, :, :]

    return stacked.astype(np.float32)


def prepare_sequence_from_landmark_list(landmark_list: List[np.ndarray], sequence_length: int = 32) -> np.ndarray:
    """
    랜드마크 리스트를 시퀀스로 변환 (스크립트용 - 선형 보간 포함)
    """
    if not landmark_list:
        raise ValueError("랜드마크 리스트가 비어있습니다.")

    num_body_landmarks = len(BODY_LANDMARK_INDICES)
    
    # 1개만 있으면 32번 복사
    if len(landmark_list) == 1:
        selected = landmark_list[0]
        return np.tile(selected[None, :, :], (sequence_length, 1, 1)).astype(np.float32)

    # 여러 개 있으면 보간
    landmark_array = np.stack(landmark_list, axis=0)  # (N, 22, 2)
    N = len(landmark_list)

    if N >= sequence_length:
        # 프레임이 충분하면 균등 샘플링
        indices = np.linspace(0, N - 1, sequence_length, dtype=int)
        return landmark_array[indices].astype(np.float32)
    else:
        # 프레임이 부족하면 선형 보간
        indices = np.linspace(0, N - 1, sequence_length)
        int_indices = indices.astype(int)
        frac_indices = indices - int_indices

        result = np.zeros((sequence_length, num_body_landmarks, 2), dtype=np.float32)
        for i in range(sequence_length):
            idx = int_indices[i]
            frac = frac_indices[i]

            if idx + 1 < N:
                result[i] = (1 - frac) * landmark_array[idx] + frac * landmark_array[idx + 1]
            else:
                result[i] = landmark_array[idx]

        return result


def _run_inference(
    sequence: "np.ndarray",
    *,
    device: str = "cuda",
) -> tuple["np.ndarray", ModelArtifacts]:
    """모델을 사용하여 시퀀스로부터 추론 수행"""
    np = _import_numpy()

    artifacts = get_motion_model()
    model = artifacts.model

    target_device = _resolve_device(device)
    parameter_sample = next(model.parameters(), None)
    current_device = parameter_sample.device if parameter_sample is not None else target_device
    if current_device != target_device:
        model = model.to(target_device)

    tensor = torch.from_numpy(sequence).permute(0, 2, 1).unsqueeze(0).to(target_device)

    logger.debug("Running inference on device %s with input shape %s", target_device, tuple(tensor.shape))

    with torch.no_grad():
        logits = model(tensor)
        probs = torch.softmax(logits, dim=1).cpu().numpy()[0]

    return np.asarray(probs, dtype=np.float32), artifacts


def predict_action_from_image(
    image_path: Path | str,
    *,
    sequence_length: int = 32,
    device: str = "cuda",
    label_map: Mapping[str, str] | None = None,
    top_k: int = 2,
) -> dict:
    """기존 image_inference.py와 동일한 방식으로 단일 이미지의 동작을 추론합니다."""
    return predict_action_from_frames(
        [image_path],
        sequence_length=sequence_length,
        device=device,
        label_map=label_map,
        top_k=top_k,
    )


def predict_action_from_frames(
    frame_paths: Sequence[Path | str],
    *,
    sequence_length: int = 32,
    device: str = "cuda",
    label_map: Mapping[str, str] | None = None,
    top_k: int = 2,
) -> dict:
    """다중 프레임으로부터 동작을 추론합니다."""
    if not frame_paths:
        raise PoseExtractionError("프레임이 제공되지 않았습니다.")

    np = _import_numpy()

    landmarks_list = [extract_landmarks_from_image(path) for path in frame_paths]
    sequence = _prepare_sequence_from_landmark_list(landmarks_list, sequence_length=sequence_length)
    probabilities, artifacts = _run_inference(sequence, device=device)

    top_k = max(1, top_k)
    top_indices = np.argsort(probabilities)[::-1][:top_k]

    effective_label_map = label_map if label_map is not None else DEFAULT_LABEL_MAP
    predictions = []
    for idx in top_indices:
        int_idx = int(idx)
        label = artifacts.index_to_label.get(int_idx, str(int_idx))
        description = effective_label_map.get(label, label)
        predictions.append(
            {
                "class_index": int_idx,
                "class_label": label,
                "description": description,
                "confidence": float(probabilities[int_idx]),
                "confidence_percent": float(probabilities[int_idx] * 100.0),
            }
        )

    top_prediction = predictions[0]
    logger.info(
        "Top prediction - class: %s, description: %s, confidence: %.2f%%",
        top_prediction["class_label"],
        top_prediction["description"],
        top_prediction["confidence_percent"],
    )

    return {
        "predictions": predictions,
        "top_prediction": {
            "class_label": top_prediction["class_label"],
            "description": top_prediction["description"],
            "confidence": top_prediction["confidence"],
            "confidence_percent": top_prediction["confidence_percent"],
        },
        "probabilities": probabilities.tolist(),
        "label_to_index": artifacts.label_to_index,
        "sequence_length": sequence_length,
    }


def load_image_groups(data_dir: Path, frames_per_sample: int = 8) -> List[Tuple[List[Path], str, str]]:
    """
    Data 폴더에서 이미지 그룹 로드
    
    Returns:
        List of (image_paths, person_name, action_name) tuples
    """
    data_dir = Path(data_dir)
    if not data_dir.exists():
        raise ValueError(f"Data directory not found: {data_dir}")

    image_groups = []
    
    # data/{사람명}/{동작명}/ 구조
    person_folders = [d for d in data_dir.iterdir() if d.is_dir()]
    
    for person_folder in sorted(person_folders):
        person_name = person_folder.name
        action_folders = [d for d in person_folder.iterdir() if d.is_dir()]
        
        for action_folder in sorted(action_folders):
            action_name = action_folder.name
            
            # 이미지 파일 찾기
            images = sorted(
                list(action_folder.glob("*.jpg")) + 
                list(action_folder.glob("*.png")) + 
                list(action_folder.glob("*.jpeg"))
            )
            
            if not images:
                continue
            
            # 시퀀스 번호별로 그룹화
            sequence_groups = defaultdict(list)
            
            for img_path in images:
                match = re.search(r'_seq(\d+)_frame', img_path.name)
                if match:
                    seq_num = match.group(1)
                    sequence_groups[seq_num].append(img_path)
                else:
                    base_name = img_path.stem.rsplit('_frame', 1)[0] if '_frame' in img_path.stem else img_path.stem
                    sequence_groups[base_name].append(img_path)
            
            # 각 시퀀스에서 frames_per_sample 개씩 그룹화
            for seq_key in sorted(sequence_groups.keys()):
                seq_images = sorted(sequence_groups[seq_key])
                
                if len(seq_images) < frames_per_sample:
                    continue
                
                # 처음 frames_per_sample 개 사용
                image_group = seq_images[:frames_per_sample]
                image_groups.append((image_group, person_name, action_name))
    
    return image_groups


def infer_actions(
    model_path: Path,
    data_dir: Path,
    frames_per_sample: int = 8,
    sequence_length: int = 32,
    device: str = "auto",
    top_k: int = 3,
):
    """
    모델을 사용하여 이미지들의 동작을 추론
    """
    # 디바이스 설정
    if device == "auto":
        if torch.cuda.is_available():
            device = torch.device("cuda")
        elif hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
            device = torch.device("mps")
        else:
            device = torch.device("cpu")
    else:
        device = torch.device(device)
    
    logger.info(f"Using device: {device}")
    
    # 모델 로드
    logger.info(f"Loading model from: {model_path}")
    checkpoint = torch.load(model_path, map_location="cpu")
    
    label_to_index = checkpoint["label_to_index"]
    num_classes = len(label_to_index)
    index_to_label = {v: k for k, v in label_to_index.items()}
    
    logger.info(f"Model classes: {list(label_to_index.keys())}")
    
    # 모델 생성 및 로드
    model = PoseGCNTemporalModel(num_classes=num_classes, in_channels=2)
    model.load_state_dict(checkpoint["model_state_dict"])
    model = model.to(device)
    model.eval()
    
    logger.info(f"Model loaded successfully (Epoch: {checkpoint.get('epoch', 'N/A')}, Val Acc: {checkpoint.get('val_acc', 'N/A'):.2f}%)")
    
    # 이미지 그룹 로드
    logger.info(f"\nLoading images from: {data_dir}")
    image_groups = load_image_groups(data_dir, frames_per_sample)
    logger.info(f"Found {len(image_groups)} sequences to process")
    
    # 추론 수행
    logger.info(f"\nRunning inference...")
    results = []
    correct = 0
    total = 0
    action_stats = defaultdict(lambda: {"correct": 0, "total": 0, "predictions": defaultdict(int)})
    
    with torch.no_grad():
        for image_paths, person_name, true_action in tqdm(image_groups, desc="Inferring"):
            try:
                # 랜드마크 추출
                landmarks_list = []
                for image_path in image_paths:
                    landmarks = extract_landmarks_from_image(image_path)
                    
                    # 랜드마크 배열이 충분한지 확인
                    if len(landmarks) < 33:
                        padded_landmarks = np.zeros((33, 2), dtype=np.float32)
                        padded_landmarks[:len(landmarks)] = landmarks
                        landmarks = padded_landmarks
                    
                    # BODY_LANDMARK_INDICES에 해당하는 랜드마크 선택
                    num_body_landmarks = len(BODY_LANDMARK_INDICES)
                    if len(landmarks) > max(BODY_LANDMARK_INDICES):
                        selected = landmarks[BODY_LANDMARK_INDICES, :]
                    else:
                        selected = np.zeros((num_body_landmarks, 2), dtype=np.float32)
                        valid_indices = [idx for idx in BODY_LANDMARK_INDICES if idx < len(landmarks)]
                        if valid_indices:
                            selected[:len(valid_indices)] = landmarks[valid_indices, :]
                    
                    landmarks_list.append(selected)
                
                # 시퀀스 생성
                sequence = prepare_sequence_from_landmark_list(landmarks_list, sequence_length)
                sequence_tensor = torch.from_numpy(sequence).float()
                sequence_tensor = sequence_tensor.permute(0, 2, 1).unsqueeze(0).to(device)  # (1, 32, 2, 22)
                
                # 추론
                outputs = model(sequence_tensor)
                probs = torch.softmax(outputs, dim=1).cpu().numpy()[0]
                
                # Top-K 예측
                top_indices = np.argsort(probs)[-top_k:][::-1]
                top_probs = probs[top_indices]
                top_labels = [index_to_label[idx] for idx in top_indices]
                
                predicted_action = top_labels[0]
                confidence = top_probs[0] * 100
                
                # 결과 저장
                result = {
                    "person": person_name,
                    "true_action": true_action,
                    "predicted_action": predicted_action,
                    "confidence": confidence,
                    "top_k": list(zip(top_labels, top_probs * 100)),
                }
                results.append(result)
                
                # 정확도 계산
                total += 1
                action_stats[true_action]["total"] += 1
                action_stats[true_action]["predictions"][predicted_action] += 1
                
                if predicted_action == true_action:
                    correct += 1
                    action_stats[true_action]["correct"] += 1
                    
            except Exception as e:
                logger.warning(f"Failed to process {person_name}/{true_action}: {e}")
                continue
    
    # 결과 출력
    logger.info(f"\n{'='*70}")
    logger.info(f"Inference Results")
    logger.info(f"{'='*70}")
    logger.info(f"Total sequences: {total}")
    logger.info(f"Correct: {correct}")
    logger.info(f"Accuracy: {100.0 * correct / total:.2f}%")
    logger.info(f"{'='*70}\n")
    
    # 동작별 정확도
    logger.info(f"Per-action Accuracy:")
    logger.info(f"{'='*70}")
    for action in sorted(action_stats.keys()):
        stats = action_stats[action]
        acc = 100.0 * stats["correct"] / stats["total"] if stats["total"] > 0 else 0.0
        logger.info(f"  {action:15s}: {stats['correct']:4d}/{stats['total']:4d} ({acc:5.2f}%)")
    logger.info(f"{'='*70}\n")
    
    # 혼동 행렬
    logger.info(f"Confusion Matrix (Top predictions per action):")
    logger.info(f"{'='*70}")
    for action in sorted(action_stats.keys()):
        predictions = action_stats[action]["predictions"]
        top_pred = max(predictions.items(), key=lambda x: x[1]) if predictions else ("N/A", 0)
        logger.info(f"  {action:15s} -> {top_pred[0]:15s} ({top_pred[1]:4d} samples)")
    logger.info(f"{'='*70}\n")
    
    # 샘플 결과 출력
    logger.info(f"Sample Results (first 10):")
    logger.info(f"{'='*70}")
    for i, result in enumerate(results[:10]):
        status = "✓" if result["predicted_action"] == result["true_action"] else "✗"
        logger.info(f"{status} {result['person']:5s}/{result['true_action']:10s} -> {result['predicted_action']:10s} ({result['confidence']:5.2f}%)")
    logger.info(f"{'='*70}")


def main():
    parser = argparse.ArgumentParser(description="Infer actions from images using trained model")
    parser.add_argument("--model_path", type=str, default="./app/trained_model/best_model.pt", help="모델 파일 경로")
    parser.add_argument("--data_dir", type=str, default="./data", help="데이터 디렉토리 경로")
    parser.add_argument("--frames_per_sample", type=int, default=8, help="샘플당 프레임 수")
    parser.add_argument("--sequence_length", type=int, default=32, help="시퀀스 길이")
    parser.add_argument("--device", type=str, default="auto", help="디바이스 (cuda/mps/cpu/auto)")
    parser.add_argument("--top_k", type=int, default=3, help="Top-K 예측 출력")
    args = parser.parse_args()
    
    infer_actions(
        model_path=Path(args.model_path),
        data_dir=Path(args.data_dir),
        frames_per_sample=args.frames_per_sample,
        sequence_length=args.sequence_length,
        device=args.device,
        top_k=args.top_k,
    )


if __name__ == "__main__":
    main()

