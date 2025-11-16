"""모션 인식 모델 관련 유틸리티."""

# infer_action.py에서 직접 import (inference.py는 하위 호환성을 위한 래퍼)
from .infer_action import (
    PoseExtractionError,
    extract_landmarks_from_image,
    predict_action_from_frames,
    predict_action_from_image,
)
from .model_loader import get_motion_model

__all__ = [
    "get_motion_model",
    "extract_landmarks_from_image",
    "predict_action_from_image",
    "predict_action_from_frames",
    "PoseExtractionError",
]

