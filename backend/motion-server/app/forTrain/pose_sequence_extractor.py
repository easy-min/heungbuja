"""
MediaPipe 포즈 랜드마크 기반 동작 시퀀스 추출 스크립트

data/이니셜/동작/ 폴더에 저장된 프레임 이미지들을 불러와
각 시퀀스(예: clap_seq001_frame1~8)에 대한 포즈 랜드마크를 추출한 뒤
압축된 NumPy (.npz) 파일로 저장합니다.

사용 예시:
    python pose_sequence_extractor.py --data_dir ./data --output_dir ./pose_sequences
    python pose_sequence_extractor.py --data_dir ./data --persons JSY YHS --actions CLAP
"""

from __future__ import annotations

import argparse
from collections import defaultdict
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import cv2
import mediapipe as mp
import numpy as np


SEQ_PATTERN = re.compile(
    r"(?P<base>.+)_seq(?P<seq>\d+)_frame(?P<frame>\d+)\.(?P<ext>jpg|jpeg|png)$",
    re.IGNORECASE,
)


SUPPORTED_ACTIONS = ["CLAP", "ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]  # 7개 동작


@dataclass
class SequenceResult:
    person: str
    action: str
    sequence_id: int
    frame_count: int
    saved_path: Path


def collect_sequences(
    action_dir: Path,
) -> Dict[int, List[Tuple[int, Path]]]:
    """
    action_dir 내 이미지 파일을 시퀀스ID/프레임ID별로 그룹화합니다.
    """
    sequences: Dict[int, List[Tuple[int, Path]]] = defaultdict(list)
    for image_path in sorted(action_dir.glob("*")):
        if not image_path.is_file():
            continue
        match = SEQ_PATTERN.match(image_path.name)
        if not match:
            continue

        seq_id = int(match.group("seq"))
        frame_id = int(match.group("frame"))
        sequences[seq_id].append((frame_id, image_path))

    # 프레임 번호 순으로 정렬
    for seq_id in list(sequences.keys()):
        sequences[seq_id] = sorted(sequences[seq_id], key=lambda item: item[0])

    return sequences


def extract_landmarks_from_image(
    pose: mp.solutions.pose.Pose,
    image_path: Path,
) -> Optional[np.ndarray]:
    image = cv2.imread(str(image_path))
    if image is None:
        raise FileNotFoundError(f"이미지를 열 수 없습니다: {image_path}")

    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    results = pose.process(image_rgb)

    if not results.pose_landmarks:
        print(f"⚠️  포즈를 감지하지 못했습니다: {image_path}")
        return None

    landmarks = np.array(
        [
            [lm.x, lm.y]
            for lm in results.pose_landmarks.landmark
        ],
        dtype=np.float32,
    )
    return landmarks


def save_sequence(
    output_dir: Path,
    person: str,
    action: str,
    sequence_id: int,
    landmarks: np.ndarray,
    metadata: Dict[str, object],
    overwrite: bool = False,
) -> Path:
    person = person.upper()
    action = action.upper()
    output_dir = output_dir / person / action
    output_dir.mkdir(parents=True, exist_ok=True)

    filename = f"{action.lower()}_seq{sequence_id:03d}.npz"
    output_path = output_dir / filename

    if output_path.exists() and not overwrite:
        raise FileExistsError(f"이미 파일이 존재합니다 (덮어쓰기 비활성화): {output_path}")

    np.savez_compressed(output_path, landmarks=landmarks, metadata=json.dumps(metadata))
    return output_path


def extract_pose_sequences(
    data_dir: Path,
    output_dir: Path,
    frames_per_sample: int,
    persons: Optional[Iterable[str]] = None,
    actions: Optional[Iterable[str]] = None,
    overwrite: bool = False,
    model_complexity: int = 1,
    min_detection_confidence: float = 0.5,
) -> List[SequenceResult]:
    """
    data_dir 구조를 순회하며 포즈 시퀀스를 추출하고 저장합니다.
    """
    data_dir = Path(data_dir)
    output_dir = Path(output_dir)

    if not data_dir.exists():
        raise FileNotFoundError(f"데이터 디렉토리를 찾을 수 없습니다: {data_dir}")

    person_filter = {p.upper() for p in persons} if persons else None
    action_filter = {a.upper() for a in actions} if actions else None

    mp_pose = mp.solutions.pose
    results: List[SequenceResult] = []

    with mp_pose.Pose(
        static_image_mode=True,
        model_complexity=model_complexity,
        enable_segmentation=False,
        min_detection_confidence=min_detection_confidence,
    ) as pose:
        for person_dir in sorted(p for p in data_dir.iterdir() if p.is_dir()):
            person_name = person_dir.name.upper()
            if person_filter and person_name not in person_filter:
                continue

            for action_dir in sorted(p for p in person_dir.iterdir() if p.is_dir()):
                action_name = action_dir.name.upper()
                if action_filter and action_name not in action_filter:
                    continue
                if action_name not in SUPPORTED_ACTIONS:
                    print(f"⚠️  지원되지 않는 동작으로 건너뜀: {action_name}")
                    continue

                sequences = collect_sequences(action_dir)
                if not sequences:
                    print(f"⚠️  시퀀스를 찾을 수 없습니다: {action_dir}")
                    continue

                for sequence_id, frames in sequences.items():
                    if len(frames) != frames_per_sample:
                        print(
                            f"⚠️  프레임 수 불일치로 건너뜀: "
                            f"{person_name}/{action_name} seq{sequence_id:03d} "
                            f"({len(frames)} 프레임, 기대: {frames_per_sample})"
                        )
                        continue

                    frame_landmarks: List[np.ndarray] = []
                    skip_sequence = False

                    for frame_idx, image_path in frames:
                        landmarks = extract_landmarks_from_image(pose, image_path)
                        if landmarks is None:
                            skip_sequence = True
                            break
                        frame_landmarks.append(landmarks)

                    if skip_sequence:
                        print(
                            f"⚠️  포즈 추출 실패로 시퀀스를 건너뜁니다: "
                            f"{person_name}/{action_name} seq{sequence_id:03d}"
                        )
                        continue

                    landmarks_array = np.stack(frame_landmarks, axis=0)
                    metadata = {
                        "person": person_name,
                        "action": action_name,
                        "sequence_id": sequence_id,
                        "frames_per_sample": frames_per_sample,
                        "landmark_count": landmarks_array.shape[1],
                    }
                    try:
                        saved_path = save_sequence(
                            output_dir=output_dir,
                            person=person_name,
                            action=action_name,
                            sequence_id=sequence_id,
                            landmarks=landmarks_array,
                            metadata=metadata,
                            overwrite=overwrite,
                        )
                        results.append(
                            SequenceResult(
                                person=person_name,
                                action=action_name,
                                sequence_id=sequence_id,
                                frame_count=frames_per_sample,
                                saved_path=saved_path,
                            )
                        )
                        print(
                            f"✓ 저장 완료: {person_name}/{action_name} "
                            f"seq{sequence_id:03d} → {saved_path}"
                        )
                    except FileExistsError as error:
                        print(f"⚠️  {error}")

    return results


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="MediaPipe를 이용한 포즈 시퀀스 (.npz) 추출 스크립트",
    )
    parser.add_argument(
        "--data_dir",
        type=str,
        required=True,
        help="프레임 이미지가 저장된 입력 디렉토리 (예: ./data)",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="pose_sequences",
        help="포즈 시퀀스를 저장할 출력 디렉토리 (기본: ./pose_sequences)",
    )
    parser.add_argument(
        "--frames_per_sample",
        type=int,
        default=8,
        help="시퀀스를 구성하는 프레임 수 (기본: 8)",
    )
    parser.add_argument(
        "--persons",
        nargs="*",
        default=None,
        help="특정 참가자(이니셜)만 처리 (대소문자 무시)",
    )
    parser.add_argument(
        "--actions",
        nargs="*",
        default=None,
        help=f"특정 동작만 처리 (예: {' '.join(SUPPORTED_ACTIONS)})",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="이미 존재하는 출력 파일을 덮어쓰기",
    )
    parser.add_argument(
        "--model_complexity",
        type=int,
        default=1,
        choices=[0, 1, 2],
        help="MediaPipe Pose 모델 복잡도 (0, 1, 2)",
    )
    parser.add_argument(
        "--min_detection_confidence",
        type=float,
        default=0.5,
        help="포즈 감지를 위한 최소 신뢰도 (0.0~1.0)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    results = extract_pose_sequences(
        data_dir=Path(args.data_dir),
        output_dir=Path(args.output_dir),
        frames_per_sample=args.frames_per_sample,
        persons=args.persons,
        actions=args.actions,
        overwrite=args.overwrite,
        model_complexity=args.model_complexity,
        min_detection_confidence=args.min_detection_confidence,
    )

    if results:
        summary = defaultdict(lambda: defaultdict(int))
        for result in results:
            summary[result.person][result.action] += 1

        print("\n📊 추출 요약")
        for person, actions in sorted(summary.items()):
            print(f"[{person}]")
            for action, count in sorted(actions.items()):
                print(f"  - {action}: {count}개")
    else:
        print("⚠️  저장된 시퀀스가 없습니다.")


if __name__ == "__main__":
    main()

