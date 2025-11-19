"""Brandnew 모션 추론 서비스 - 새로운 모델 전용."""

from __future__ import annotations

import logging
import os
from functools import lru_cache
from pathlib import Path
from typing import Sequence

import numpy as np
import torch

from app.services.inference import (
    InferenceResult,
    MotionGCNCNN,
    PoseExtractor,
)

LOGGER = logging.getLogger(__name__)


class BrandnewMotionInferenceService:
    """
    Brandnew 모델 전용 추론 서비스

    기존 모델과 클래스 매핑이 다르므로 별도 구현 필요:

    Brandnew 모델 클래스 순서:
      0: CLAP, 1: ELBOW, 2: EXIT, 3: STAY, 4: STRETCH, 5: TILT, 6: UNDERARM

    기존 모델 클래스 순서:
      0: CLAP, 1: ELBOW, 2: STRETCH, 3: TILT, 4: EXIT, 5: UNDERARM, 6: STAY
    """

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

        # Brandnew 모델용 매핑
        # DB actionCode → Model class_index
        self.ACTION_CODE_TO_CLASS_INDEX = {
            1: 0,  # 손 박수 → CLAP
            2: 1,  # 팔 치기 → ELBOW
            4: 4,  # 팔 뻗기 → STRETCH
            5: 5,  # 기우뚱 → TILT
            6: 2,  # 비상구 → EXIT
            7: 6,  # 겨드랑이박수 → UNDERARM
            9: 3,  # 가만히 있음 → STAY
        }

        # Model class_index → DB actionCode
        self.CLASS_INDEX_TO_ACTION_CODE = {
            0: 1,  # CLAP → 손 박수
            1: 2,  # ELBOW → 팔 치기
            2: 6,  # EXIT → 비상구
            3: 9,  # STAY → 가만히 있음
            4: 4,  # STRETCH → 팔 뻗기
            5: 5,  # TILT → 기우뚱
            6: 7,  # UNDERARM → 겨드랑이박수
        }

    def predict(
        self,
        frames: Sequence[str],
        target_action_name: str | None = None,
        target_action_code: int | None = None,
    ) -> InferenceResult:
        """프레임 시퀀스를 받아 동작 예측 수행"""
        if not frames:
            raise ValueError("프레임 데이터가 비어 있습니다.")

        # 기존 inference.py의 로직 재사용
        from app.services.inference import MotionInferenceService

        # 임시 서비스 생성 (전처리용)
        temp_service = MotionInferenceService.__new__(MotionInferenceService)
        temp_service.frames_per_sample = self.frames_per_sample
        temp_service.pose_extractor = self.pose_extractor
        temp_service.device = self.device

        sampled_frames = temp_service._sample_frames(frames, self.frames_per_sample)
        keypoint_sequence, decode_time_s, pose_time_s = temp_service._frames_to_keypoints(
            sampled_frames
        )

        LOGGER.info("🔍 Brandnew - Keypoint sequence shape: %s", keypoint_sequence.shape)

        input_tensor = torch.from_numpy(keypoint_sequence).unsqueeze(0)
        input_tensor = input_tensor.to(self.device)

        with torch.no_grad():
            from time import perf_counter

            inference_start = perf_counter()
            logits = self.model(input_tensor)
            inference_time_ms = (perf_counter() - inference_start) * 1000
            probabilities = torch.softmax(logits, dim=-1).cpu().numpy()[0]

            LOGGER.info("🔍 Brandnew - Logits: %s", logits.cpu().numpy()[0])
            LOGGER.info("🔍 Brandnew - Probabilities: %s", probabilities)
            LOGGER.info("🔍 Brandnew - Class mapping: %s", self.id_to_label)

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

        # actionCode 변환 (Brandnew 매핑 사용)
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
        """확률 기반 점수 계산"""
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
        """목표 확률이 없을 때 폴백 점수 계산"""
        if not target_action:
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
            if confidence >= 0.90:
                return 3
            if confidence >= 0.75:
                return 2
            if confidence >= 0.60:
                return 1
            return 0
        else:
            return 0


@lru_cache(maxsize=1)
def get_brandnew_inference_service() -> BrandnewMotionInferenceService:
    """Brandnew 모델을 사용하는 추론 서비스 반환."""
    model_path = Path(__file__).resolve().parent.parent / "brandnewTrain" / "checkpoints" / "brandnew_model_v1.pt"

    if not model_path.exists():
        raise FileNotFoundError(f"Brandnew 모델 파일을 찾을 수 없습니다: {model_path}")

    device_override = os.getenv("MOTION_INFERENCE_DEVICE")
    LOGGER.info("Loading brandnew model from: %s", model_path)

    return BrandnewMotionInferenceService(model_path=model_path, device=device_override)
