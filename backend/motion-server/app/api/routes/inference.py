"""동작 추론(inference) API 라우터."""

from __future__ import annotations

import base64
import logging
from pathlib import Path
from tempfile import NamedTemporaryFile
import asyncio
import time
from collections import defaultdict
from typing import Dict, List
from datetime import datetime

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from pymongo import MongoClient

from app.ml import PoseExtractionError, predict_action_from_frames
from app.ml.model_loader import ModelLoaderError
from app.core.config import get_settings

logger = logging.getLogger(__name__)

# MongoDB 연결
settings = get_settings()
try:
    mongo_client = MongoClient(settings.mongodb_uri, serverSelectionTimeoutMS=5000)
    # 연결 테스트
    mongo_client.admin.command('ping')
    db = mongo_client['heungbudb']
    perf_collection = db['motion_server_performance']
    logger.info("✅ MongoDB 연결 성공")
except Exception as e:
    logger.warning(f"⚠️ MongoDB 연결 실패: {e}. 성능 로그는 메모리에만 저장됩니다.")
    mongo_client = None
    db = None
    perf_collection = None

# 성능 측정을 위한 통계 수집기
class PerformanceStats:
    def __init__(self):
        self.request_count = 0
        self.total_times: Dict[str, List[float]] = defaultdict(list)
        self.last_report_time = time.time()
        self.report_interval = 60  # 60초마다 통계 출력

    def record(self, stage: str, duration: float):
        self.total_times[stage].append(duration)

    def increment_request(self):
        self.request_count += 1

    def maybe_report(self):
        """일정 주기마다 통계 출력"""
        now = time.time()
        if now - self.last_report_time >= self.report_interval:
            self.report()
            self.reset()

    def report(self):
        """통계 출력 및 MongoDB 저장"""
        if self.request_count == 0:
            return

        logger.info("=" * 80)
        logger.info(f"📊 Motion Server Performance Report (Last {self.report_interval}s)")
        logger.info(f"Total Requests: {self.request_count}")
        logger.info("-" * 80)

        stats_data = {
            "timestamp": datetime.utcnow(),
            "interval_seconds": self.report_interval,
            "total_requests": self.request_count,
            "stages": {}
        }

        for stage, times in sorted(self.total_times.items()):
            if times:
                avg = sum(times) / len(times)
                min_t = min(times)
                max_t = max(times)
                total = sum(times)

                logger.info(f"  {stage}:")
                logger.info(f"    - Average: {avg*1000:.2f}ms")
                logger.info(f"    - Min: {min_t*1000:.2f}ms")
                logger.info(f"    - Max: {max_t*1000:.2f}ms")
                logger.info(f"    - Total: {total*1000:.2f}ms ({len(times)} calls)")

                stats_data["stages"][stage] = {
                    "average_ms": round(avg * 1000, 2),
                    "min_ms": round(min_t * 1000, 2),
                    "max_ms": round(max_t * 1000, 2),
                    "total_ms": round(total * 1000, 2),
                    "count": len(times)
                }

        logger.info("=" * 80)

        # MongoDB에 저장
        if perf_collection is not None:
            try:
                perf_collection.insert_one(stats_data)
                logger.info("✅ 성능 통계를 MongoDB에 저장했습니다.")
            except Exception as e:
                logger.error(f"❌ MongoDB 저장 실패: {e}")

    def reset(self):
        """통계 초기화"""
        self.request_count = 0
        self.total_times.clear()
        self.last_report_time = time.time()

# 전역 통계 객체
perf_stats = PerformanceStats()

router = APIRouter(prefix="/inference", tags=["inference"])
analyze_router = APIRouter(prefix="/api/ai", tags=["inference"])

ACTION_CODE_TO_LABEL = {
    1: "CLAP",
    2: "ELBOW",
    3: "STRETCH",
    4: "TILT",
    5: "UNDERARM"
}
ACTION_CODE_TO_DESCRIPTION = {
    1: "손 박수",
    2: "팔 치기",
    3: "팔 뻗기",
    4: "기우뚱",
    5: "겨드랑이"
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
    request_start = time.time()
    perf_stats.increment_request()

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

        # 1. Base64 디코딩 및 파일 생성
        decode_start = time.time()
        for encoded_frame in payload.frames:
            image_bytes = _decode_base64_image(encoded_frame)
            frame_paths.append(_make_temp_image_file(image_bytes))
        decode_duration = time.time() - decode_start
        perf_stats.record("1_decode_and_file_creation", decode_duration)

        logger.debug(
            "Running inference for action_code=%s, frame_count=%d (sequence_length=%d, top_k=%d)",
            payload.action_code,
            payload.frame_count,
            sequence_length,
            top_k,
        )

        # 2. AI 추론 실행
        inference_start = time.time()
        result = await asyncio.to_thread(
            predict_action_from_frames,
            frame_paths=frame_paths,
            sequence_length=sequence_length,
            top_k=top_k,
            device="cpu",
        )
        inference_duration = time.time() - inference_start
        perf_stats.record("2_ai_inference_total", inference_duration)

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

        # 3. 판정 결과 결정
        judgment_start = time.time()
        if expected_probability >= 0.9:
            judgment = 3
        elif expected_probability < 0.5:
            judgment = 1
        else:
            judgment = 2
        judgment_duration = time.time() - judgment_start
        perf_stats.record("3_judgment_decision", judgment_duration)

        # 전체 요청 시간 기록
        total_duration = time.time() - request_start
        perf_stats.record("0_total_request", total_duration)

        # 개별 요청 로그 (간단하게)
        logger.info(
            f"⏱️ Request completed in {total_duration*1000:.2f}ms "
            f"(decode: {decode_duration*1000:.2f}ms, inference: {inference_duration*1000:.2f}ms)"
        )

        # 주기적 통계 출력 체크
        perf_stats.maybe_report()

        return {"judgment": judgment}
    except (PoseExtractionError, ModelLoaderError) as exc:
        logger.error("Inference failed (422): %s", exc, exc_info=True)
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except RuntimeError as exc:
        logger.exception("Runtime error during inference: %s", exc)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc
    finally:
        # 4. 임시 파일 정리
        cleanup_start = time.time()
        for path in frame_paths:
            try:
                path.unlink()
            except OSError:
                logger.warning("Failed to delete temporary file %s", path)
        cleanup_duration = time.time() - cleanup_start
        perf_stats.record("4_file_cleanup", cleanup_duration)


@analyze_router.post("/analyze")
async def analyze_motion_action(payload: InferenceRequest) -> dict:
    return await predict_motion_action_base64(payload)


