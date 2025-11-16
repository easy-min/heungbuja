"""기존 image_inference.py 스크립트 로직을 백엔드에서 재사용하도록 래핑한 모듈."""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import Mapping, Sequence, TYPE_CHECKING

from .constants import BODY_LANDMARK_INDICES
from .model_loader import ModelArtifacts, ModelLoaderError, get_motion_model

if TYPE_CHECKING:  # pragma: no cover - 정적 타입 체크 용도
    import numpy as np  # type: ignore[import-not-found]

logger = logging.getLogger(__name__)

# 성능 측정용 타이머 컨텍스트
class Timer:
    """간단한 타이머 유틸리티"""
    def __init__(self, name: str):
        self.name = name
        self.start = 0.0
        self.duration = 0.0

    def __enter__(self):
        self.start = time.time()
        return self

    def __exit__(self, *args):
        self.duration = time.time() - self.start
        logger.debug(f"  └─ {self.name}: {self.duration*1000:.2f}ms")


DEFAULT_LABEL_MAP: Mapping[str, str] = {
    "CLAP": "박수 동작",
    "ELBOW": "팔꿈치 동작",
    "HIP": "엉덩이 동작",
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


def _import_numpy():
    try:
        import numpy as np  # type: ignore[import-not-found]
    except ModuleNotFoundError as exc:  # pragma: no cover - 설치 환경 의존
        raise PoseExtractionError(
            "NumPy 패키지가 설치되어 있지 않습니다. `pip install numpy` 명령으로 설치해 주세요."
        ) from exc
    return np


def _import_torch():
    try:
        import torch  # type: ignore[import-not-found]
    except ModuleNotFoundError as exc:  # pragma: no cover - 설치 환경 의존
        raise ModelLoaderError(
            "PyTorch가 설치되어 있지 않습니다. `pip install torch` 명령으로 설치해 주세요."
        ) from exc
    return torch


def _resolve_device(device_arg: str):
    torch_module = _import_torch()
    normalized = device_arg.lower()

    if normalized in {"auto", "cuda"}:
        if not torch_module.cuda.is_available():
            raise RuntimeError("CUDA 장치를 사용할 수 없습니다. GPU 환경을 확인해 주세요.")
        return torch_module.device("cuda")

    if normalized.startswith("cuda"):
        return torch_module.device(normalized)

    if normalized == "cpu":
        logger.warning("GPU 사용이 요청되었지만 'cpu'가 명시되었습니다. CPU 장치를 그대로 사용합니다.")
        return torch_module.device("cpu")

    return torch_module.device(device_arg)


def extract_landmarks_from_image(
    image_path: Path | str,
    *,
    scale_factor: int = 1, # 프론트에서 보내는 로직을 좀 키움
    padding_ratio: float = 0.2,
    model_complexity: int = 1, # 2에서 1로 모델의 정확도를 줄임
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


def _prepare_sequence(landmarks: "np.ndarray", sequence_length: int) -> "np.ndarray":
    np = _import_numpy()
    selected = landmarks[BODY_LANDMARK_INDICES, :]
    window = np.tile(selected[None, :, :], (sequence_length, 1, 1))
    return window.astype(np.float32)


def _prepare_sequence_from_landmark_list(
    landmark_list: Sequence["np.ndarray"],
    sequence_length: int,
) -> "np.ndarray":
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


def _run_inference(
    sequence: "np.ndarray",
    *,
    device: str = "cuda",
) -> tuple["np.ndarray", ModelArtifacts]:
    torch_module = _import_torch()
    np = _import_numpy()

    artifacts = get_motion_model()
    model = artifacts.model

    target_device = _resolve_device(device)
    parameter_sample = next(model.parameters(), None)
    current_device = parameter_sample.device if parameter_sample is not None else target_device
    if current_device != target_device:
        model = model.to(target_device)

    tensor = torch_module.from_numpy(sequence).permute(0, 2, 1).unsqueeze(0).to(target_device)

    logger.debug("Running inference on device %s with input shape %s", target_device, tuple(tensor.shape))

    with torch_module.no_grad():
        logits = model(tensor)
        probs = torch_module.softmax(logits, dim=1).cpu().numpy()[0]

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
    if not frame_paths:
        raise PoseExtractionError("프레임이 제공되지 않았습니다.")

    np = _import_numpy()

    # MediaPipe Pose 랜드마크 추출 (가장 느린 부분)
    landmark_start = time.time()
    landmarks_list = [extract_landmarks_from_image(path) for path in frame_paths]
    landmark_duration = time.time() - landmark_start
    logger.debug(f"  └─ MediaPipe extraction: {landmark_duration*1000:.2f}ms for {len(frame_paths)} frames")

    # 시퀀스 준비
    seq_start = time.time()
    sequence = _prepare_sequence_from_landmark_list(landmarks_list, sequence_length=sequence_length)
    seq_duration = time.time() - seq_start
    logger.debug(f"  └─ Sequence preparation: {seq_duration*1000:.2f}ms")

    # PyTorch 모델 추론
    inference_start = time.time()
    probabilities, artifacts = _run_inference(sequence, device=device)
    inference_duration = time.time() - inference_start
    logger.debug(f"  └─ PyTorch inference: {inference_duration*1000:.2f}ms")

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


