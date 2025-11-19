"""Brandnew 모션 추론 서비스 - 새로운 모델 전용."""

from __future__ import annotations

import logging
import os
from functools import lru_cache
from pathlib import Path

from app.services.inference import MotionInferenceService

LOGGER = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def get_brandnew_inference_service() -> MotionInferenceService:
    """Brandnew 모델을 사용하는 추론 서비스 반환."""
    model_path = Path(__file__).resolve().parent.parent / "brandnewTrain" / "checkpoints" / "brandnew_model_v1.pt"

    if not model_path.exists():
        raise FileNotFoundError(f"Brandnew 모델 파일을 찾을 수 없습니다: {model_path}")

    device_override = os.getenv("MOTION_INFERENCE_DEVICE")
    LOGGER.info("Loading brandnew model from: %s", model_path)

    return MotionInferenceService(model_path=model_path, device=device_override)
