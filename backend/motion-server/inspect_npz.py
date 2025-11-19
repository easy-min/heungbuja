"""
.npz 파일 구조 확인
"""
import numpy as np
from pathlib import Path

# ELBOW 샘플 하나 로드
npz_files = list(Path('app/brandnewTrain/pose_sequences_from_images').glob('**/ELBOW/*.npz'))
if not npz_files:
    print("❌ ELBOW .npz 파일을 찾을 수 없습니다")
    exit(1)

sample_file = npz_files[0]
data = np.load(sample_file, allow_pickle=True)

print(f"📂 파일: {sample_file}")
print(f"🔑 Keys: {list(data.keys())}")
print(f"📊 landmarks shape: {data['landmarks'].shape}")
print(f"🏷️  action: {data.get('action', 'N/A')}")
print(f"👤 person: {data.get('person', 'N/A')}")
print(f"📍 source: {data.get('source', 'N/A')}")

print(f"\n🔍 데이터 샘플:")
print(f"   첫 프레임, 첫 landmark: {data['landmarks'][0,0,:]}")
print(f"   데이터 범위: min={data['landmarks'].min():.4f}, max={data['landmarks'].max():.4f}")

print(f"\n✅ .npz 파일이 정상적으로 생성되었습니다")
