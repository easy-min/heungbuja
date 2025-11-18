import base64
import logging
from io import BytesIO
from functools import lru_cache
from pathlib import Path
from typing import Any, List, Optional, Tuple

import numpy as np
import mediapipe as mp
from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel, Field
from PIL import Image

from app.services.pose_sequence_classifier import (
    ReferenceSequence,
    evaluate_query,
    load_npz_bytes,
    load_reference_sequences,
    summarize_by_action,
)

router = APIRouter(prefix="/api/pose-sequences", tags=["pose-sequence"])
LOGGER = logging.getLogger(__name__)
BASE_REFERENCE_DIR = Path(__file__).resolve().parents[2] / "services" / "pose_sequences"


class PoseSequenceReference(BaseModel):
    action: str = Field(..., description="참조 동작 이름")
    person: str = Field(..., description="참조 시퀀스 수집자")
    sequenceId: int = Field(..., description="참조 시퀀스 ID")
    distance: float = Field(..., ge=0, description="평균 유클리드 거리")
    cosine: float = Field(..., description="평균 코사인 유사도")
    path: str = Field(..., description="참조 시퀀스 파일 경로")


class PoseSequenceSummary(BaseModel):
    action: str = Field(..., description="동작 이름")
    averageDistance: float = Field(..., ge=0, description="동작별 평균 유클리드 거리")
    averageCosine: float = Field(..., description="동작별 평균 코사인 유사도")


class PoseSequenceClassificationResponse(BaseModel):
    referenceCount: int = Field(..., ge=0, description="비교에 사용된 참조 시퀀스 수")
    topResults: List[PoseSequenceReference] = Field(..., description="Top-K 참조 시퀀스 결과")
    actionSummary: List[PoseSequenceSummary] = Field(..., description="동작별 평균 요약")
    passedThresholds: Optional[List[PoseSequenceReference]] = Field(
        None, description="임계값을 통과한 참조 시퀀스 목록"
    )
    queryMetadata: Optional[dict[str, Any]] = Field(None, description="질의 시퀀스 메타데이터")


class PoseSequenceClassificationRequest(BaseModel):
    npzBase64: Optional[str] = Field(
        None,
        description="Base64로 인코딩된 .npz 파일 내용",
    )
    actionCode: Optional[int] = Field(
        None,
        description="Spring 서버에서 전달하는 목표 동작 코드",
    )
    actionName: Optional[str] = Field(
        None,
        description="Spring 서버에서 전달하는 목표 동작 이름",
    )
    frameCount: Optional[int] = Field(
        None,
        ge=1,
        description="Spring 서버에서 전송한 총 프레임 수",
    )
    frames: Optional[List[str]] = Field(
        None,
        description="Base64 인코딩된 이미지 프레임 목록",
    )
    landmarks: Optional[List[List[List[float]]]] = Field(
        None,
        description="(frames, landmarks, dims) 형태의 좌표 배열",
    )
    metadata: Optional[dict[str, Any]] = Field(
        None,
        description="질의 시퀀스에 대한 메타데이터 (선택)",
    )

    model_config = {"extra": "forbid"}


def _normalize_actions(actions: Optional[List[str]]) -> Optional[Tuple[str, ...]]:
    if not actions:
        return None
    normalized = {action.strip().upper() for action in actions if action and action.strip()}
    return tuple(sorted(normalized)) or None


@lru_cache(maxsize=16)
def _get_references_cached(
    reference_dir: str, actions_key: Optional[Tuple[str, ...]]
) -> Tuple[ReferenceSequence, ...]:
    path = Path(reference_dir)
    references = load_reference_sequences(path, actions=list(actions_key) if actions_key else None)
    return tuple(references)


def _make_reference_payload(
    ref: ReferenceSequence, distance: float, cosine: float, base_dir: Path
) -> PoseSequenceReference:
    try:
        relative_path = ref.path.relative_to(base_dir)
        path_str = str(relative_path)
    except ValueError:
        path_str = str(ref.path)

    return PoseSequenceReference(
        action=ref.action,
        person=ref.person,
        sequenceId=int(ref.sequence_id),
        distance=float(distance),
        cosine=float(cosine),
        path=path_str,
    )


def _decode_base64_image(data: str) -> np.ndarray:
    try:
        image_data = base64.b64decode(data)
    except (ValueError, TypeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="프레임을 Base64로 디코딩할 수 없습니다.",
        ) from exc

    try:
        with Image.open(BytesIO(image_data)) as img:
            rgb_image = img.convert("RGB")
            return np.array(rgb_image)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="프레임 이미지를 읽는 중 오류가 발생했습니다.",
        ) from exc


def _extract_landmarks_from_frames(frames: List[str]) -> np.ndarray:
    if not frames:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="frames 목록이 비어 있습니다.",
        )

    mp_pose = mp.solutions.pose
    extracted: List[np.ndarray] = []

    with mp_pose.Pose(
        static_image_mode=True,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.5,
    ) as pose:
        for frame in frames:
            image = _decode_base64_image(frame)
            results = pose.process(image)
            if not results.pose_landmarks:
                LOGGER.debug("프레임에서 포즈를 감지하지 못했습니다. 프레임을 건너뜁니다.")
                continue

            coords = np.array(
                [[lm.x, lm.y] for lm in results.pose_landmarks.landmark],
                dtype=np.float32,
            )
            extracted.append(coords)

    if not extracted:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="유효한 포즈가 감지된 프레임이 없습니다.",
        )

    return np.stack(extracted, axis=0)


