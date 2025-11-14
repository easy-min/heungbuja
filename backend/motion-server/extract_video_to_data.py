"""
동영상에서 동작 데이터 자동 추출 스크립트

100bpm, 8박자 주기로 반복되는 동작 동영상을 분석하여
train.py에 바로 사용할 수 있는 학습 데이터로 변환합니다.

사용법:
    # 폴더 일괄 처리 (권장!)
    python extract_video_to_data.py --video_dir ./videos

    # 단일 동영상 처리
    python extract_video_to_data.py --video clap_video.mp4 --action CLAP

    # 시작 1초 건너뛰기
    python extract_video_to_data.py --video_dir ./videos --start 1.0

출력:
    data/
    └── CLAP/
        ├── clap_seq001_frame1.jpg
        ├── clap_seq001_frame2.jpg
        ├── ...
        ├── clap_seq001_frame8.jpg
        ├── clap_seq002_frame1.jpg
        └── ...
"""

import argparse
import cv2
from pathlib import Path
import numpy as np
import re


# 지원하는 동작 목록
SUPPORTED_ACTIONS = ["CLAP", "ELBOW", "HIP", "STRETCH", "TILT"]

# 비디오 파일 확장자
VIDEO_EXTENSIONS = [".mp4", ".avi", ".mov", ".mkv", ".MP4", ".AVI", ".MOV", ".MKV"]


def detect_action_from_filename(filename: str) -> str | None:
    """
    파일명에서 동작 이름 자동 감지

    예시:
        CLAP.mp4 → CLAP
        clap_video.mp4 → CLAP
        my_elbow_test.mp4 → ELBOW
        박수.mp4 → None (지원 안 함)
    """
    filename_upper = filename.upper()

    for action in SUPPORTED_ACTIONS:
        if action in filename_upper:
            return action

    return None


def process_video_directory(
    video_dir: Path,
    output_base_dir: Path,
    bpm: int = 100,
    beats_per_cycle: int = 8,
    frames_per_sample: int = 8,
    start_offset: float = 0.0,
    end_offset: float = 0.0,
):
    """
    폴더 내 모든 동영상을 일괄 처리

    Args:
        video_dir: 동영상 파일들이 있는 폴더
        output_base_dir: 출력 기본 디렉토리 (data/)
        기타 매개변수는 extract_frames_from_video와 동일
    """
    video_dir = Path(video_dir)
    if not video_dir.exists():
        raise FileNotFoundError(f"동영상 폴더를 찾을 수 없습니다: {video_dir}")

    if not video_dir.is_dir():
        raise ValueError(f"폴더가 아닙니다: {video_dir}")

    # 비디오 파일 찾기
    video_files = []
    for ext in VIDEO_EXTENSIONS:
        video_files.extend(video_dir.glob(f"*{ext}"))

    if not video_files:
        print(f"⚠️  {video_dir} 폴더에서 비디오 파일을 찾을 수 없습니다.")
        print(f"지원 형식: {', '.join(VIDEO_EXTENSIONS)}")
        return

    print(f"\n{'='*70}")
    print(f"📁 폴더 일괄 처리 시작")
    print(f"{'='*70}")
    print(f"동영상 폴더: {video_dir}")
    print(f"출력 폴더: {output_base_dir}")
    print(f"발견된 동영상: {len(video_files)}개")
    print(f"{'='*70}\n")

    # 각 동영상 처리
    processed = 0
    skipped = 0

    for video_file in sorted(video_files):
        action_name = detect_action_from_filename(video_file.stem)

        if action_name is None:
            print(f"⚠️  건너뜀: {video_file.name} (동작 이름을 파일명에서 찾을 수 없음)")
            print(f"    파일명에 다음 중 하나가 포함되어야 합니다: {', '.join(SUPPORTED_ACTIONS)}\n")
            skipped += 1
            continue

        output_dir = output_base_dir / action_name

        try:
            extract_frames_from_video(
                video_path=video_file,
                action_name=action_name,
                output_dir=output_dir,
                bpm=bpm,
                beats_per_cycle=beats_per_cycle,
                frames_per_sample=frames_per_sample,
                start_offset=start_offset,
                end_offset=end_offset,
            )
            processed += 1
        except Exception as e:
            print(f"❌ {video_file.name} 처리 실패: {e}\n")
            skipped += 1

    print(f"\n{'='*70}")
    print(f"🎉 폴더 일괄 처리 완료!")
    print(f"{'='*70}")
    print(f"처리 완료: {processed}개")
    print(f"건너뜀: {skipped}개")
    print(f"출력 폴더: {output_base_dir}")
    print(f"\n💡 학습 명령어:")
    print(f"python train.py --data_dir {output_base_dir} --frames_per_sample {frames_per_sample} --epochs 50")
    print(f"{'='*70}\n")


