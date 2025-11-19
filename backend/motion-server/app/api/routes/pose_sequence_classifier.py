import base64
import logging
from io import BytesIO
from functools import lru_cache
from pathlib import Path
from typing import Any, List, Optional, Sequence, Tuple

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
    normalize_landmarks,
    sanitize_landmarks_array,
)

router = APIRouter(prefix="/api/pose-sequences", tags=["pose-sequence"])
LOGGER = logging.getLogger(__name__)

_APP_ROOT = Path(__file__).resolve().parents[2]
_REFERENCE_DIR_CANDIDATES = [
    _APP_ROOT / "pose_sequences",
    _APP_ROOT / "services" / "pose_sequences",
]
for _candidate in _REFERENCE_DIR_CANDIDATES:
    if _candidate.exists():
        BASE_REFERENCE_DIR = _candidate
        break
else:
    BASE_REFERENCE_DIR = _REFERENCE_DIR_CANDIDATES[0]

ACTION_CODE_TO_LABEL = {
    1: "CLAP",
    2: "ELBOW",
    4: "STRETCH",
    5: "TILT",
    6: "EXIT",
    7: "UNDERARM",
}
LABEL_TO_ACTION_CODE = {label: code for code, label in ACTION_CODE_TO_LABEL.items()}

MIN_VALID_FRAMES = 1
MIN_MOTION_THRESHOLD = 0.03
LEFT_SHOULDER_IDX = 0
RIGHT_SHOULDER_IDX = 1
LEFT_WRIST_IDX = 4
RIGHT_WRIST_IDX = 5


class PoseSequenceClassificationResponse(BaseModel):
    actionCode: Optional[int] = Field(
        None, description="요청한 동작 코드 (매핑 후 숫자)"
    )
    judgment: int = Field(
        ...,
        ge=0,
        le=3,
        description="유사도 기반 판정 점수 (3: 매우 유사, 2: 보통, 1: 낮음, 0: 데이터 없음)",
    )


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


def _is_clap_like(sequence: np.ndarray) -> bool:
    if sequence.ndim != 3 or sequence.shape[0] == 0:
        return False

    try:
        normalized = normalize_landmarks(sequence)
    except ValueError:
        return False

    left_wrist = normalized[:, LEFT_WRIST_IDX]
    right_wrist = normalized[:, RIGHT_WRIST_IDX]
    wrist_distance = np.linalg.norm(left_wrist - right_wrist, axis=1)
    if wrist_distance.size == 0:
        return False

    min_wrist_distance = float(np.min(wrist_distance))
    mean_wrist_distance = float(np.mean(wrist_distance))
    wrist_range = float(np.max(wrist_distance) - min_wrist_distance)
    # hands should move toward each other then apart; ensure at least one strong convergence
    convergence_strength = wrist_range

    if min_wrist_distance > 0.28:
        return False
    if mean_wrist_distance > 0.50:
        return False
    if convergence_strength < 0.18:
        return False
    return True


def _has_clear_margin(
    target_action: str,
    target_result: Optional[Tuple[ReferenceSequence, float, float]],
    evaluations: Sequence[Tuple[ReferenceSequence, float, float]],
    cosine_margin: float = 0.07,
    distance_margin: float = 0.7,
) -> bool:
    if not target_result:
        return False

    _, best_dist, best_cos = target_result
    best_other: Optional[Tuple[ReferenceSequence, float, float]] = None
    for ref, euc, cos in evaluations:
        if ref.action.strip().upper() == target_action:
            continue
        if best_other is None or cos > best_other[2]:
            best_other = (ref, euc, cos)

    if best_other is None:
        return True

    _, other_dist, other_cos = best_other
    if best_cos - other_cos < cosine_margin and other_dist <= best_dist + distance_margin:
        return False
    return True


@lru_cache(maxsize=16)
def _get_references_cached(
    reference_dir: str, actions_key: Optional[Tuple[str, ...]]
) -> Tuple[ReferenceSequence, ...]:
    path = Path(reference_dir)
    references = load_reference_sequences(path, actions=list(actions_key) if actions_key else None)
    return tuple(references)


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

    if len(extracted) < MIN_VALID_FRAMES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                f"유효한 프레임이 부족합니다 ({len(extracted)}/{len(frames)}개). "
                f"최소 {MIN_VALID_FRAMES}개 이상 프레임을 제공해주세요."
            ),
        )

    stacked = np.stack(extracted, axis=0)
    return sanitize_landmarks_array(stacked)


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
            landmarks_array = sanitize_landmarks_array(np.asarray(payload.landmarks, dtype=np.float32))
        except (ValueError, TypeError) as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="landmarks 필드를 float32 배열로 변환할 수 없습니다.",
            ) from exc

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


