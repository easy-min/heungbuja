"""
180도 뒤집어진 이미지를 보정하는 스크립트

사용법:
    python fix_180_rotation.py
"""

from pathlib import Path
import cv2

# 180도 회전이 필요한 폴더 목록
FOLDERS_180 = [
    "extracted_data/JSY/STRETCH",
    "extracted_data/JSY/TILT",
    "extracted_data/JSY/UNDERARM",
    "extracted_data/KSM/STAY",
    "extracted_data/KSM/TILT",
    "extracted_data/LJM/TILT",
    "extracted_data/PHE/STAY",
    "extracted_data/PJE/STAY",
    "extracted_data/PJE/TILT",
    "extracted_data/YHS/TILT",
    "extracted_data/YHS/UNDERARM",
]


def rotate_180(folder_path: Path) -> None:
    """
    폴더 내 모든 .jpg 파일을 180도 회전
    (_backup.jpg는 제외)
    """
    if not folder_path.exists():
        print(f"⚠️  폴더 없음: {folder_path}")
        return

    jpg_files = list(folder_path.glob("*.jpg"))
    # _backup.jpg는 제외
    jpg_files = [f for f in jpg_files if "_backup.jpg" not in f.name]

    if not jpg_files:
        print(f"⚠️  이미지 없음: {folder_path}")
        return

    print(f"\n{'='*70}")
    print(f"📂 {folder_path}")
    print(f"{'='*70}")

    processed = 0
    failed = 0

    for img_path in jpg_files:
        try:
            # 이미지 읽기
            img = cv2.imread(str(img_path))
            if img is None:
                print(f"  ❌ 읽기 실패: {img_path.name}")
                failed += 1
                continue

            # 180도 회전
            rotated = cv2.rotate(img, cv2.ROTATE_180)

            # 덮어쓰기
            success = cv2.imwrite(str(img_path), rotated)
            if not success:
                print(f"  ❌ 저장 실패: {img_path.name}")
                failed += 1
                continue

            processed += 1

            if processed == 1 or processed % 100 == 0:
                print(f"  ✓ {processed}개 처리 완료...")

        except Exception as e:
            print(f"  ❌ 에러: {img_path.name} - {e}")
            failed += 1

    print(f"\n✅ {folder_path.name}: 성공 {processed}개, 실패 {failed}개")


def main() -> None:
    base_dir = Path(".")

    print(f"\n{'='*70}")
    print(f"🔄 180도 회전 보정 시작")
    print(f"{'='*70}")
    print(f"대상 폴더: {len(FOLDERS_180)}개\n")

    total_processed = 0
    total_failed = 0

    for folder_rel in FOLDERS_180:
        folder_path = base_dir / folder_rel
        rotate_180(folder_path)

    print(f"\n{'='*70}")
    print(f"🎉 전체 처리 완료!")
    print(f"{'='*70}")
    print(f"\n💡 다음 단계:")
    print(f"python pose_sequence_extractor.py --data_dir ./extracted_data --output_dir ./pose_sequences --overwrite")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
