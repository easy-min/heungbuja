import logging

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field, conlist

from app.services.inference import InferenceResult, MotionInferenceService, get_inference_service

router = APIRouter(prefix="/api/ai", tags=["analysis"])
LOGGER = logging.getLogger(__name__)


class AnalyzeRequest(BaseModel):
    actionCode: int | None = Field(None, description="동작 코드 (선택)")
    actionName: str | None = Field(None, description="동작 이름 (선택)")
    frameCount: int | None = Field(None, description="프론트에서 전송한 총 프레임 수")
    frames: conlist(str, min_length=1) = Field(..., description="Base64 이미지 프레임 리스트")


class AnalyzeResponse(BaseModel):
    judgment: int = Field(..., ge=0, le=3, description="동작 판정 결과 점수 (1~3 권장)")
    actionCode: int | None = Field(
        None, description="판정에 사용된 동작 코드 (없을 경우 예측 결과 코드)"
    )


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze_motion(
    payload: AnalyzeRequest,
    inference_service: MotionInferenceService = Depends(get_inference_service),
) -> AnalyzeResponse:
    try:
        result: InferenceResult = inference_service.predict(
            frames=payload.frames,
            target_action_name=payload.actionName,
            target_action_code=payload.actionCode,
        )
    except ValueError as exc:
        LOGGER.warning("Invalid analyze request: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except Exception as exc:  # pragma: no cover - 예외 상황 로깅
        LOGGER.exception("Motion inference failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="동작 분석 중 오류가 발생했습니다.",
        ) from exc

    response_action_code = (
        payload.actionCode if payload.actionCode is not None else result.action_code
    )

    return AnalyzeResponse(judgment=result.judgment, actionCode=response_action_code)


