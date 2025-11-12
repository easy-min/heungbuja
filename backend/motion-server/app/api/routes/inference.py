"""동작 추론(inference) API 라우터."""

from __future__ import annotations

import base64
import logging
from pathlib import Path
from tempfile import NamedTemporaryFile

from fastapi import APIRouter, File, HTTPException, UploadFile, status
from pydantic import BaseModel, ConfigDict, Field

from app.ml import PoseExtractionError, predict_action_from_image
from app.ml.model_loader import ModelLoaderError

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/inference", tags=["inference"])


class InferenceRequest(BaseModel):
    """Base64 인코딩된 이미지로 추론 요청을 받기 위한 스키마."""

    session_id: str = Field(..., alias="sessionId")
    frame_data: str = Field(..., alias="frameData")
    current_play_time: float = Field(..., alias="currentPlayTime")
    sequence_length: int | None = Field(None, alias="sequenceLength")
    top_k: int | None = Field(None, alias="topK")

    model_config = ConfigDict(populate_by_name=True, extra="ignore")


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


@router.post("/predict")
async def predict_motion_action(
    file: UploadFile = File(...),
    sequence_length: int = 32,
    top_k: int = 2,
) -> dict:
    """업로드된 단일 이미지에서 동작을 추론합니다."""
    if not file.filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="파일 이름이 없습니다.")

    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="이미지 파일만 업로드할 수 있습니다.",
        )

    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="파일이 비어 있습니다.")

    suffix = Path(file.filename).suffix or ".jpg"
    tmp_path: Path | None = None
    try:
        tmp_path = _make_temp_image_file(file_bytes, suffix=suffix)

        logger.debug(
            "Running inference for uploaded file %s (sequence_length=%d, top_k=%d)",
            file.filename,
            sequence_length,
            top_k,
        )

        result = predict_action_from_image(
            image_path=tmp_path,
            sequence_length=sequence_length,
            top_k=top_k,
            device="cuda",
        )
        return result

    except (PoseExtractionError, ModelLoaderError) as exc:
        logger.exception("Inference failed: %s", exc)
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except RuntimeError as exc:
        logger.exception("Runtime error during inference: %s", exc)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc
    finally:
        if tmp_path is not None:
            try:
                tmp_path.unlink()
            except OSError:
                logger.warning("Failed to delete temporary file %s", tmp_path)


@router.post("/predict/base64")
async def predict_motion_action_base64(payload: InferenceRequest) -> dict:
    """Base64 인코딩된 이미지 데이터를 받아 동작을 추론합니다."""
    sequence_length = payload.sequence_length or 32
    top_k = payload.top_k or 2

    image_bytes = _decode_base64_image(payload.frame_data)
    tmp_path: Path | None = None

    try:
        tmp_path = _make_temp_image_file(image_bytes)

        logger.debug(
            "Running inference for session %s (sequence_length=%d, top_k=%d)",
            payload.session_id,
            sequence_length,
            top_k,
        )

        result = predict_action_from_image(
            image_path=tmp_path,
            sequence_length=sequence_length,
            top_k=top_k,
            device="cuda",
        )

        return {
            "sessionId": payload.session_id,
            "currentPlayTime": payload.current_play_time,
            **result,
        }

    except (PoseExtractionError, ModelLoaderError) as exc:
        logger.exception("Inference failed: %s", exc)
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except RuntimeError as exc:
        logger.exception("Runtime error during inference: %s", exc)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc
    finally:
        if tmp_path is not None:
            try:
                tmp_path.unlink()
            except OSError:
                logger.warning("Failed to delete temporary file %s", tmp_path)


