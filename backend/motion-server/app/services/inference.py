"""모션 추론 모델 및 전처리 유틸리티."""

from __future__ import annotations

import base64
import logging
import os
from dataclasses import dataclass
from functools import lru_cache
from io import BytesIO
from pathlib import Path
from time import perf_counter
from typing import Iterable, List, Sequence, Tuple

import mediapipe as mp
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image

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


class GraphConvLayer(nn.Module):
    """간단한 GCN 레이어."""

    def __init__(self, num_nodes: int, in_features: int, out_features: int) -> None:
        super().__init__()
        self.register_parameter(
            "adjacency", nn.Parameter(torch.eye(num_nodes, dtype=torch.float32))
        )
        self.linear = nn.Linear(in_features, out_features)
        self.norm = nn.LayerNorm(out_features)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (B, T, N, F)
        adjacency = torch.softmax(self.adjacency, dim=-1)
        aggregated = torch.einsum("nj,btjf->btnf", adjacency, x)
        out = self.linear(aggregated)
        out = self.norm(out)
        return F.relu(out)


class TemporalCNN(nn.Module):
    """시계열 패턴 추출용 1D CNN 블록."""

    def __init__(
        self,
        in_channels: int,
        channel_sizes: Sequence[int],
        dropout: float,
    ) -> None:
        super().__init__()
        layers: List[nn.Module] = []
        input_channels = in_channels
        for out_channels in channel_sizes:
            layers.append(nn.Conv1d(input_channels, out_channels, kernel_size=3, padding=1))
            layers.append(nn.BatchNorm1d(out_channels))
            layers.append(nn.ReLU(inplace=True))
            if dropout > 0:
                layers.append(nn.Dropout(dropout))
            input_channels = out_channels
        self.network = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.network(x)