def _resolve_target_action(
    action_code: Optional[int], action_name: Optional[str]
) -> Tuple[Optional[str], Optional[int]]:
    if action_code in ACTION_CODE_TO_LABEL:
        return ACTION_CODE_TO_LABEL[action_code], action_code

    if action_name:
        normalized = action_name.strip().upper()
        if normalized in LABEL_TO_ACTION_CODE:
            return normalized, LABEL_TO_ACTION_CODE[normalized]
        return normalized, None

    return None, None


def _score_similarity(distance: float, cosine: float) -> int:
    clamped_cosine = max(0.0, min(1.0, cosine))
    if distance <= 4.5 and clamped_cosine >= 0.92:
        return 3
    if distance <= 6.5 and clamped_cosine >= 0.82:
        return 2
    return 1


def _judgment_from_score(score: Optional[int]) -> int:
    if score is None:
        return 0
    return max(1, min(3, score))


def _estimate_motion(sequence: np.ndarray) -> float:
    if sequence.ndim != 3 or sequence.shape[0] < 2:
        return 0.0
    deltas = np.diff(sequence, axis=0)
    per_frame_motion = np.linalg.norm(deltas, axis=(1, 2))
    if per_frame_motion.size == 0:
        return 0.0
    return float(np.mean(per_frame_motion))


@router.post(
    "/classify",
    response_model=PoseSequenceClassificationResponse,
    summary="질의 포즈 시퀀스를 참조 시퀀스와 비교",
)
async def classify_pose_sequence(
    payload: PoseSequenceClassificationRequest,
    _top_k: int = Query(5, alias="topK", ge=1, description="(호환용) 미사용 매개변수"),
    _distance_threshold: Optional[float] = Query(
        None, alias="distanceThreshold", description="(호환용) 미사용 매개변수"
    ),
    _cosine_threshold: Optional[float] = Query(
        None, alias="cosineThreshold", description="(호환용) 미사용 매개변수"
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
    resolved_action_code = payload.actionCode
    if resolved_action_code is None and payload.actionName:
        action_name_key = payload.actionName.strip().upper()
        resolved_action_code = LABEL_TO_ACTION_CODE.get(action_name_key)

    query_landmarks, query_metadata = _load_query_sequence(payload)

    frame_count = query_landmarks.shape[0]
    if frame_count < MIN_VALID_FRAMES:
        LOGGER.warning(
            "Insufficient frame count for reliable comparison: %d (required >= %d)",
            frame_count,
            MIN_VALID_FRAMES,
        )
        return PoseSequenceClassificationResponse(actionCode=resolved_action_code, judgment=0)

    motion_metric = _estimate_motion(query_landmarks)
    if motion_metric < MIN_MOTION_THRESHOLD:
        LOGGER.info(
            "Detected insufficient motion (avg delta=%.4f < %.4f). Returning judgment 0.",
            motion_metric,
            MIN_MOTION_THRESHOLD,
        )
        return PoseSequenceClassificationResponse(actionCode=resolved_action_code, judgment=0)

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

    try:
        evaluations = evaluate_query(query_landmarks, references)
    except ValueError as exc:
        LOGGER.warning("유사도 계산 중 오류 발생: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    if not evaluations:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="유사도 계산 결과가 비어 있습니다.",
        )

    if query_metadata:
        query_metadata = dict(query_metadata)

    target_action, target_action_code = _resolve_target_action(
        payload.actionCode, payload.actionName
    )

    response_action_code: Optional[int] = (
        payload.actionCode
        if payload.actionCode is not None
        else target_action_code
        if target_action_code is not None
        else resolved_action_code
    )

    judgment = 0
    if target_action:
        best_match_tuple = next(
            ((ref, euc, cos) for ref, euc, cos in evaluations if ref.action.strip().upper() == target_action),
            None,
        )
        if best_match_tuple:
            _, best_dist, best_cos = best_match_tuple
            additional_checks_passed = True

            if target_action == "CLAP":
                if not _is_clap_like(query_landmarks):
                    LOGGER.info("CLAP heuristics failed; wrists did not converge sufficiently.")
                    additional_checks_passed = False
                elif not _has_clear_margin(target_action, best_match_tuple, evaluations):
                    LOGGER.info("CLAP detection ambiguous compared to other actions.")
                    additional_checks_passed = False

            if additional_checks_passed:
                score = _score_similarity(best_dist, best_cos)
                judgment = _judgment_from_score(score)
            else:
                judgment = 0
        else:
            LOGGER.warning(
                "요청한 동작(%s)에 해당하는 참조 시퀀스를 찾지 못했습니다.",
                target_action,
            )
    else:
        LOGGER.debug("요청에 actionCode/actionName이 없어 judgment를 0으로 반환합니다.")

    return PoseSequenceClassificationResponse(
        actionCode=response_action_code,
        judgment=judgment,
    )

