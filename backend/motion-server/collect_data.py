"""
웹캠으로 동작 이미지 수집 스크립트

사용법:
    python collect_data.py --action CLAP --output data/CLAP --count 100

키 조작:
    SPACE: 이미지 캡처
    Q: 종료
"""

import argparse
import cv2
from pathlib import Path
import time


def collect_images(action_name: str, output_dir: Path, target_count: int = 100):
    """웹캠으로 이미지 수집"""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 기존 이미지 개수 확인
    existing_images = list(output_dir.glob("*.jpg"))
    start_idx = len(existing_images)

    print(f"\n{'='*60}")
    print(f"동작 이미지 수집 시작: {action_name}")
    print(f"저장 경로: {output_dir}")
    print(f"기존 이미지: {start_idx}개")
    print(f"목표: {target_count}개")
    print(f"{'='*60}\n")
    print("조작법:")
    print("  SPACE: 이미지 캡처")
    print("  Q: 종료")
    print(f"\n동작 '{action_name}'을(를) 취해주세요...\n")

    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("❌ 웹캠을 열 수 없습니다.")
        return

    count = start_idx
    last_capture_time = 0
    capture_interval = 0.5  # 0.5초 간격으로만 캡처 가능

    while count < target_count:
        ret, frame = cap.read()
        if not ret:
            print("❌ 프레임을 읽을 수 없습니다.")
            break

        # 화면에 정보 표시
        display_frame = frame.copy()
        text_y = 30
        cv2.putText(
            display_frame,
            f"Action: {action_name}",
            (10, text_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 255, 0),
            2,
        )
        text_y += 35
        cv2.putText(
            display_frame,
            f"Captured: {count}/{target_count}",
            (10, text_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 255, 0),
            2,
        )
        text_y += 35
        cv2.putText(
            display_frame,
            "SPACE: Capture | Q: Quit",
            (10, text_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (255, 255, 255),
            1,
        )

        cv2.imshow(f"Data Collection - {action_name}", display_frame)
        key = cv2.waitKey(1) & 0xFF

        current_time = time.time()

        if key == ord(" "):  # 스페이스바
            if current_time - last_capture_time >= capture_interval:
                filename = output_dir / f"img_{count:04d}.jpg"
                cv2.imwrite(str(filename), frame)
                print(f"✓ [{count+1}/{target_count}] 저장: {filename.name}")
                count += 1
                last_capture_time = current_time
            else:
                print("⚠️  너무 빨리 캡처하려고 했습니다. 잠시 후 다시 시도하세요.")

        elif key == ord("q"):  # Q키
            print("\n중단됨.")
            break

    cap.release()
    cv2.destroyAllWindows()

    print(f"\n{'='*60}")
    print(f"✅ 수집 완료!")
    print(f"총 {count}개 이미지 저장됨: {output_dir}")
    print(f"{'='*60}\n")


def main():
    parser = argparse.ArgumentParser(description="웹캠으로 동작 이미지 수집")
    parser.add_argument(
        "--action",
        type=str,
        required=True,
        help="동작 이름 (예: CLAP, WAVE, JUMP)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="저장 경로 (기본: data/{action})",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=100,
        help="수집할 이미지 개수 (기본: 100)",
    )
    args = parser.parse_args()

    # 출력 경로 설정
    if args.output is None:
        output_dir = Path("data") / args.action
    else:
        output_dir = Path(args.output)

    collect_images(args.action, output_dir, args.count)


if __name__ == "__main__":
    main()