def extract_frames_from_video(
    video_path: Path,
    action_name: str,
    output_dir: Path,
    bpm: int = 100,
    beats_per_cycle: int = 8,
    frames_per_sample: int = 8,
    start_offset: float = 0.0,
    end_offset: float = 0.0,
):
    """
    동영상에서 BPM 주기에 맞춰 프레임을 추출하여 학습 데이터로 저장

    Args:
        video_path: 입력 동영상 경로
        action_name: 동작 이름 (예: CLAP, ELBOW)
        output_dir: 출력 디렉토리 (data/{action_name})
        bpm: 비트 속도 (기본: 100)
        beats_per_cycle: 한 동작 사이클이 차지하는 박자 수 (기본: 8)
        frames_per_sample: 각 샘플당 추출할 프레임 수 (기본: 8, train.py와 일치)
        start_offset: 동영상 시작 부분 건너뛰기 (초)
        end_offset: 동영상 끝 부분 건너뛰기 (초)
    """
    video_path = Path(video_path)
    if not video_path.exists():
        raise FileNotFoundError(f"동영상 파일을 찾을 수 없습니다: {video_path}")

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n{'='*70}")
    print(f"📹 동영상 분석 시작")
    print(f"{'='*70}")
    print(f"입력 파일: {video_path}")
    print(f"동작 이름: {action_name}")
    print(f"출력 경로: {output_dir}")
    print(f"BPM: {bpm}")
    print(f"박자/사이클: {beats_per_cycle}박자")
    print(f"프레임/샘플: {frames_per_sample}개")
    print(f"{'='*70}\n")

    # 동영상 열기
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"동영상을 열 수 없습니다: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = total_frames / fps

    print(f"📊 동영상 정보:")
    print(f"  - FPS: {fps:.2f}")
    print(f"  - 총 프레임: {total_frames}")
    print(f"  - 길이: {duration:.2f}초\n")

    # 주기 계산
    seconds_per_beat = 60.0 / bpm
    seconds_per_cycle = seconds_per_beat * beats_per_cycle
    frames_per_cycle = int(fps * seconds_per_cycle)

    print(f"⏱️  주기 계산:")
    print(f"  - 1박자: {seconds_per_beat:.3f}초")
    print(f"  - 1사이클 ({beats_per_cycle}박자): {seconds_per_cycle:.3f}초")
    print(f"  - 1사이클: {frames_per_cycle} 프레임\n")

    # 오프셋 적용
    start_frame = int(fps * start_offset)
    end_frame = total_frames - int(fps * end_offset)
    usable_frames = end_frame - start_frame

    if start_offset > 0 or end_offset > 0:
        print(f"✂️  오프셋 적용:")
        print(f"  - 시작 건너뛰기: {start_offset}초 ({start_frame} 프레임)")
        print(f"  - 끝 건너뛰기: {end_offset}초")
        print(f"  - 사용 가능 구간: {usable_frames} 프레임 ({usable_frames/fps:.2f}초)\n")

    # 예상 샘플 수 계산
    max_cycles = usable_frames // frames_per_cycle
    print(f"🎯 예상 추출 샘플: {max_cycles}개\n")

    # 프레임 추출
    cycle_idx = 0
    saved_count = 0

    while True:
        # 현재 사이클 시작 프레임
        cycle_start_frame = start_frame + cycle_idx * frames_per_cycle
        cycle_end_frame = cycle_start_frame + frames_per_cycle

        if cycle_end_frame > end_frame:
            break

        sample_frames = []

        # 이 사이클에서 균등하게 frames_per_sample개 추출
        for i in range(frames_per_sample):
            # 선형 보간으로 균등 샘플링
            progress = i / (frames_per_sample - 1) if frames_per_sample > 1 else 0
            target_frame = int(cycle_start_frame + progress * frames_per_cycle)

            # 범위 체크
            if target_frame >= end_frame:
                target_frame = end_frame - 1

            cap.set(cv2.CAP_PROP_POS_FRAMES, target_frame)
            ret, frame = cap.read()

            if not ret:
                print(f"⚠️  프레임 {target_frame} 읽기 실패")
                break

            sample_frames.append(frame)

        # frames_per_sample개 모두 추출했으면 저장
        if len(sample_frames) == frames_per_sample:
            seq_number = cycle_idx + 1
            for frame_num, frame in enumerate(sample_frames, 1):
                filename = output_dir / f"{action_name.lower()}_seq{seq_number:03d}_frame{frame_num}.jpg"
                cv2.imwrite(str(filename), frame)

            saved_count += frames_per_sample
            print(f"✓ [{seq_number:3d}] {action_name}_seq{seq_number:03d}_frame1~8.jpg 저장")
            cycle_idx += 1
        else:
            print(f"⚠️  사이클 {cycle_idx + 1}: 프레임 부족 ({len(sample_frames)}/{frames_per_sample})")
            break

    cap.release()

    print(f"\n{'='*70}")
    print(f"✅ 추출 완료!")
    print(f"{'='*70}")
    print(f"총 샘플: {cycle_idx}개")
    print(f"총 이미지: {saved_count}개")
    print(f"저장 경로: {output_dir}")
    print(f"\n💡 학습 명령어:")
    print(f"python train.py --data_dir ./data --frames_per_sample {frames_per_sample} --epochs 50")
    print(f"{'='*70}\n")


