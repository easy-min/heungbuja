"""동작 추론(inference) API 라우터."""

from __future__ import annotations

import base64
import logging
from pathlib import Path
from tempfile import NamedTemporaryFile

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.ml import PoseExtractionError, predict_action_from_frames
from app.ml.model_loader import ModelLoaderError

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/inference", tags=["inference"])
analyze_router = APIRouter(prefix="/api/ai", tags=["inference"])

ACTION_CODE_TO_LABEL = {
    1: "CLAP",
    2: "ELBOW",
    3: "HIP",
    4: "STRETCH",
    5: "TILT"
}
ACTION_CODE_TO_DESCRIPTION = {
    1: "손 박수",
    2: "팔 치기",
    3: "엉덩이 치기",
    4: "팔 뻗기",
    5: "기울기기"
}


class InferenceRequest(BaseModel):
    """다중 프레임 기반 추론 요청 스키마."""

    action_code: int = Field(..., alias="actionCode")
    action_name: str = Field(..., alias="actionName")
    frame_count: int = Field(..., alias="frameCount")
    frames: list[str]

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    @field_validator("frames")
    @classmethod
    def _validate_frames(cls, value: list[str], info):
        if not value:
            raise ValueError("frames 배열이 비어 있습니다.")
        return value


def _make_temp_image_file(data: bytes, suffix: str = ".jpg") -> Path:
    with NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(data)
        return Path(tmp.name)


def _decode_base64_image(data: str) -> bytes:
    header, _, encoded = data.partition(",")
    payload = encoded if _ else data
    try:
        return base64.b64decode(payload, validate=True)
    except (base64.binascii.Error, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="frameData가 올바른 Base64 형식이 아닙니다.",
        ) from exc


@router.post("/predict/base64")
async def predict_motion_action_base64(payload: InferenceRequest) -> dict:
    """Base64 인코딩된 이미지 데이터를 받아 동작을 추론합니다."""
    sequence_length = 32
    top_k = 2

    frame_paths: list[Path] = []
    try:
        try:
            expected_label = ACTION_CODE_TO_LABEL[payload.action_code]
        except KeyError as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"지원하지 않는 actionCode입니다: {payload.action_code}",
            ) from exc

        if payload.frame_count != len(payload.frames):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"frameCount({payload.frame_count})가 frames 길이({len(payload.frames)})와 일치하지 않습니다.",
            )

        for encoded_frame in payload.frames:
            image_bytes = _decode_base64_image(encoded_frame)
            frame_paths.append(_make_temp_image_file(image_bytes))

        logger.debug(
            "Running inference for action_code=%s, frame_count=%d (sequence_length=%d, top_k=%d)",
            payload.action_code,
            payload.frame_count,
            sequence_length,
            top_k,
        )

        result = predict_action_from_frames(
            frame_paths=frame_paths,
            sequence_length=sequence_length,
            top_k=top_k,
            device="cuda",
        )

        label_to_index = result.get("label_to_index", {})
        if expected_label not in label_to_index:
            logger.error("Expected label %s not present in label_to_index", expected_label)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"예상 레이블({expected_label})을 확인할 수 없습니다.",
            )

        expected_index = label_to_index[expected_label]
        probabilities = result.get("probabilities", [])
        try:
            expected_probability = float(probabilities[expected_index])
        except (IndexError, TypeError, ValueError) as exc:
            logger.error("Cannot read probability for expected label %s: %s", expected_label, exc)
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="예상 레이블 확률 계산 중 오류가 발생했습니다.",
            ) from exc

        top_prediction = result.get("top_prediction", {})
        top_prediction_label = top_prediction.get("class_label", "UNKNOWN")
        top_prediction_confidence = float(top_prediction.get("confidence", 0.0))

        logger.info(
            "Inference summary (actionCode=%s) expected=%s(prob=%.4f) top=%s(prob=%.4f)",
            payload.action_code,
            expected_label,
            expected_probability,
            top_prediction_label,
            top_prediction_confidence,
        )

        if expected_probability >= 0.9:
            return {"judgment": 3}
        if expected_probability < 0.5:
            return {"judgment": 1}
        return {"judgment": 2}
    except (PoseExtractionError, ModelLoaderError) as exc:
        logger.error("Inference failed (422): %s", exc, exc_info=True)
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except RuntimeError as exc:
        logger.exception("Runtime error during inference: %s", exc)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc
    finally:
        for path in frame_paths:
            try:
                path.unlink()
            except OSError:
                logger.warning("Failed to delete temporary file %s", path)


@analyze_router.post("/analyze")
async def analyze_motion_action(payload: InferenceRequest) -> dict:
    return await predict_motion_action_base64(payload)


