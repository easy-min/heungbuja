"""
거리 기반 포즈 시퀀스 분류 스크립트

저장된 참조 시퀀스(.npz)와 비교하여 질의 시퀀스가 어떤 동작인지
유클리드 거리/코사인 유사도를 통해 판별합니다.
"""

from __future__ import annotations

import argparse
import logging
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Any, Iterable, List, Optional, Sequence, Tuple

import numpy as np


LOGGER = logging.getLogger(__name__)


CANONICAL_LANDMARK_INDICES_22 = tuple(range(11, 33))  # Mediapipe 33포인트에서 얼굴 제외
CANONICAL_LANDMARK_COUNT = len(CANONICAL_LANDMARK_INDICES_22)


@dataclass
class ReferenceSequence:
    path: Path
    person: str
    action: str
    sequence_id: int
    landmarks: np.ndarray

def sanitize_landmarks_array(landmarks: np.ndarray) -> np.ndarray:
    """
    랜드마크 배열을 정규화 가능한 형태로 정리합니다.
    - visibility 등 좌표 외 채널 제거
    - Mediapipe 33포인트 입력 시 몸통 22포인트만 추출
    """
    if landmarks.ndim != 3:
        raise ValueError(f"랜드마크 배열은 3차원이어야 합니다. 현재 shape={landmarks.shape}")

    sanitized = np.asarray(landmarks, dtype=np.float32)

    def _is_visibility_channel(channel: np.ndarray) -> bool:
        # visibility/presence 값은 [0, 1] 범위에 존재한다는 가정 하에 판별
        finite_vals = channel[np.isfinite(channel)]
        if finite_vals.size == 0:
            return False
        return float(np.nanmin(finite_vals)) >= -1e-3 and float(np.nanmax(finite_vals)) <= 1.0 + 1e-3

    dropped = 0
    while sanitized.shape[-1] > 2 and _is_visibility_channel(sanitized[..., -1]):
        sanitized = sanitized[..., :-1]
        dropped += 1

    if dropped:
        LOGGER.debug("랜드마크 배열에서 visibility 채널 %d개를 제거했습니다. 결과 shape=%s", dropped, sanitized.shape)

    if sanitized.shape[-1] < 2:
        raise ValueError("랜드마크 배열에는 최소 2개의 좌표 차원이 필요합니다.")

    num_landmarks = sanitized.shape[1]

    if num_landmarks == 33:
        sanitized = sanitized[:, CANONICAL_LANDMARK_INDICES_22, :]
        LOGGER.debug(
            "33개 Mediapipe 랜드마크를 몸통 22포인트로 변환했습니다. 결과 shape=%s",
            sanitized.shape,
        )
        num_landmarks = sanitized.shape[1]

    return sanitized


def _load_npz(data_source: Any) -> Tuple[np.ndarray, dict]:
    """np.load에 전달 가능한 데이터 소스에서 랜드마크와 메타데이터를 로드합니다."""
    with np.load(data_source) as data:
        landmarks = sanitize_landmarks_array(np.array(data["landmarks"], dtype=np.float32))
        metadata = {}
        if "metadata" in data:
            try:
                import json

                metadata = json.loads(str(data["metadata"]))
            except Exception:
                metadata = {"raw_metadata": str(data["metadata"])}
    return landmarks, metadata


def load_npz_sequence(path: Path) -> Tuple[np.ndarray, dict]:
    """단일 .npz 파일에서 랜드마크와 메타데이터를 로드합니다."""
    return _load_npz(path)


def load_npz_bytes(content: bytes) -> Tuple[np.ndarray, dict]:
    """바이트 배열로 전달된 .npz 데이터를 로드합니다."""
    return _load_npz(BytesIO(content))


def load_reference_sequences(
    reference_dir: Path,
    actions: Optional[Iterable[str]] = None,
) -> List[ReferenceSequence]:
    """참조 디렉터리의 .npz 파일을 모두 읽어 ReferenceSequence 목록을 생성합니다."""
    actions_filter = {action.upper() for action in actions} if actions else None
    references: List[ReferenceSequence] = []

    for npz_path in sorted(reference_dir.rglob("*.npz")):
        person = npz_path.parent.parent.name if npz_path.parent.parent != reference_dir else ""
        action = npz_path.parent.name.upper()
        if actions_filter and action not in actions_filter:
            continue

        landmarks, metadata = load_npz_sequence(npz_path)
        seq_id = metadata.get("sequence_id") if isinstance(metadata, dict) else None
        if seq_id is None:
            # 파일명 패턴에서 추출
            seq_id = extract_sequence_id(npz_path.name)

        references.append(
            ReferenceSequence(
                path=npz_path,
                person=metadata.get("person", person),
                action=metadata.get("action", action),
                sequence_id=int(seq_id) if seq_id is not None else -1,
                landmarks=landmarks,
            )
        )
    return references