def main():
    parser = argparse.ArgumentParser(
        description="100bpm 8박자 동작 동영상에서 학습 데이터 자동 추출",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예제:
  # 폴더 일괄 처리 (권장!)
  python extract_video_to_data.py --video_dir ./videos

  # 폴더 일괄 처리 + 시작 1초 건너뛰기
  python extract_video_to_data.py --video_dir ./videos --start 1.0

  # 단일 동영상 처리
  python extract_video_to_data.py --video clap.mp4 --action CLAP

파일명 규칙 (폴더 일괄 처리 시):
  - CLAP.mp4, clap_video.mp4 → CLAP으로 인식
  - ELBOW.mp4, my_elbow.mp4 → ELBOW로 인식
  - 파일명에 CLAP, ELBOW, HIP, STRETCH, TILT 중 하나 포함 필요
        """,
    )

    # 입력 방식 선택 (상호 배타적)
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument(
        "--video_dir",
        type=str,
        help="동영상 폴더 경로 (폴더 내 모든 비디오를 자동 처리)",
    )
    input_group.add_argument(
        "--video",
        type=str,
        help="단일 동영상 파일 경로",
    )

    parser.add_argument(
        "--action",
        type=str,
        default=None,
        help="동작 이름 (--video 사용 시 필수)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="출력 디렉토리 (기본: ./data)",
    )
    parser.add_argument(
        "--bpm",
        type=int,
        default=100,
        help="비트 속도 (기본: 100)",
    )
    parser.add_argument(
        "--beats",
        type=int,
        default=8,
        help="한 동작 사이클의 박자 수 (기본: 8박자)",
    )
    parser.add_argument(
        "--frames",
        type=int,
        default=8,
        help="각 샘플당 프레임 수 (기본: 8, train.py와 동일)",
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
    args = parser.parse_args()

    # 폴더 일괄 처리 모드
    if args.video_dir:
        output_base_dir = Path(args.output) if args.output else Path("data")

        process_video_directory(
            video_dir=args.video_dir,
            output_base_dir=output_base_dir,
            bpm=args.bpm,
            beats_per_cycle=args.beats,
            frames_per_sample=args.frames,
            start_offset=args.start,
            end_offset=args.end,
        )

    # 단일 동영상 처리 모드
    elif args.video:
        if not args.action:
            parser.error("--video 사용 시 --action이 필수입니다.")

        if args.output is None:
            output_dir = Path("data") / args.action.upper()
        else:
            output_dir = Path(args.output)

        extract_frames_from_video(
            video_path=args.video,
            action_name=args.action.upper(),
            output_dir=output_dir,
            bpm=args.bpm,
            beats_per_cycle=args.beats,
            frames_per_sample=args.frames,
            start_offset=args.start,
            end_offset=args.end,
        )


if __name__ == "__main__":
    main()
