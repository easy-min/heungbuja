"""Model loading utilities for the motion-server."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict

from .models import PoseGCNTemporalModel

logger = logging.getLogger(__name__)

MODEL_FILENAME = "best_model.pt"
MODEL_PATH = Path(__file__).resolve().parent.parent / "trained_model" / MODEL_FILENAME


class ModelLoaderError(RuntimeError):
    """Raised when the motion model cannot be loaded."""


@dataclass(frozen=True)
class ModelArtifacts:
    """모델과 레이블 매핑 정보를 함께 보관."""

    model: Any
    label_to_index: Dict[str, int]
    index_to_label: Dict[int, str]


def _import_torch() -> Any:
    """Import torch lazily to avoid hard dependency during module import."""
    try:
        import torch  # type: ignore[import-not-found]
    except ModuleNotFoundError as exc:  # pragma: no cover - executed only when torch is missing
        raise ModelLoaderError(
            "PyTorch가 설치되어 있지 않습니다. `pip install torch` 명령으로 설치해 주세요."
        ) from exc

    return torch


def _build_index_to_label(mapping: Dict[str, int]) -> Dict[int, str]:
    return {idx: label for label, idx in mapping.items()}


def _load_model(model_path: Path) -> ModelArtifacts:
    """Load the motion recognition model from disk."""
    if not model_path.exists():
        raise ModelLoaderError(f"모델 파일을 찾을 수 없습니다: {model_path}")

    torch_module = _import_torch()
    logger.debug("Loading motion model from %s", model_path)
    checkpoint = torch_module.load(model_path, map_location="cpu")

    if not isinstance(checkpoint, dict):
        raise ModelLoaderError("체크포인트 형식이 올바르지 않습니다. state_dict 딕셔너리를 기대합니다.")

    try:
        label_to_index: Dict[str, int] = dict(checkpoint["label_to_index"])
        state_dict = checkpoint["model_state_dict"]
    except KeyError as exc:
        raise ModelLoaderError(f"체크포인트에 필요한 키가 없습니다: {exc}") from exc

    if not isinstance(state_dict, dict):
        raise ModelLoaderError("model_state_dict 값이 딕셔너리가 아닙니다.")

    model = PoseGCNTemporalModel(num_classes=len(label_to_index), in_channels=2)
    model.load_state_dict(state_dict)
    model.eval()

    index_to_label = _build_index_to_label(label_to_index)
    return ModelArtifacts(model=model, label_to_index=label_to_index, index_to_label=index_to_label)


@lru_cache(maxsize=1)
def get_motion_model() -> ModelArtifacts:
    """Return the cached motion recognition model instance."""
    return _load_model(MODEL_PATH)

