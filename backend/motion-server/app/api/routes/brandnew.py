"""Brandnew 모델 전용 API 라우터 - 새로운 학습 모델 추론."""

import logging

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field, conlist

from app.services.inference import InferenceResult
from app.services.brandnew_inference import BrandnewMotionInferenceService, get_brandnew_inference_service

router = APIRouter(prefix="/api/ai/brandnew", tags=["brandnew-analysis"])
LOGGER = logging.getLogger(__name__)


class BrandnewAnalyzeRequest(BaseModel):
    actionCode: int | None = Field(None, description="동작 코드 (선택)")
    actionName: str | None = Field(None, description="동작 이름 (선택)")
    frameCount: int | None = Field(None, description="프론트에서 전송한 총 프레임 수")
    frames: conlist(str, min_length=1) = Field(..., description="Base64 이미지 프레임 리스트")


class BrandnewAnalyzeResponse(BaseModel):
    actionCode: int | None = Field(
        None, description="판정에 사용된 동작 코드 (없을 경우 예측 결과 코드)"
    )
    judgment: int = Field(..., ge=0, le=3, description="동작 판정 결과 점수 (0~3)")

    # AI 모델 추론 상세 정보 (정확도 분석용)
    predictedLabel: str = Field(..., description="AI가 예측한 동작 이름")
    confidence: float = Field(..., ge=0, le=1, description="예측 신뢰도 (0.0~1.0)")
    targetProbability: float | None = Field(None, ge=0, le=1, description="목표 동작일 확률 (0.0~1.0)")

    # 처리 시간 분석
    decodeTimeMs: float = Field(..., ge=0, description="이미지 디코딩 총 소요 시간(ms)")
    poseTimeMs: float = Field(..., ge=0, description="Mediapipe 포즈 추출 총 소요 시간(ms)")
    inferenceTimeMs: float = Field(..., ge=0, description="동작 추론 소요 시간(ms)")


@router.post("/analyze", response_model=BrandnewAnalyzeResponse)
async def analyze_motion_brandnew(
    payload: BrandnewAnalyzeRequest,
    inference_service: BrandnewMotionInferenceService = Depends(get_brandnew_inference_service),
) -> BrandnewAnalyzeResponse:
    """Brandnew 모델을 사용한 동작 분석 엔드포인트."""
    try:
        result: InferenceResult = inference_service.predict(
            frames=payload.frames,
            target_action_name=payload.actionName,
            target_action_code=payload.actionCode,
        )
    except ValueError as exc:
        LOGGER.warning("Invalid brandnew analyze request: %s", exc, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except Exception as exc:  # pragma: no cover - 예외 상황 로깅
        LOGGER.exception("Brandnew motion inference failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Brandnew 동작 분석 중 오류가 발생했습니다.",
        ) from exc

    response_action_code = (
        payload.actionCode if payload.actionCode is not None else result.action_code
    )

    return BrandnewAnalyzeResponse(
        actionCode=response_action_code,
        judgment=result.judgment,
        predictedLabel=result.predicted_label,
        confidence=result.confidence,
        targetProbability=result.target_probability,
        decodeTimeMs=result.decode_time_ms,
        poseTimeMs=result.pose_time_ms,
        inferenceTimeMs=result.inference_time_ms,
    )
