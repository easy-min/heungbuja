"""
학습 데이터와 테스트 데이터 비교

pose_sequences .npz vs extracted_data 이미지
두 데이터의 출처가 같은지 확인
"""

import numpy as np
import glob
from pathlib import Path

print("\n" + "=" * 80)
print("🔍 데이터 출처 비교")
print("=" * 80)

# 1. pose_sequences의 npz 파일 정보
print("\n📂 학습 데이터 (pose_sequences):")
pose_seq_dir = Path("app/brandnewTrain/pose_sequences")
if pose_seq_dir.exists():
    npz_files = list(pose_seq_dir.glob("**/*.npz"))
    print(f"   총 {len(npz_files)}개 npz 파일")

    # 샘플 하나 로드
    if npz_files:
        sample = np.load(npz_files[0], allow_pickle=True)
        print(f"\n   샘플: {npz_files[0].name}")
        print(f"   Keys: {list(sample.keys())}")
        if 'landmarks' in sample:
            print(f"   Shape: {sample['landmarks'].shape}")
        if 'source' in sample:
            print(f"   Source: {sample['source']}")
        if 'person' in sample:
            print(f"   Person: {sample['person']}")
        if 'action' in sample:
            print(f"   Action: {sample['action']}")
else:
    print("   ❌ 폴더 없음")

# 2. pose_sequences_from_images의 npz 파일 정보
print("\n📂 이미지에서 생성한 데이터 (pose_sequences_from_images):")
pose_img_dir = Path("app/brandnewTrain/pose_sequences_from_images")
if pose_img_dir.exists():
    npz_files_img = list(pose_img_dir.glob("**/*.npz"))
    print(f"   총 {len(npz_files_img)}개 npz 파일")

    # 샘플 하나 로드
    if npz_files_img:
        sample = np.load(npz_files_img[0], allow_pickle=True)
        print(f"\n   샘플: {npz_files_img[0].name}")
        print(f"   Keys: {list(sample.keys())}")
        if 'landmarks' in sample:
            print(f"   Shape: {sample['landmarks'].shape}")
        if 'source' in sample:
            print(f"   Source: {sample['source']}")
        if 'person' in sample:
            print(f"   Person: {sample['person']}")
        if 'action' in sample:
            print(f"   Action: {sample['action']}")
else:
    print("   ❌ 폴더 없음")

# 3. extracted_data 이미지 정보
print("\n📂 테스트 데이터 (extracted_data 이미지):")
extracted_dir = Path("app/brandnewTrain/extracted_data")
if extracted_dir.exists():
    jpg_files = list(extracted_dir.glob("**/*.jpg"))
    jpg_files = [f for f in jpg_files if "_backup" not in str(f)]
    print(f"   총 {len(jpg_files)}개 jpg 파일")

    # 시퀀스 개수 계산
    sequences = set()
    for jpg in jpg_files:
        name = jpg.name
        parts = name.split("_frame")
        if len(parts) == 2:
            sequences.add(parts[0])

    print(f"   총 시퀀스 개수: {len(sequences)}개")
else:
    print("   ❌ 폴더 없음")

# 4. 비교
print("\n" + "=" * 80)
print("📊 비교 분석:")
print("=" * 80)

print("\n❓ 핵심 질문:")
print("   1. pose_sequences .npz는 어디서 왔나?")
print("      → origin_data 비디오에서 추출?")
print("   2. extracted_data 이미지는 어디서 왔나?")
print("      → origin_data 비디오와 같은 출처?")
print("   3. 둘의 출처가 다르면 → 분포가 달라서 모델이 작동 안 함!")

print("\n💡 해결책:")
print("   Option 1: pose_sequences_from_images로 학습")
print("      → extracted_data 이미지 → .npz → 학습")
print("      → 테스트 데이터와 동일한 출처")
print("")
print("   Option 2: origin_data 비디오로 통일")
print("      → 비디오 → 이미지 → .npz → 학습")
print("      → 비디오 → 이미지 → 테스트")

print("\n" + "=" * 80)
print("🎯 다음 단계:")
print("=" * 80)
print("pose_sequences_from_images로 재학습을 완료했나요?")
print("완료했다면 그 모델이 왜 작동 안 하는지 디버깅 필요!")
print("=" * 80 + "\n")
