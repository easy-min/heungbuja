"""Brandnew 모션 추론 서비스 - 독립적인 구현 (test_server_simulation.py 기반)"""

from __future__ import annotations

import base64
import logging
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from time import perf_counter
from typing import List, Sequence

import cv2
import mediapipe as mp
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

LOGGER = logging.getLogger(__name__)


@dataclass
class InferenceResult:
    predicted_label: str
    confidence: float
    judgment: int
    decode_time_ms: float
    pose_time_ms: float
    inference_time_ms: float
    action_code: int | None
    target_probability: float | None = None


# ============================================================================
# 모델 구조 정의 (test_server_simulation.py와 완전히 동일)
# ============================================================================

class GCNLayer(nn.Module):
    """GCN 레이어 - adjacency를 그대로 사용 (softmax 없음!)"""

    def __init__(self, in_features: int, out_features: int, adjacency: torch.Tensor, dropout: float = 0.0):
        super().__init__()
        self.linear = nn.Linear(in_features, out_features)
        self.dropout = nn.Dropout(dropout)
        self.norm = nn.LayerNorm(out_features)
        # ✅ adjacency를 buffer로 등록 (학습 안 됨, checkpoint에서 로드된 값 그대로 사용)
        self.register_buffer("adjacency", adjacency)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # ✅ softmax 없이 그대로 사용!
        agg = torch.einsum("ij,btnf->btif", self.adjacency, x)
        out = self.linear(agg)
        out = self.dropout(out)
        out = self.norm(out)
        return out


class TemporalCNN(nn.Module):
    """시계열 CNN 블록"""

    def __init__(self, in_channels: int, hidden_channels: Sequence[int], kernel_size: int = 3, dropout: float = 0.2):
        super().__init__()
        layers: List[nn.Module] = []
        prev = in_channels
        padding = kernel_size // 2
        for channels in hidden_channels:
            layers.extend([
                nn.Conv1d(prev, channels, kernel_size=kernel_size, padding=padding),
                nn.BatchNorm1d(channels),
                nn.ReLU(inplace=True),
                nn.Dropout(dropout),
            ])
            prev = channels
        self.network = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = self.network(x)
        return out.mean(dim=-1)