def _load_query_sequence(payload: PoseSequenceClassificationRequest) -> Tuple[np.ndarray, dict]:
    if payload.npzBase64:
        try:
            decoded = base64.b64decode(payload.npzBase64)
        except (ValueError, TypeError) as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="npzBase64 필드를 Base64로 디코딩할 수 없습니다.",
            ) from exc

        try:
            landmarks, metadata = load_npz_bytes(decoded)
        except KeyError as exc:
            LOGGER.warning("Invalid npz payload (missing landmarks): %s", exc)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="npz 데이터에서 'landmarks' 배열을 찾을 수 없습니다.",
            ) from exc
        except Exception as exc:
            LOGGER.exception("Failed to load npz payload: %s", exc)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="npz 데이터를 읽는 중 오류가 발생했습니다.",
            ) from exc

        if payload.metadata:
            metadata = {**metadata, **payload.metadata}
        return landmarks, metadata

    if payload.frames:
        if payload.frameCount is not None and payload.frameCount != len(payload.frames):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="frameCount 값이 frames 배열 길이와 일치하지 않습니다.",
            )

        landmarks_array = _extract_landmarks_from_frames(payload.frames)
        metadata = dict(payload.metadata) if payload.metadata else {}
        metadata.setdefault("source", "frames")
        metadata.setdefault("frame_count", len(landmarks_array))
        if payload.actionCode is not None:
            metadata.setdefault("action_code", payload.actionCode)
        if payload.actionName:
            metadata.setdefault("action_name", payload.actionName)
        return landmarks_array, metadata

    if payload.landmarks:
        try:
            landmarks_array = np.asarray(payload.landmarks, dtype=np.float32)
        except ValueError as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="landmarks 필드를 float32 배열로 변환할 수 없습니다.",
            ) from exc

        if landmarks_array.ndim != 3:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="landmarks 배열은 (frames, landmarks, dims) 3차원 형태여야 합니다.",
            )

        metadata = payload.metadata or {}
        if payload.actionCode is not None:
            metadata.setdefault("action_code", payload.actionCode)
        if payload.actionName:
            metadata.setdefault("action_name", payload.actionName)
        return landmarks_array, metadata

    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail="npzBase64 또는 landmarks 중 하나는 반드시 포함되어야 합니다.",
    )


@router.post(
    "/classify",
    response_model=PoseSequenceClassificationResponse,
    summary="질의 포즈 시퀀스를 참조 시퀀스와 비교",
)
async def classify_pose_sequence(
    payload: PoseSequenceClassificationRequest,
    top_k: int = Query(5, alias="topK", ge=1, description="상위 K개의 결과를 반환"),
    distance_threshold: Optional[float] = Query(
        None, alias="distanceThreshold", description="유클리드 거리 임계값(이하만 통과)"
    ),
    cosine_threshold: Optional[float] = Query(
        None, alias="cosineThreshold", description="코사인 유사도 임계값(이상만 통과)"
    ),
    actions: Optional[List[str]] = Query(
        None, description="특정 동작만 비교하고 싶을 때 지정 (대소문자 무시)"
    ),
    reference_dir: Optional[str] = Query(
        None,
        alias="referenceDir",
        description="기본 디렉터리 대신 사용할 참조 시퀀스 디렉터리",
    ),
) -> PoseSequenceClassificationResponse:
    query_landmarks, query_metadata = _load_query_sequence(payload)

    base_reference_dir = BASE_REFERENCE_DIR
    if reference_dir:
        custom_dir = Path(reference_dir).expanduser()
        if not custom_dir.is_absolute():
            custom_dir = (BASE_REFERENCE_DIR / reference_dir).resolve()
        base_reference_dir = custom_dir

    if not base_reference_dir.exists():
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"참조 디렉터리를 찾을 수 없습니다: {base_reference_dir}",
        )
    if not base_reference_dir.is_dir():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"참조 경로가 디렉터리가 아닙니다: {base_reference_dir}",
        )

    actions_key = _normalize_actions(actions)

    try:
        references_tuple = _get_references_cached(str(base_reference_dir.resolve()), actions_key)
    except Exception as exc:  # pragma: no cover - 캐시 내부 예외 처리
        LOGGER.exception("Failed to load reference sequences: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="참조 시퀀스를 로드하는 중 오류가 발생했습니다.",
        ) from exc

    references: List[ReferenceSequence] = list(references_tuple)
    if not references:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="비교할 참조 시퀀스를 찾지 못했습니다.",
        )

    evaluations = evaluate_query(query_landmarks, references)
    if not evaluations:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="유사도 계산 결과가 비어 있습니다.",
        )

    k = min(top_k, len(evaluations))
    top_results_payload = [
        _make_reference_payload(ref, euc, cos, base_reference_dir)
        for ref, euc, cos in evaluations[:k]
    ]

    action_summary_payload = [
        PoseSequenceSummary(
            action=action,
            averageDistance=float(avg_dist),
            averageCosine=float(avg_cos),
        )
        for action, avg_dist, avg_cos in summarize_by_action(evaluations)
    ]

    passed_thresholds: List[PoseSequenceReference] = []
    if distance_threshold is not None or cosine_threshold is not None:
        for ref, euc, cos in evaluations:
            if distance_threshold is not None and euc > distance_threshold:
                continue
            if cosine_threshold is not None and cos < cosine_threshold:
                continue
            passed_thresholds.append(_make_reference_payload(ref, euc, cos, base_reference_dir))

    response_metadata: Optional[dict[str, Any]] = None
    if query_metadata:
        response_metadata = dict(query_metadata)

    return PoseSequenceClassificationResponse(
        referenceCount=len(references),
        topResults=top_results_payload,
        actionSummary=action_summary_payload,
        passedThresholds=passed_thresholds or None,
        queryMetadata=response_metadata,
    )

