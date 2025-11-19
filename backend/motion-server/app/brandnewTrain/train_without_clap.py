"""
CLAP을 제외한 6개 동작으로 학습

CLAP이 다른 동작을 압도하는지 확인하기 위한 실험용 스크립트
"""

import sys
import subprocess

# CLAP을 제외한 동작들
ACTIONS_WITHOUT_CLAP = ["ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]

print("=" * 80)
print("🆕 CLAP 제외 학습 시작")
print("=" * 80)
print(f"학습 동작: {', '.join(ACTIONS_WITHOUT_CLAP)}")
print(f"제외 동작: CLAP")
print("=" * 80 + "\n")

# train_gcn_cnn.py 호출 (CLAP 제외)
cmd = [
    sys.executable,
    "train_gcn_cnn.py",
    "--data_dir", "./pose_sequences",
    "--epochs", "150",
    "--actions", *ACTIONS_WITHOUT_CLAP,
    "--save_name", "brandnew_no_clap_v1.pt",
    "--batch_size", "32",
]

print(f"실행 명령: {' '.join(cmd)}\n")

result = subprocess.run(cmd)

if result.returncode == 0:
    print("\n" + "=" * 80)
    print("✅ 학습 완료!")
    print("=" * 80)
    print("모델 저장 위치: app/brandnewTrain/checkpoints/brandnew_no_clap_v1.pt")
    print("=" * 80)
else:
    print("\n❌ 학습 실패!")
    sys.exit(1)