class GCNTemporalModel(nn.Module):
    """GCN + Temporal CNN 모델 (학습 코드와 동일한 구조)"""

    def __init__(self, input_dim: int, num_classes: int, adjacency: torch.Tensor,
                 gcn_hidden_dims: Sequence[int] = (64, 128),
                 temporal_channels: Sequence[int] = (128, 256),
                 dropout: float = 0.3) -> None:
        super().__init__()
        self.gcn_layers = nn.ModuleList()
        prev_dim = input_dim
        for hidden_dim in gcn_hidden_dims:
            self.gcn_layers.append(GCNLayer(prev_dim, hidden_dim, adjacency, dropout=dropout))
            prev_dim = hidden_dim

        self.temporal_cnn = TemporalCNN(prev_dim, temporal_channels, dropout=dropout)
        temporal_out_dim = temporal_channels[-1] if temporal_channels else prev_dim

        self.classifier = nn.Sequential(
            nn.Linear(temporal_out_dim, temporal_out_dim),
            nn.ReLU(inplace=True),
            nn.Dropout(dropout),
            nn.Linear(temporal_out_dim, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        for gcn in self.gcn_layers:
            x = F.relu(gcn(x))
        x = x.mean(dim=2)
        x = x.permute(0, 2, 1)
        features = self.temporal_cnn(x)
        logits = self.classifier(features)
        return logits


# ============================================================================
# MediaPipe Pose 추출기
# ============================================================================

class PoseExtractor:
    """MediaPipe Pose 추출"""

    def __init__(self) -> None:
        self._pose = mp.solutions.pose.Pose(
            static_image_mode=True,
            model_complexity=1,
            enable_segmentation=False,
            min_detection_confidence=0.5,
        )


# ============================================================================
# Brandnew 추론 서비스
# ============================================================================

class BrandnewMotionInferenceService:
    """Brandnew 모델 전용 추론 서비스 - 독립적인 구현"""

    def __init__(self, model_path: Path, device: str | None = None) -> None:
        checkpoint = torch.load(model_path, map_location="cpu", weights_only=False)
        args = checkpoint.get("args", {})
        class_mapping = checkpoint.get("class_mapping", {})

        if device:
            requested = torch.device(device)
            if requested.type == "cuda" and not torch.cuda.is_available():
                raise RuntimeError("CUDA 장치가 요청되었지만 사용 가능하지 않습니다.")
            self.device = requested
        else:
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

        LOGGER.info("Brandnew model inference device: %s", self.device)
        self.frames_per_sample = int(args.get("frames_per_sample", 8))
        self.class_mapping = {label.upper(): index for label, index in class_mapping.items()}
        self.id_to_label = {index: label for label, index in self.class_mapping.items()}

        LOGGER.info("Brandnew model class mapping: %s", self.id_to_label)

        # 모델 파라미터
        gcn_hidden_dims = args.get("gcn_hidden_dims", [64, 128])
        temporal_channels = args.get("temporal_channels", [128, 256])
        dropout = float(args.get("dropout", 0.3))
        adjacency = checkpoint["model_state_dict"]["gcn_layers.0.adjacency"]

        # ✅ 독립적인 모델 구조 사용!
        self.model = GCNTemporalModel(
            input_dim=checkpoint["model_state_dict"]["gcn_layers.0.linear.weight"].shape[1],
            num_classes=len(class_mapping),
            adjacency=adjacency,
            gcn_hidden_dims=gcn_hidden_dims,
            temporal_channels=temporal_channels,
            dropout=dropout,
        )
        self.model.load_state_dict(checkpoint["model_state_dict"])
        self.model.to(self.device)
        self.model.eval()

        self.pose_extractor = PoseExtractor()

        # DB actionCode → Model class_index 매핑 (실제 모델 기준으로 수정 필요)
        # 실제 모델: 0: ELBOW, 1: EXIT, 2: STAY, 3: STRETCH, 4: TILT, 5: UNDERARM
        self.ACTION_CODE_TO_CLASS_INDEX = {
            2: self.class_mapping.get("ELBOW"),      # 팔 치기 → ELBOW
            4: self.class_mapping.get("STRETCH"),    # 팔 뻗기 → STRETCH
            5: self.class_mapping.get("TILT"),       # 기우뚱 → TILT
            6: self.class_mapping.get("EXIT"),       # 비상구 → EXIT
            7: self.class_mapping.get("UNDERARM"),   # 겨드랑이박수 → UNDERARM
            9: self.class_mapping.get("STAY"),       # 가만히 있음 → STAY
        }

        # Model class_index → DB actionCode (역매핑)
        self.CLASS_INDEX_TO_ACTION_CODE = {}
        for action_code, class_idx in self.ACTION_CODE_TO_CLASS_INDEX.items():
            if class_idx is not None:
                self.CLASS_INDEX_TO_ACTION_CODE[class_idx] = action_code

        LOGGER.info("Brandnew ACTION_CODE mapping: %s", self.ACTION_CODE_TO_CLASS_INDEX)

    def predict(
        self,
        frames: Sequence[str],
        target_action_name: str | None = None,
        target_action_code: int | None = None,
    ) -> InferenceResult:
        """프레임 시퀀스를 받아 동작 예측 수행"""
        if not frames:
            raise ValueError("프레임 데이터가 비어 있습니다.")

        # 전처리: 학습과 동일한 방식으로 전체 시퀀스 정규화
        sampled_frames = self._sample_frames(frames, self.frames_per_sample)
        keypoint_sequence, decode_time_s, pose_time_s = self._frames_to_keypoints(sampled_frames)

        LOGGER.info("🔍 Brandnew - Keypoint sequence shape: %s", keypoint_sequence.shape)

        input_tensor = torch.from_numpy(keypoint_sequence).unsqueeze(0)
        input_tensor = input_tensor.to(self.device)

        with torch.no_grad():
            inference_start = perf_counter()
            logits = self.model(input_tensor)
            inference_time_ms = (perf_counter() - inference_start) * 1000
            probabilities = torch.softmax(logits, dim=-1).cpu().numpy()[0]

            LOGGER.info("🔍 Brandnew - Logits: %s", logits.cpu().numpy()[0])
            LOGGER.info("🔍 Brandnew - Probabilities: %s", probabilities)

        decode_time_ms = decode_time_s * 1000
        pose_time_ms = pose_time_s * 1000

        best_idx = int(np.argmax(probabilities))
        predicted_label = self.id_to_label.get(best_idx, "UNKNOWN")
        confidence = float(probabilities[best_idx])

        target_index = self._resolve_target_index(target_action_name, target_action_code)
        target_probability: float | None = None
        if target_index is not None and 0 <= target_index < len(probabilities):
            target_probability = float(probabilities[target_index])
            judgment = self._score_by_probability(target_probability)
        else:
            judgment = self._fallback_score(predicted_label, confidence, target_action_name)

        total_time_ms = decode_time_ms + pose_time_ms + inference_time_ms
        LOGGER.info(
            "🎯 Brandnew AI 판정 - 목표=%s(code=%s), 예측=%s(%.1f%%), "
            "목표확률=%.1f%%, 점수=%d점 | ⏱️ 총=%.0fms",
            target_action_name,
            target_action_code,
            predicted_label,
            confidence * 100,
            (target_probability * 100) if target_probability else 0,
            judgment,
            total_time_ms,
        )

        # actionCode 변환
        if target_action_code is not None:
            resolved_action_code = target_action_code
        else:
            resolved_action_code = self.CLASS_INDEX_TO_ACTION_CODE.get(best_idx, best_idx + 1)

        return InferenceResult(
            predicted_label=predicted_label,
            confidence=confidence,
            judgment=judgment,
            action_code=resolved_action_code,
            decode_time_ms=decode_time_ms,
            pose_time_ms=pose_time_ms,
            inference_time_ms=inference_time_ms,
            target_probability=target_probability,
        )

    def _resolve_target_index(
        self, action_name: str | None, action_code: int | None
    ) -> int | None:
        """목표 동작을 모델 클래스 인덱스로 변환"""
        if action_code is not None:
            model_index = self.ACTION_CODE_TO_CLASS_INDEX.get(action_code)
            if model_index is not None and model_index in self.id_to_label:
                return model_index

        if action_name:
            key = action_name.strip().upper()
            return self.class_mapping.get(key)

        return None

    @staticmethod
    def _score_by_probability(probability: float) -> int:
        """확률 기반 점수 계산 (완화된 기준)"""
        if probability >= 0.80:  # 90 → 80
            return 3
        if probability >= 0.60:  # 75 → 60
            return 2
        if probability >= 0.40:  # 60 → 40
            return 1
        return 0

    def _fallback_score(
        self, predicted_label: str, confidence: float, target_action: str | None
    ) -> int:
        """목표 확률이 없을 때 폴백 점수 계산 (완화된 기준)"""
        if not target_action:
            if confidence >= 0.80:  # 90 → 80
                return 3
            if confidence >= 0.60:  # 75 → 60
                return 2
            if confidence >= 0.40:  # 60 → 40
                return 1
            return 0

        target_key = target_action.strip().upper()
        predicted_key = predicted_label.strip().upper()

        if target_key == predicted_key:
            if confidence >= 0.80:  # 90 → 80
                return 3
            if confidence >= 0.60:  # 75 → 60
                return 2
            if confidence >= 0.40:  # 60 → 40
                return 1
            return 0
        else:
            return 0

    def _frames_to_keypoints(self, frames: Sequence[str]):
        """프레임을 키포인트 시퀀스로 변환 (test_server_simulation.py와 동일)"""
        raw_landmarks_list = []
        decode_elapsed = 0.0
        pose_elapsed = 0.0
        valid_count = 0
        total_count = 0

        for encoded in frames:
            total_count += 1

            # 이미지 디코딩 (cv2 방식)
            decode_start = perf_counter()
            try:
                image_data = base64.b64decode(encoded)
            except Exception as exc:
                raise ValueError("Base64 디코딩에 실패했습니다.") from exc

            nparr = np.frombuffer(image_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

            if image is None:
                raise ValueError("이미지 디코딩에 실패했습니다.")

            image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            decode_elapsed += perf_counter() - decode_start

            # Pose 추출
            pose_start = perf_counter()
            results = self.pose_extractor._pose.process(image_rgb)
            pose_elapsed += perf_counter() - pose_start

            if not results.pose_landmarks:
                continue

            landmarks = results.pose_landmarks.landmark
            all_coords = np.array([(lm.x, lm.y) for lm in landmarks], dtype=np.float32)
            raw_landmarks_list.append(all_coords)
            valid_count += 1

        # 최소 프레임 체크
        MIN_VALID_FRAMES = 5
        if valid_count < MIN_VALID_FRAMES:
            raise ValueError(
                f"유효한 동작 프레임이 부족합니다 ({valid_count}/{total_count}개). "
                f"카메라에 전신이 보이도록 해주세요."
            )

        LOGGER.info("📹 Brandnew - 프레임 분석: 유효=%d개, 전체=%d개", valid_count, total_count)

        # (T, 33, 2) 형태로 스택
        raw_sequence = np.stack(raw_landmarks_list, axis=0)

        # 전체 시퀀스를 한 번에 정규화
        normalized_sequence = self._normalize_sequence(raw_sequence)

        return normalized_sequence, decode_elapsed, pose_elapsed

    @staticmethod
    def _normalize_sequence(landmarks_sequence: np.ndarray) -> np.ndarray:
        """
        시퀀스 전체를 정규화 (test_server_simulation.py와 완전히 동일)

        Args:
            landmarks_sequence: (T, 33, 2) raw landmarks

        Returns:
            (T, 22, 2) normalized body keypoints
        """
        HIP_INDICES = (23, 24)
        USED_LANDMARK_INDICES = list(range(11, 33))

        coords = landmarks_sequence[..., :2]
        pelvis = (coords[:, HIP_INDICES[0], :] + coords[:, HIP_INDICES[1], :]) / 2.0
        coords = coords - pelvis[:, None, :]

        body_coords = coords[:, USED_LANDMARK_INDICES, :]
        max_range = np.max(np.linalg.norm(body_coords, axis=-1, ord=2))
        if max_range < 1e-6:
            max_range = 1.0
        body_coords = body_coords / max_range

        return body_coords.astype(np.float32)

    def _sample_frames(self, frames: Sequence[str], target_count: int):
        """프레임 샘플링"""
        if len(frames) == target_count:
            return list(frames)

        if len(frames) < target_count:
            padding = [frames[-1]] * (target_count - len(frames))
            return list(frames) + padding

        indices = np.linspace(0, len(frames) - 1, target_count).astype(int)
        return [frames[i] for i in indices]


@lru_cache(maxsize=1)
def get_brandnew_inference_service() -> BrandnewMotionInferenceService:
    """Brandnew 모델을 사용하는 추론 서비스 반환."""
    model_path = Path(__file__).resolve().parent.parent / "brandnewTrain" / "checkpoints" / "brandnew_model_v2.pt"

    if not model_path.exists():
        raise FileNotFoundError(f"Brandnew 모델 파일을 찾을 수 없습니다: {model_path}")

    device_override = os.getenv("MOTION_INFERENCE_DEVICE")
    LOGGER.info("Loading brandnew model from: %s", model_path)

    return BrandnewMotionInferenceService(model_path=model_path, device=device_override)
