"""
학습 시 train/val split 확인

test_brandnew_server.py가 테스트하는 샘플들이
validation set에 포함되었는지 확인
"""
import random
from pathlib import Path
from collections import defaultdict

# train_gcn_cnn.py와 동일한 split 로직
def split_samples(sample_paths, val_split=0.2, seed=42):
    samples = list(sample_paths)
    random.Random(seed).shuffle(samples)

    val_size = int(round(len(samples) * val_split))
    val_size = max(1, min(val_size, len(samples) - 1))

    val_samples = samples[:val_size]
    train_samples = samples[val_size:]
    return train_samples, val_samples

# 데이터 수집
data_dir = Path("app/brandnewTrain/pose_sequences_from_images")
actions = ["ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]

print("\n" + "=" * 80)
print("🔍 Train/Val Split 분석")
print("=" * 80)

all_samples = []
for action in actions:
    npz_files = list(data_dir.glob(f"**/{action}/*.npz"))
    all_samples.extend(npz_files)

print(f"\n총 샘플 수: {len(all_samples)}개")

# Split
train_samples, val_samples = split_samples(all_samples, val_split=0.2, seed=42)

print(f"Train: {len(train_samples)}개 ({len(train_samples)/len(all_samples)*100:.1f}%)")
print(f"Val: {len(val_samples)}개 ({len(val_samples)/len(all_samples)*100:.1f}%)")

# test_brandnew_server.py가 테스트하는 샘플들
test_sequences = [
    "JSY/ELBOW/elbow_seq001.npz",
    "JSY/ELBOW/elbow_seq002.npz",
    "JSY/ELBOW/elbow_seq003.npz",
    "JSY/STRETCH/stretch_seq001.npz",
    "JSY/STRETCH/stretch_seq002.npz",
    "JSY/STRETCH/stretch_seq003.npz",
    "JSY/TILT/tilt_seq001.npz",
    "JSY/TILT/tilt_seq002.npz",
    "JSY/TILT/tilt_seq003.npz",
    "JSY/EXIT/exit_seq001.npz",
    "JSY/EXIT/exit_seq002.npz",
    "JSY/EXIT/exit_seq003.npz",
    "JSY/UNDERARM/underarm_seq001.npz",
    "JSY/UNDERARM/underarm_seq002.npz",
    "JSY/UNDERARM/underarm_seq003.npz",
    "JSY/STAY/stay_seq001.npz",
    "JSY/STAY/stay_seq002.npz",
    "JSY/STAY/stay_seq003.npz",
]

print("\n" + "=" * 80)
print("📋 테스트 샘플 분석")
print("=" * 80)

val_set = set(str(p) for p in val_samples)
train_set = set(str(p) for p in train_samples)

in_train = 0
in_val = 0
not_found = 0

for test_seq in test_sequences:
    # 전체 경로로 변환
    full_path = data_dir / test_seq
    full_path_str = str(full_path)

    if full_path_str in train_set:
        print(f"🔵 TRAIN: {test_seq}")
        in_train += 1
    elif full_path_str in val_set:
        print(f"🟢 VAL:   {test_seq}")
        in_val += 1
    else:
        print(f"❌ NONE:  {test_seq}")
        not_found += 1

print("\n" + "=" * 80)
print("📊 결과")
print("=" * 80)
print(f"Train에 있음: {in_train}개")
print(f"Val에 있음:   {in_val}개")
print(f"없음:         {not_found}개")

print("\n💡 분석:")
if in_val > 0:
    print(f"   ✅ Validation에 {in_val}개 테스트 샘플이 포함됨")
    print(f"   → Validation 정확도가 높은 이유!")
if in_train > 0:
    print(f"   ⚠️  Train에 {in_train}개 테스트 샘플이 포함됨")
    print(f"   → 이 샘플들은 학습에 사용됨 (data leakage)")
if not_found > 0:
    print(f"   ❌ {not_found}개는 train/val 어디에도 없음")
    print(f"   → 완전히 unseen data (진짜 테스트)")

print("=" * 80 + "\n")
