"""
각 데이터 소스의 시퀀스 개수를 클래스별로 확인
"""

from pathlib import Path
from collections import Counter

print("\n" + "=" * 80)
print("📊 데이터 개수 상세 비교")
print("=" * 80)

actions = ["CLAP", "ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]

# 1. pose_sequences
print("\n1️⃣  pose_sequences (원본):")
pose_seq_dir = Path("app/brandnewTrain/pose_sequences")
if pose_seq_dir.exists():
    for action in actions:
        count = len(list(pose_seq_dir.glob(f"**/{action}/*.npz")))
        print(f"   {action:10s}: {count:4d}개")

# 2. pose_sequences_from_images
print("\n2️⃣  pose_sequences_from_images (이미지에서 생성):")
pose_img_dir = Path("app/brandnewTrain/pose_sequences_from_images")
if pose_img_dir.exists():
    for action in actions:
        count = len(list(pose_img_dir.glob(f"**/{action}/*.npz")))
        print(f"   {action:10s}: {count:4d}개")

# 3. extracted_data (이미지)
print("\n3️⃣  extracted_data (테스트 이미지):")
extracted_dir = Path("app/brandnewTrain/extracted_data")
if extracted_dir.exists():
    for action in actions:
        # 시퀀스 개수 계산
        sequences = set()
        jpg_files = list(extracted_dir.glob(f"**/{action}/*.jpg"))
        for jpg in jpg_files:
            if "_backup" not in str(jpg):
                parts = jpg.name.split("_frame")
                if len(parts) == 2:
                    sequences.add(parts[0])
        print(f"   {action:10s}: {len(sequences):4d}개 시퀀스")

print("\n" + "=" * 80)
print("🔍 분석:")
print("=" * 80)
print("만약 pose_sequences_from_images와 extracted_data의 개수가 같다면:")
print("→ 데이터 출처는 동일, 학습은 제대로 됨")
print("→ 문제는 추론 시 전처리에 있음!")
print("\n만약 개수가 다르다면:")
print("→ 데이터 출처가 다름")
print("→ pose_sequences_from_images가 전체가 아닐 수 있음")
print("=" * 80 + "\n")