def extract_sequence_id(filename: str) -> Optional[int]:
    """파일명에서 seq 번호를 추출합니다."""
    import re

    match = re.search(r"seq(\d+)", filename, re.IGNORECASE)
    if match:
        return int(match.group(1))
    return None


def resample_sequence(sequence: np.ndarray, target_frames: int) -> np.ndarray:
    """시퀀스를 선형 보간으로 target_frames 길이에 맞춥니다."""
    frames = sequence.shape[0]
    if frames == target_frames:
        return sequence
    indices = np.linspace(0, frames - 1, target_frames)
    lower = np.floor(indices).astype(int)
    upper = np.ceil(indices).astype(int)
    alpha = indices - lower
    resampled = (1 - alpha)[:, None, None] * sequence[lower] + alpha[:, None, None] * sequence[upper]
    return resampled.astype(np.float32)


def normalize_landmarks(sequence: np.ndarray) -> np.ndarray:
    """
    랜드마크 시퀀스를 위치/스케일 정규화합니다.
    - 양쪽 골반(23, 24) 중점을 원점으로 이동
    - 양쪽 어깨(11, 12) 사이 거리를 1로 스케일링
    """
    num_landmarks = sequence.shape[1]

    if num_landmarks >= 25:
        left_shoulder_idx, right_shoulder_idx = 11, 12
        left_hip_idx, right_hip_idx = 23, 24
    elif num_landmarks == 22:
        # 얼굴을 제외한 22개 포인트(원본 Mediapipe 인덱스 11~32)만 남은 경우
        left_shoulder_idx, right_shoulder_idx = 0, 1
        left_hip_idx, right_hip_idx = 12, 13
    else:
        raise ValueError(
            f"랜드마크 수({num_landmarks})가 지원되지 않습니다. "
            "정규화에 필요한 어깨/골반 관절을 찾을 수 없습니다."
        )

    hips_center = (sequence[:, left_hip_idx] + sequence[:, right_hip_idx]) / 2.0
    normalized = sequence - hips_center[:, None, :]

    shoulder_dist = np.linalg.norm(
        normalized[:, left_shoulder_idx] - normalized[:, right_shoulder_idx],
        axis=1,
        keepdims=True,
    )
    shoulder_dist = np.maximum(shoulder_dist, 1e-6)
    normalized = normalized / shoulder_dist[:, None, :]
    return normalized


def flatten_sequence(sequence: np.ndarray) -> np.ndarray:
    """(frames, landmarks, dims)를 (frames, landmarks*dims) 형태로 펼칩니다."""
    frames = sequence.shape[0]
    return sequence.reshape(frames, -1)


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """프레임별 코사인 유사도의 평균을 계산합니다."""
    dot = np.sum(a * b, axis=1)
    denom = np.linalg.norm(a, axis=1) * np.linalg.norm(b, axis=1) + 1e-9
    cos = dot / denom
    return float(np.mean(cos))


def euclidean_distance(a: np.ndarray, b: np.ndarray) -> float:
    """프레임별 유클리드 거리의 평균을 계산합니다."""
    diff = a - b
    distances = np.linalg.norm(diff, axis=1)
    return float(np.mean(distances))


def compare_sequences(query: np.ndarray, reference: np.ndarray) -> Tuple[float, float]:
    """질의 시퀀스와 참조 시퀀스의 유사도를 계산합니다."""
    ref_frames = reference.shape[0]
    query_resampled = resample_sequence(query, ref_frames)

    if query_resampled.shape[1:] != reference.shape[1:]:
        raise ValueError(
            "질의 시퀀스와 참조 시퀀스의 랜드마크 형상이 일치하지 않습니다: "
            f"query={query_resampled.shape[1:]}, reference={reference.shape[1:]}."
        )

    query_norm = normalize_landmarks(query_resampled)
    reference_norm = normalize_landmarks(reference)

    query_flat = flatten_sequence(query_norm)
    reference_flat = flatten_sequence(reference_norm)

    euc = euclidean_distance(query_flat, reference_flat)
    cos = cosine_similarity(query_flat, reference_flat)
    return euc, cos


