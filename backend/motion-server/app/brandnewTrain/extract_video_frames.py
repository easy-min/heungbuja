"""
동영상에서 동작 데이터 자동 추출 스크립트 (동작별 맞춤 주기)

100bpm 기준으로 동작별 리듬에 맞춰 프레임을 추출합니다:
- CLAP: 1박자당 1동작 (0.6초)
- 나머지: 2박자당 1동작 (1.2초)

사용법:
    # 폴더 일괄 처리 (권장)
    python extract_video_frames.py --video_dir ./origin_data --output_dir ./extracted_data

    # 특정 동영상만 처리
    python extract_video_frames.py --video_dir ./origin_data --actions CLAP STRETCH

출력:
    extracted_data/
    ├── PERSON1/
    │   ├── CLAP/
    │   │   ├── clap_seq001_frame1.jpg
    │   │   ├── clap_seq001_frame2.jpg
    │   │   ├── ...
    │   │   └── clap_seq001_frame8.jpg
    │   └── STRETCH/
    │       └── ...
    └── PERSON2/
        └── ...
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Dict, List, Optional

import cv2
import numpy as np


# 동작별 설정
ACTION_CONFIG = {
    "CLAP": {
        "beats_per_action": 1,  # 1박자당 1동작
        "seconds_per_action": 0.6,  # 100bpm에서 1박자 = 0.6초
    },
    "ELBOW": {
        "beats_per_action": 2,
        "seconds_per_action": 1.2,
    },
    "STRETCH": {
        "beats_per_action": 2,
        "seconds_per_action": 1.2,
    },
    "TILT": {
        "beats_per_action": 2,
        "seconds_per_action": 1.2,
    },
    "EXIT": {
        "beats_per_action": 2,
        "seconds_per_action": 1.2,
    },
    "UNDERARM": {
        "beats_per_action": 2,
        "seconds_per_action": 1.2,
    },
    "STAY": {
        "beats_per_action": 2,
        "seconds_per_action": 1.2,
    },
}

SUPPORTED_ACTIONS = list(ACTION_CONFIG.keys())
VIDEO_EXTENSIONS = [".mp4", ".avi", ".mov", ".mkv", ".MP4", ".AVI", ".MOV", ".MKV"]

# 파일명에서 인물 이름 추출 패턴
PERSON_PATTERN = re.compile(
    r"(?P<person>[A-Z]{3}|[가-힣]+)_(?P<action>[A-Z]+)",
    re.IGNORECASE,
)


def extract_person_and_action(filename: str) -> tuple[Optional[str], Optional[str]]:
    """
    파일명에서 인물과 동작 추출

    예시:
        KSM_CLAP.mp4 → ("KSM", "CLAP")
        수연_박수.mp4 → ("수연", None)  # 한글 동작명은 매핑 필요
        CLAP.mp4 → (None, "CLAP")
    """
    match = PERSON_PATTERN.search(filename)
    if match:
        person = match.group("person").upper()
        action_raw = match.group("action").upper()

        # 한글 동작명 매핑
        korean_action_map = {
            "박수": "CLAP",
            "팔꿈치": "ELBOW",
            "스트레칭": "STRETCH",
            "스트레치": "STRETCH",
            "기울이기": "TILT",
            "비상구": "EXIT",
            "겨드랑이": "UNDERARM",
            "대기": "STAY",
        }

        # 영어 동작명이면 그대로, 한글이면 매핑
        if action_raw in SUPPORTED_ACTIONS:
            action = action_raw
        else:
            # 파일명 전체에서 한글 동작명 찾기
            for korean, english in korean_action_map.items():
                if korean in filename:
                    action = english
                    break
            else:
                action = None

        return person, action

    # 패턴이 매칭 안 되면 파일명에서 동작명만이라도 찾기
    filename_upper = filename.upper()
    for action in SUPPORTED_ACTIONS:
        if action in filename_upper:
            return None, action

    return None, None


def get_video_rotation(video_path: Path) -> int:
    """
    동영상의 회전 메타데이터 확인

    Returns:
        0, 90, 180, 270 (시계방향 회전 각도)
    """
    try:
        import subprocess
        import json

        # ffprobe로 메타데이터 읽기
        result = subprocess.run(
            [
                'ffprobe',
                '-v', 'quiet',
                '-print_format', 'json',
                '-show_streams',
                str(video_path)
            ],
            capture_output=True,
            text=True,
            timeout=10
        )

        if result.returncode == 0:
            data = json.loads(result.stdout)
            for stream in data.get('streams', []):
                if stream.get('codec_type') == 'video':
                    # rotation 태그 확인
                    rotation = stream.get('tags', {}).get('rotate', '0')
                    return int(rotation)
    except Exception:
        # ffprobe가 없거나 실패하면 0 반환
        pass

    return 0


def rotate_frame(frame: np.ndarray, rotation: int) -> np.ndarray:
    """
    프레임을 회전 각도에 맞춰 보정

    Args:
        frame: 원본 프레임
        rotation: 시계방향 회전 각도 (0, 90, 180, 270)

    Returns:
        보정된 프레임
    """
    if rotation == 90:
        # 시계방향 90도 회전 → 반시계방향 90도로 보정
        return cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
    elif rotation == 180:
        return cv2.rotate(frame, cv2.ROTATE_180)
    elif rotation == 270:
        # 시계방향 270도 = 반시계방향 90도 → 시계방향 90도로 보정
        return cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
    else:
        return frame


def extract_frames_from_video(
    video_path: Path,
    action_name: str,
    person_name: Optional[str],
    output_dir: Path,
    frames_per_sample: int = 8,
    start_offset: float = 0.0,
    end_offset: float = 0.0,
) -> int:
    """
    동영상에서 동작별 맞춤 주기로 프레임 추출

    Returns:
        추출된 시퀀스 개수
    """
    if action_name not in ACTION_CONFIG:
        raise ValueError(f"지원되지 않는 동작: {action_name}")

    config = ACTION_CONFIG[action_name]
    seconds_per_action = config["seconds_per_action"]

    # 동영상 회전 메타데이터 확인
    rotation = get_video_rotation(video_path)

    # 동영상 열기
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"동영상을 열 수 없습니다: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = total_frames / fps

    print(f"\n{'='*70}")
    print(f"📹 {video_path.name}")
    print(f"{'='*70}")
    print(f"인물: {person_name or 'Unknown'}")
    print(f"동작: {action_name}")
    print(f"동영상 정보: {fps:.2f}fps, {duration:.2f}초, {total_frames} 프레임")
    print(f"동작 주기: {seconds_per_action}초 ({config['beats_per_action']}박자)")
    if rotation != 0:
        print(f"⚠️  회전 보정: {rotation}도 (자동 보정 적용)")

    # 오프셋 적용
    start_frame = int(fps * start_offset)
    end_frame = total_frames - int(fps * end_offset)
    usable_frames = end_frame - start_frame
    usable_duration = usable_frames / fps

    if start_offset > 0 or end_offset > 0:
        print(f"오프셋: 시작 {start_offset}초, 끝 {end_offset}초 건너뛰기")
        print(f"사용 구간: {usable_duration:.2f}초")

    # 1사이클 = 1동작
    frames_per_cycle = int(fps * seconds_per_action)
    max_cycles = int(usable_duration / seconds_per_action)

    print(f"예상 시퀀스: {max_cycles}개")
    print(f"{'='*70}")

    # 출력 디렉토리 생성
    output_dir.mkdir(parents=True, exist_ok=True)

    # 프레임 추출
    saved_sequences = 0

    for cycle_idx in range(max_cycles):
        cycle_start_frame = start_frame + cycle_idx * frames_per_cycle
        cycle_end_frame = min(cycle_start_frame + frames_per_cycle, end_frame)

        sample_frames = []

        # 이 사이클에서 균등하게 frames_per_sample개 추출
        for i in range(frames_per_sample):
            # 선형 보간으로 균등 샘플링
            if frames_per_sample > 1:
                progress = i / (frames_per_sample - 1)
            else:
                progress = 0.0

            target_frame = int(cycle_start_frame + progress * (cycle_end_frame - cycle_start_frame - 1))
            target_frame = min(target_frame, end_frame - 1)

            cap.set(cv2.CAP_PROP_POS_FRAMES, target_frame)
            ret, frame = cap.read()

            if not ret:
                print(f"⚠️  프레임 {target_frame} 읽기 실패")
                break

            # 회전 보정 적용
            if rotation != 0:
                frame = rotate_frame(frame, rotation)

            sample_frames.append(frame)

        # frames_per_sample개 모두 추출했으면 저장
        if len(sample_frames) == frames_per_sample:
            seq_number = cycle_idx + 1

            for frame_num, frame in enumerate(sample_frames, 1):
                filename = output_dir / f"{action_name.lower()}_seq{seq_number:03d}_frame{frame_num}.jpg"
                cv2.imwrite(str(filename), frame)

            saved_sequences += 1

            if saved_sequences % 10 == 0:
                print(f"  ✓ {saved_sequences}개 시퀀스 추출 중...")

    cap.release()

    print(f"✅ 완료: {saved_sequences}개 시퀀스 추출")
    return saved_sequences


def process_video_directory(
    video_dir: Path,
    output_base_dir: Path,
    frames_per_sample: int = 8,
    start_offset: float = 0.0,
    end_offset: float = 0.0,
    action_filter: Optional[List[str]] = None,
) -> Dict[str, int]:
    """
    폴더 구조를 순회하며 모든 동영상 처리

    입력 구조:
        origin_data/
        ├── CLAP/
        │   ├── KSM_CLAP.mp4
        │   └── 수연_박수.mp4
        └── STRETCH/
            └── ...

    출력 구조:
        extracted_data/
        ├── KSM/
        │   ├── CLAP/
        │   │   └── clap_seq001_frame1~8.jpg
        │   └── STRETCH/
        │       └── ...
        └── 수연/
            └── ...

    Returns:
        동작별 추출된 시퀀스 개수
    """
    if not video_dir.exists():
        raise FileNotFoundError(f"동영상 폴더를 찾을 수 없습니다: {video_dir}")

    action_filter_set = set(action_filter) if action_filter else None

    print(f"\n{'='*70}")
    print(f"📁 폴더 일괄 처리 시작")
    print(f"{'='*70}")
    print(f"입력 폴더: {video_dir}")
    print(f"출력 폴더: {output_base_dir}")
    if action_filter_set:
        print(f"필터: {', '.join(action_filter_set)}")
    print(f"{'='*70}")

    # 동작별 폴더 순회
    stats = {}
    total_videos = 0
    total_sequences = 0

    for action_dir in sorted(video_dir.iterdir()):
        if not action_dir.is_dir():
            continue

        action_name = action_dir.name.upper()

        # 동작 필터 적용
        if action_filter_set and action_name not in action_filter_set:
            continue

        if action_name not in SUPPORTED_ACTIONS:
            print(f"⚠️  지원되지 않는 동작 폴더 건너뜀: {action_name}")
            continue

        # 해당 동작 폴더의 동영상 파일 찾기
        video_files = []
        for ext in VIDEO_EXTENSIONS:
            video_files.extend(action_dir.glob(f"*{ext}"))

        if not video_files:
            print(f"⚠️  {action_name} 폴더에 동영상 없음")
            continue

        action_sequences = 0

        for video_file in sorted(video_files):
            person_name, detected_action = extract_person_and_action(video_file.stem)

            # 파일명에서 추출한 동작과 폴더명 비교
            if detected_action and detected_action != action_name:
                print(f"⚠️  동작 불일치: {video_file.name} (폴더: {action_name}, 파일: {detected_action})")
                continue

            # 인물명이 없으면 파일명 사용
            if not person_name:
                person_name = video_file.stem.split('_')[0] if '_' in video_file.stem else "UNKNOWN"

            # 출력 경로: output_base_dir/PERSON/ACTION/
            output_dir = output_base_dir / person_name / action_name

            try:
                sequences = extract_frames_from_video(
                    video_path=video_file,
                    action_name=action_name,
                    person_name=person_name,
                    output_dir=output_dir,
                    frames_per_sample=frames_per_sample,
                    start_offset=start_offset,
                    end_offset=end_offset,
                )

                action_sequences += sequences
                total_videos += 1

            except Exception as e:
                print(f"❌ {video_file.name} 처리 실패: {e}\n")
                continue

        stats[action_name] = action_sequences
        total_sequences += action_sequences

    # 요약
    print(f"\n{'='*70}")
    print(f"🎉 전체 처리 완료!")
    print(f"{'='*70}")
    print(f"처리된 동영상: {total_videos}개")
    print(f"추출된 시퀀스: {total_sequences}개")
    print(f"\n동작별 통계:")
    for action, count in sorted(stats.items()):
        print(f"  - {action}: {count}개 시퀀스")
    print(f"\n출력 폴더: {output_base_dir}")
    print(f"\n💡 다음 단계:")
    print(f"1. pose_sequence_extractor.py로 랜드마크 추출:")
    print(f"   python pose_sequence_extractor.py --data_dir {output_base_dir} --output_dir ./pose_sequences")
    print(f"\n2. 모델 학습:")
    print(f"   python train_gcn_cnn.py --data_dir ./pose_sequences --epochs 50")
    print(f"{'='*70}\n")

    return stats


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="100bpm 동작 동영상에서 동작별 맞춤 주기로 프레임 추출",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예제:
  # 전체 폴더 처리
  python extract_video_frames.py --video_dir ./origin_data --output_dir ./extracted_data

  # 특정 동작만 처리
  python extract_video_frames.py --video_dir ./origin_data --actions CLAP STRETCH

  # 시작 1초 건너뛰기 (카운트다운 등)
  python extract_video_frames.py --video_dir ./origin_data --start 1.0

  # 프레임 수 변경 (기본: 8)
  python extract_video_frames.py --video_dir ./origin_data --frames 16

동작별 주기:
  - CLAP: 0.6초 (1박자)
  - ELBOW, STRETCH, TILT, EXIT, UNDERARM, STAY: 1.2초 (2박자)
        """,
    )

    parser.add_argument(
        "--video_dir",
        type=str,
        required=True,
        help="동영상 폴더 경로 (origin_data/)",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="extracted_data",
        help="출력 디렉토리 (기본: extracted_data)",
    )
    parser.add_argument(
        "--frames",
        type=int,
        default=8,
        help="각 시퀀스당 프레임 수 (기본: 8)",
    )
    parser.add_argument(
        "--start",
        type=float,
        default=0.0,
        help="동영상 시작 부분 건너뛰기 (초, 기본: 0)",
    )
    parser.add_argument(
        "--end",
        type=float,
        default=0.0,
        help="동영상 끝 부분 건너뛰기 (초, 기본: 0)",
    )
    parser.add_argument(
        "--actions",
        nargs="*",
        default=None,
        help=f"특정 동작만 처리 (예: CLAP STRETCH). 기본: 전체 ({', '.join(SUPPORTED_ACTIONS)})",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    video_dir = Path(args.video_dir)
    output_dir = Path(args.output_dir)

    action_filter = [a.upper() for a in args.actions] if args.actions else None

    process_video_directory(
        video_dir=video_dir,
        output_base_dir=output_dir,
        frames_per_sample=args.frames,
        start_offset=args.start,
        end_offset=args.end,
        action_filter=action_filter,
    )


if __name__ == "__main__":
    main()
