"""
추출된 이미지 중 회전이 필요한 이미지를 자동으로 보정하는 스크립트

1920x1080 이미지가 1080x1920으로 되어 있으면 시계방향 90도 회전 적용

사용법:
    # 폴더 전체 처리
    python rotate_extracted_images.py --dir ./extracted_data

    # 특정 인물/동작만 처리
    python rotate_extracted_images.py --dir ./extracted_data/KSM/CLAP
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import List

import cv2
import numpy as np


def find_rotated_images(base_dir: Path) -> List[Path]:
    """
    가로가 세로보다 긴 이미지 찾기

    정상: 720x1280 또는 1080x1920 (세로 화면)
    회전 필요: 1280x720 또는 1920x1080 (가로 화면)

    Returns:
        회전이 필요한 이미지 경로 리스트
    """
    rotated_images = []

    for image_path in base_dir.rglob("*.jpg"):
        # _backup.jpg는 건너뛰기
        if "_backup.jpg" in image_path.name:
            continue

        img = cv2.imread(str(image_path))
        if img is None:
            continue

        height, width = img.shape[:2]

        # 가로가 세로보다 길면 회전 필요
        if width > height:
            rotated_images.append(image_path)

    return rotated_images


def rotate_image_clockwise_90(image_path: Path, backup: bool = True) -> None:
    """
    이미지를 반시계방향 90도 회전 (1280x720 → 720x1280)

    Args:
        image_path: 이미지 경로
        backup: 원본 백업 여부
    """
    img = cv2.imread(str(image_path))
    if img is None:
        raise RuntimeError(f"이미지를 열 수 없습니다: {image_path}")

    # 백업 (원본 그대로)
    if backup:
        backup_path = image_path.parent / (image_path.stem + "_backup.jpg")
        if not backup_path.exists():
            success = cv2.imwrite(str(backup_path), img)
            if not success:
                raise RuntimeError(f"백업 파일 저장 실패: {backup_path}")

    # 반시계방향 90도 회전 (가로 → 세로)
    rotated = cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE)

    # 덮어쓰기
    success = cv2.imwrite(str(image_path), rotated)
    if not success:
        raise RuntimeError(f"회전된 이미지 저장 실패: {image_path}")


def process_directory(
    base_dir: Path,
    dry_run: bool = False,
    backup: bool = True,
) -> None:
    """
    디렉토리 내 모든 회전이 필요한 이미지 처리

    Args:
        base_dir: 처리할 디렉토리
        dry_run: True면 실제 회전 안 하고 목록만 출력
        backup: 원본 백업 여부
    """
    print(f"\n{'='*70}")
    print(f"🔍 회전이 필요한 이미지 검색 중...")
    print(f"{'='*70}")
    print(f"디렉토리: {base_dir}")
    print(f"{'='*70}\n")

    rotated_images = find_rotated_images(base_dir)

    if not rotated_images:
        print("✅ 회전이 필요한 이미지가 없습니다!")
        return

    print(f"📊 발견된 이미지: {len(rotated_images)}개\n")

    if dry_run:
        print("🔍 Dry-run 모드: 회전이 필요한 이미지 목록")
        print(f"{'='*70}")
        for i, img_path in enumerate(rotated_images[:20], 1):
            img = cv2.imread(str(img_path))
            height, width = img.shape[:2]
            relative_path = img_path.relative_to(base_dir)
            print(f"{i:3d}. {relative_path} ({width}x{height})")

        if len(rotated_images) > 20:
            print(f"     ... 외 {len(rotated_images) - 20}개")

        print(f"\n💡 실제 회전하려면 --dry-run 없이 실행하세요")
        return

    # 실제 회전 처리
    print(f"🔄 회전 처리 시작 (백업: {'예' if backup else '아니오'})")
    print(f"{'='*70}\n")

    processed = 0
    failed = 0

    for i, img_path in enumerate(rotated_images, 1):
        try:
            # 원본 크기 확인
            img = cv2.imread(str(img_path))
            old_height, old_width = img.shape[:2]

            # 회전
            rotate_image_clockwise_90(img_path, backup=backup)

            # 회전 후 크기 확인
            img_after = cv2.imread(str(img_path))
            new_height, new_width = img_after.shape[:2]

            processed += 1

            if processed % 50 == 0:
                print(f"  ✓ {processed}/{len(rotated_images)} 처리 중...")
            elif processed <= 10 or processed == len(rotated_images):
                relative_path = img_path.relative_to(base_dir)
                print(f"  ✓ [{i:3d}] {relative_path}")
                print(f"        {old_width}x{old_height} → {new_width}x{new_height}")

        except Exception as e:
            print(f"  ❌ 실패: {img_path.name} - {e}")
            failed += 1

    print(f"\n{'='*70}")
    print(f"✅ 처리 완료!")
    print(f"{'='*70}")
    print(f"성공: {processed}개")
    print(f"실패: {failed}개")

    if backup:
        print(f"\n💡 원본은 _backup.jpg 파일로 백업되었습니다")
        print(f"   문제가 없다면 다음 명령으로 백업 삭제:")
        print(f"   find {base_dir} -name '*_backup.jpg' -delete")

    print(f"{'='*70}\n")


def restore_from_backup(base_dir: Path) -> None:
    """
    백업 파일에서 복원
    """
    backup_files = list(base_dir.rglob("*_backup.jpg"))

    if not backup_files:
        print("백업 파일이 없습니다.")
        return

    print(f"\n{'='*70}")
    print(f"🔄 백업에서 복원 중...")
    print(f"{'='*70}")
    print(f"백업 파일: {len(backup_files)}개\n")

    restored = 0

    for backup_path in backup_files:
        # clap_seq001_frame1_backup.jpg → clap_seq001_frame1.jpg
        original_name = backup_path.stem.replace("_backup", "") + ".jpg"
        original_path = backup_path.parent / original_name

        try:
            # 백업 파일을 원본으로 복사
            img = cv2.imread(str(backup_path))
            cv2.imwrite(str(original_path), img)

            # 백업 파일 삭제
            backup_path.unlink()

            restored += 1

            if restored % 50 == 0:
                print(f"  ✓ {restored}/{len(backup_files)} 복원 중...")

        except Exception as e:
            print(f"  ❌ 실패: {backup_path.name} - {e}")

    print(f"\n✅ 복원 완료: {restored}개")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="세로로 잘못 회전된 이미지를 자동으로 보정",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예제:
  # 목록만 확인 (실제 회전 안 함)
  python rotate_extracted_images.py --dir ./extracted_data --dry-run

  # 실제 회전 처리 (백업 자동 생성)
  python rotate_extracted_images.py --dir ./extracted_data

  # 백업 없이 회전
  python rotate_extracted_images.py --dir ./extracted_data --no-backup

  # 백업에서 복원
  python rotate_extracted_images.py --dir ./extracted_data --restore
        """,
    )

    parser.add_argument(
        "--dir",
        type=str,
        required=True,
        help="처리할 디렉토리 (extracted_data/ 또는 하위 폴더)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="실제 회전 안 하고 목록만 출력",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="백업 파일 생성 안 함 (주의!)",
    )
    parser.add_argument(
        "--restore",
        action="store_true",
        help="백업 파일에서 복원",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    base_dir = Path(args.dir)
    if not base_dir.exists():
        raise FileNotFoundError(f"디렉토리를 찾을 수 없습니다: {base_dir}")

    if args.restore:
        restore_from_backup(base_dir)
    else:
        process_directory(
            base_dir=base_dir,
            dry_run=args.dry_run,
            backup=not args.no_backup,
        )


if __name__ == "__main__":
    main()