class MotionGCNCNN(nn.Module):
    """GCN + Temporal CNN 기반 모션 분류기."""

    def __init__(
        self,
        num_nodes: int,
        input_dim: int,
        gcn_hidden_dims: Sequence[int],
        temporal_channels: Sequence[int],
        num_classes: int,
        dropout: float,
    ) -> None:
        super().__init__()
        self.gcn_layers = nn.ModuleList()
        prev_dim = input_dim
        for hidden_dim in gcn_hidden_dims:
            self.gcn_layers.append(GraphConvLayer(num_nodes, prev_dim, hidden_dim))
            prev_dim = hidden_dim

        self.temporal_cnn = TemporalCNN(prev_dim, temporal_channels, dropout)
        self.temporal_pool = nn.AdaptiveAvgPool1d(1)
        classifier_layers: List[nn.Module] = [
            nn.Linear(temporal_channels[-1], temporal_channels[-1]),
            nn.ReLU(inplace=True),
            nn.Dropout(dropout),
            nn.Linear(temporal_channels[-1], num_classes),
        ]
        self.classifier = nn.Sequential(*classifier_layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (B, T, N, F)
        for layer in self.gcn_layers:
            x = layer(x)

        # 노드 평균으로 그래프 임베딩 생성
        x = torch.mean(x, dim=2)  # (B, T, H)
        x = x.permute(0, 2, 1)  # (B, H, T)
        x = self.temporal_cnn(x)
        x = self.temporal_pool(x).squeeze(-1)
        logits = self.classifier(x)
        return logits


class PoseExtractor:
    """Mediapipe Pose를 활용한 2D 관절 좌표 추출기."""

    # 선택한 랜드마크 인덱스 (총 22개)
    SELECTED_LANDMARKS = [
        mp.solutions.pose.PoseLandmark.LEFT_SHOULDER,
        mp.solutions.pose.PoseLandmark.LEFT_ELBOW,
        mp.solutions.pose.PoseLandmark.LEFT_WRIST,
        mp.solutions.pose.PoseLandmark.LEFT_THUMB,
        mp.solutions.pose.PoseLandmark.LEFT_PINKY,
        mp.solutions.pose.PoseLandmark.LEFT_HIP,
        mp.solutions.pose.PoseLandmark.LEFT_KNEE,
        mp.solutions.pose.PoseLandmark.LEFT_ANKLE,
        mp.solutions.pose.PoseLandmark.LEFT_HEEL,
        mp.solutions.pose.PoseLandmark.LEFT_FOOT_INDEX,
        mp.solutions.pose.PoseLandmark.MOUTH_LEFT,
        mp.solutions.pose.PoseLandmark.NOSE,
        mp.solutions.pose.PoseLandmark.RIGHT_SHOULDER,
        mp.solutions.pose.PoseLandmark.RIGHT_ELBOW,
        mp.solutions.pose.PoseLandmark.RIGHT_WRIST,
        mp.solutions.pose.PoseLandmark.RIGHT_THUMB,
        mp.solutions.pose.PoseLandmark.RIGHT_PINKY,
        mp.solutions.pose.PoseLandmark.RIGHT_HIP,
        mp.solutions.pose.PoseLandmark.RIGHT_KNEE,
        mp.solutions.pose.PoseLandmark.RIGHT_ANKLE,
        mp.solutions.pose.PoseLandmark.RIGHT_HEEL,
        mp.solutions.pose.PoseLandmark.RIGHT_FOOT_INDEX,
    ]

    ROOT_INDICES = [
        SELECTED_LANDMARKS.index(mp.solutions.pose.PoseLandmark.LEFT_HIP),
        SELECTED_LANDMARKS.index(mp.solutions.pose.PoseLandmark.RIGHT_HIP),
    ]

    def __init__(self) -> None:
        self._pose = mp.solutions.pose.Pose(
            static_image_mode=True,
            model_complexity=1,
            enable_segmentation=False,
            min_detection_confidence=0.5,
        )

    def extract(self, image: np.ndarray) -> np.ndarray:
        results = self._pose.process(image)
        if not results.pose_landmarks:
            return np.zeros((len(self.SELECTED_LANDMARKS), 2), dtype=np.float32)

        landmarks = results.pose_landmarks.landmark
        coords = []
        for idx in self.SELECTED_LANDMARKS:
            lm = landmarks[idx]
            coords.append((lm.x, lm.y))
        keypoints = np.array(coords, dtype=np.float32)
        return self._normalize(keypoints)

    def _normalize(self, keypoints: np.ndarray) -> np.ndarray:
        if not np.any(keypoints):
            return keypoints

        valid_points = keypoints[self.ROOT_INDICES]
        if np.all(valid_points == 0):
            center = np.mean(keypoints, axis=0)
        else:
            center = np.mean(valid_points, axis=0)

        keypoints -= center

        max_distance = np.linalg.norm(keypoints, axis=1).max()
        if max_distance > 0:
            keypoints /= max_distance

        return keypoints


class MotionInferenceService:
    """학습된 모델을 로드하여 프레임 시퀀스를 판정 점수로 변환."""

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

        LOGGER.info("Motion inference device: %s", self.device)
        self.frames_per_sample = int(args.get("frames_per_sample", 8))
        self.class_mapping = {label.upper(): index for label, index in class_mapping.items()}
        self.id_to_label = {index: label for label, index in self.class_mapping.items()}

        gcn_hidden_dims = args.get("gcn_hidden_dims", [64, 128])
        temporal_channels = args.get("temporal_channels", [128, 256])
        dropout = float(args.get("dropout", 0.3))

        self.model = MotionGCNCNN(
            num_nodes=checkpoint["model_state_dict"]["gcn_layers.0.adjacency"].shape[0],
            input_dim=checkpoint["model_state_dict"]["gcn_layers.0.linear.weight"].shape[1],
            gcn_hidden_dims=gcn_hidden_dims,
            temporal_channels=temporal_channels,
            num_classes=len(self.class_mapping),
            dropout=dropout,
        )
        self.model.load_state_dict(checkpoint["model_state_dict"])
        self.model.to(self.device)
        self.model.eval()

        self.pose_extractor = PoseExtractor()

    def predict(
        self,
        frames: Sequence[str],
        target_action_name: str | None = None,
        target_action_code: int | None = None,
    ) -> InferenceResult:
        if not frames:
            raise ValueError("프레임 데이터가 비어 있습니다.")

        sampled_frames = self._sample_frames(frames, self.frames_per_sample)
        keypoint_sequence, decode_time_s, pose_time_s = self._frames_to_keypoints(
            sampled_frames
        )

        # 🔍 디버깅: 입력 데이터 확인
        LOGGER.info("🔍 디버깅 - Keypoint sequence shape: %s", keypoint_sequence.shape)
        LOGGER.info("🔍 디버깅 - Keypoint stats - mean: %.4f, std: %.4f, min: %.4f, max: %.4f",
                   keypoint_sequence.mean(), keypoint_sequence.std(),
                   keypoint_sequence.min(), keypoint_sequence.max())

        input_tensor = torch.from_numpy(keypoint_sequence).unsqueeze(0)  # (1, T, N, 2)
        input_tensor = input_tensor.to(self.device)

        with torch.no_grad():
            inference_start = perf_counter()
            logits = self.model(input_tensor)
            inference_time_ms = (perf_counter() - inference_start) * 1000
            probabilities = torch.softmax(logits, dim=-1).cpu().numpy()[0]

            # 🔍 디버깅: 모델 출력 확인
            LOGGER.info("🔍 디버깅 - Logits: %s", logits.cpu().numpy()[0])
            LOGGER.info("🔍 디버깅 - Probabilities: %s", probabilities)
            LOGGER.info("🔍 디버깅 - Class mapping: %s", self.id_to_label)

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
            "🎯 AI 판정 결과 - 목표동작=%s(code=%s), 예측동작=%s(신뢰도=%.1f%%), "
            "목표확률=%.1f%%, 점수=%d점 | ⏱️ 총=%.0fms (디코딩=%.0fms, Pose추출=%.0fms, 추론=%.0fms)",
            target_action_name,
            target_action_code,
            predicted_label,
            confidence * 100,
            (target_probability * 100) if target_probability else 0,
            judgment,
            total_time_ms,
            decode_time_ms,
            pose_time_ms,
            inference_time_ms,
        )

        # ========================================================================
        # ⚠️ CRITICAL: Convert model index back to DB actionCode when returning
        # ========================================================================
        # Model class_index → DB actionCode 역매핑
        CLASS_INDEX_TO_ACTION_CODE = {
            0: 1,  # CLAP → 손 박수
            1: 2,  # ELBOW → 팔 치기
            2: 4,  # STRETCH → 팔 뻗기
            3: 5,  # TILT → 기우뚱
            4: 6,  # EXIT → 비상구
            5: 7,  # UNDERARM → 겨드랑이박수
            6: 9,  # STAY → 가만히 있음
        }
        # ========================================================================
        if target_action_code is not None:
            resolved_action_code = target_action_code
        else:
            # 모델의 best_idx를 올바른 actionCode로 변환
            resolved_action_code = CLASS_INDEX_TO_ACTION_CODE.get(best_idx, best_idx + 1)

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
        # ========================================================================
        # ⚠️ CRITICAL: DB actionCode → Model class_index Mapping
        # ========================================================================
        # DB의 actionCode와 모델의 class_index는 1:1 매핑이 아닙니다!
        # 일부 DB 동작은 모델에 학습되지 않았습니다.
        #
        # Model에 학습된 동작 (7개):
        #   class 0: CLAP (손 박수)
        #   class 1: ELBOW (팔 치기)
        #   class 2: STRETCH (팔 뻗기)
        #   class 3: TILT (기우뚱)
        #   class 4: EXIT (비상구)
        #   class 5: UNDERARM (겨드랑이박수)
        #   class 6: STAY (가만히 있음)
        #
        # DB actionCode → Model class_index 매핑:
        ACTION_CODE_TO_CLASS_INDEX = {
            1: 0,  # 손 박수 → CLAP
            2: 1,  # 팔 치기 → ELBOW
            # 3: None,  # 엉덩이 박수 (데이터 없음)
            4: 2,  # 팔 뻗기 → STRETCH
            5: 3,  # 기우뚱 → TILT
            6: 4,  # 비상구 → EXIT
            7: 5,  # 겨드랑이박수 → UNDERARM
            # 8: None,  # 팔 모으기 (학습 안 함)
            9: 6,  # 가만히 있음 → STAY
        }
        # ========================================================================

        if action_code is not None:
            # DB actionCode를 Model class_index로 변환
            model_index = ACTION_CODE_TO_CLASS_INDEX.get(action_code)
            if model_index is not None and model_index in self.id_to_label:
                return model_index

        if action_name:
            key = action_name.strip().upper()
            return self.class_mapping.get(key)

        return None

    @staticmethod
    def _score_by_probability(probability: float) -> int:
        # ========================================================================
        # ⚠️ CRITICAL: Stricter scoring criteria to prevent high scores for
        # incorrect or minimal movements
        # ========================================================================
        # Previous issue: 51% threshold was too lenient
        # - Even when stationary, model could predict 51-60% → judgment=2 (66.7점)
        # - Resulted in unfair scores: moving=75점 vs stationary=73점
        #
        # New stricter criteria:
        # - 90%+ → 3점 (Perfect, 100점)
        # - 75%+ → 2점 (Good, 66.7점)
        # - 60%+ → 1점 (Needs improvement, 33.3점)
        # - <60% → 0점 (Incorrect or no movement, 0점)
        # ========================================================================
        if probability >= 0.90:
            return 3
        if probability >= 0.75:
            return 2
        if probability >= 0.60:
            return 1
        return 0

    def _fallback_score(
        self, predicted_label: str, confidence: float, target_action: str | None
    ) -> int:
        # ========================================================================
        # Fallback scoring when target_probability is not available
        # Aligned with stricter _score_by_probability criteria
        # ========================================================================
        if not target_action:
            # No target specified - use general confidence thresholds
            if confidence >= 0.90:
                return 3
            if confidence >= 0.75:
                return 2
            if confidence >= 0.60:
                return 1
            return 0

        target_key = target_action.strip().upper()
        predicted_key = predicted_label.strip().upper()

        if target_key == predicted_key:
            # Predicted correctly - use confidence thresholds
            if confidence >= 0.90:
                return 3
            if confidence >= 0.75:
                return 2
            if confidence >= 0.60:
                return 1
            return 0
        else:
            # Predicted incorrectly - always 0 points
            return 0

    # ========================================================================
    # ⚠️ CRITICAL: Filter out invalid frames (zero vectors) to prevent
    # meaningless predictions when person is not detected by Mediapipe
    # ========================================================================
    # Issue: When user doesn't move or is out of frame, Mediapipe returns
    # zero vectors, but model still predicts with low confidence (~15-20%)
    # This causes unfair scoring where "no movement" gets ~33-50 points!
    #
    # Solution: Only use frames where person is actually detected
    # Require minimum 3 valid frames to ensure reliable prediction
    # ========================================================================
    def _frames_to_keypoints(self, frames: Iterable[str]) -> Tuple[np.ndarray, float, float]:
        keypoints = []
        valid_count = 0
        total_count = 0

        decode_elapsed = 0.0
        pose_elapsed = 0.0
        for encoded in frames:
            total_count += 1
            decode_start = perf_counter()
            image = self._decode_base64_image(encoded)
            decode_elapsed += perf_counter() - decode_start

            pose_start = perf_counter()
            coords = self.pose_extractor.extract(image)
            pose_elapsed += perf_counter() - pose_start

            # Skip zero vectors (person not detected)
            if np.any(coords):
                keypoints.append(coords)
                valid_count += 1

        # Require at least 5 valid frames for reliable prediction
        # (Increased from 3 to reduce false positives when stationary)
        MIN_VALID_FRAMES = 5
        if valid_count < MIN_VALID_FRAMES:
            raise ValueError(
                f"유효한 동작 프레임이 부족합니다 ({valid_count}/{total_count}개). "
                f"카메라에 전신이 보이도록 해주세요."
            )

        LOGGER.info(
            "📹 프레임 분석: 유효=%d개, 전체=%d개, 필터링=%d개 (영벡터 제외)",
            valid_count, total_count, total_count - valid_count
        )

        keypoint_array = np.stack(keypoints, axis=0).astype(np.float32)
        return keypoint_array, decode_elapsed, pose_elapsed

    def _sample_frames(self, frames: Sequence[str], target_count: int) -> List[str]:
        if len(frames) == target_count:
            return list(frames)

        if len(frames) < target_count:
            padding = [frames[-1]] * (target_count - len(frames))
            return list(frames) + padding

        indices = np.linspace(0, len(frames) - 1, target_count).astype(int)
        return [frames[i] for i in indices]

    @staticmethod
    def _decode_base64_image(data: str) -> np.ndarray:
        try:
            image_data = base64.b64decode(data)
        except base64.binascii.Error as exc:
            raise ValueError("Base64 디코딩에 실패했습니다.") from exc

        with Image.open(BytesIO(image_data)) as img:
            rgb_image = img.convert("RGB")
            return np.array(rgb_image)


@lru_cache(maxsize=1)
def get_inference_service() -> MotionInferenceService:
    model_path = Path(__file__).resolve().parent.parent / "trained_model" / "gcn_cnn_best.pt"
    device_override = os.getenv("MOTION_INFERENCE_DEVICE")
    return MotionInferenceService(model_path=model_path, device=device_override)