def evaluate_query(
    query_landmarks: np.ndarray,
    references: Sequence[ReferenceSequence],
) -> List[Tuple[ReferenceSequence, float, float]]:
    """질의 시퀀스와 모든 참조 시퀀스 간 유사도를 계산합니다."""
    results: List[Tuple[ReferenceSequence, float, float]] = []
    for ref in references:
        euc, cos = compare_sequences(query_landmarks, ref.landmarks)
        results.append((ref, euc, cos))
    results.sort(key=lambda item: item[1])  # 유클리드 거리가 작은 순
    return results


def summarize_by_action(
    evaluations: Sequence[Tuple[ReferenceSequence, float, float]],
) -> List[Tuple[str, float, float]]:
    """동작(action)별 평균 거리/유사도를 계산합니다."""
    from collections import defaultdict

    action_metrics = defaultdict(list)
    for ref, euc, cos in evaluations:
        action_metrics[ref.action].append((euc, cos))

    summary: List[Tuple[str, float, float]] = []
    for action, metrics in action_metrics.items():
        euc_vals, cos_vals = zip(*metrics)
        summary.append((action, float(np.mean(euc_vals)), float(np.mean(cos_vals))))
    summary.sort(key=lambda item: item[1])
    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="저장된 포즈 시퀀스를 거리 기반으로 분류합니다.",
    )
    parser.add_argument(
        "--reference_dir",
        type=Path,
        required=True,
        help="참조 시퀀스(.npz)가 저장된 최상위 디렉터리",
    )
    parser.add_argument(
        "--query",
        type=Path,
        required=True,
        help="분류할 질의 시퀀스(.npz) 파일 경로",
    )
    parser.add_argument(
        "--actions",
        nargs="*",
        help="특정 동작만 비교하고 싶을 경우 지정 (대소문자 무시)",
    )
    parser.add_argument(
        "--top_k",
        type=int,
        default=5,
        help="상위 몇 개 결과를 출력할지 설정 (기본 5)",
    )
    parser.add_argument(
        "--distance_threshold",
        type=float,
        default=None,
        help="이 값보다 유클리드 거리가 작으면 유사한 동작으로 간주",
    )
    parser.add_argument(
        "--cosine_threshold",
        type=float,
        default=None,
        help="이 값보다 코사인 유사도가 크면 유사한 동작으로 간주",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if not args.reference_dir.exists():
        raise FileNotFoundError(f"참조 디렉터리를 찾을 수 없습니다: {args.reference_dir}")
    if not args.query.exists():
        raise FileNotFoundError(f"질의 파일을 찾을 수 없습니다: {args.query}")

    references = load_reference_sequences(args.reference_dir, actions=args.actions)
    if not references:
        raise RuntimeError("비교할 참조 시퀀스를 찾지 못했습니다.")

    query_landmarks, query_meta = load_npz_sequence(args.query)

    evaluations = evaluate_query(query_landmarks, references)

    top_k = min(args.top_k, len(evaluations))
    print(f"\n=== Top {top_k} 결과 (유클리드 거리 기준) ===")
    for ref, euc, cos in evaluations[:top_k]:
        print(
            f"{ref.action:<10} {ref.person:<5} seq{ref.sequence_id:03d} | "
            f"dist: {euc:.4f}  cos: {cos:.4f} | {ref.path}"
        )

    if args.distance_threshold is not None or args.cosine_threshold is not None:
        print("\n=== 임계값 통과 결과 ===")
        passed = []
        for ref, euc, cos in evaluations:
            if args.distance_threshold is not None and euc > args.distance_threshold:
                continue
            if args.cosine_threshold is not None and cos < args.cosine_threshold:
                continue
            passed.append((ref, euc, cos))

        if passed:
            for ref, euc, cos in passed:
                print(
                    f"{ref.action:<10} {ref.person:<5} seq{ref.sequence_id:03d} | "
                    f"dist: {euc:.4f}  cos: {cos:.4f} | {ref.path}"
                )
        else:
            print("임계값을 만족하는 결과가 없습니다.")

    action_summary = summarize_by_action(evaluations)
    print("\n=== 동작별 평균 유사도 ===")
    for action, avg_dist, avg_cos in action_summary:
        print(f"{action:<10} dist: {avg_dist:.4f}  cos: {avg_cos:.4f}")

    if query_meta:
        print("\n질의 메타데이터:", query_meta)


if __name__ == "__main__":
    main()


