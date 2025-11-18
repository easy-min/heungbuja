"""
모델 체크포인트 정보 확인 스크립트
"""
import torch
from pathlib import Path

model_path = Path(__file__).parent / "app" / "trained_model" / "gcn_cnn_best.pt"

print(f"\n{'='*80}")
print(f"모델 파일: {model_path}")
print(f"{'='*80}\n")

checkpoint = torch.load(model_path, map_location="cpu", weights_only=False)

print("🔍 체크포인트 키:")
for key in checkpoint.keys():
    print(f"  - {key}")

print("\n📋 Args (학습 설정):")
args = checkpoint.get("args", {})
for k, v in args.items():
    print(f"  {k}: {v}")

print("\n🏷️ Class Mapping (클래스 매핑):")
class_mapping = checkpoint.get("class_mapping", {})
for label, idx in sorted(class_mapping.items(), key=lambda x: x[1]):
    print(f"  {idx}: {label}")

print("\n📊 모델 상태:")
model_state = checkpoint.get("model_state_dict", {})
print(f"  총 파라미터 수: {len(model_state)} 개")

# 첫 번째 레이어 확인
if "gcn_layers.0.linear.weight" in model_state:
    weight = model_state["gcn_layers.0.linear.weight"]
    print(f"  입력 차원: {weight.shape[1]}")
    print(f"  GCN hidden: {weight.shape[0]}")

# 최종 출력 레이어 확인
for key in model_state.keys():
    if "fc" in key and "weight" in key:
        fc_weight = model_state[key]
        print(f"  출력 클래스 수: {fc_weight.shape[0]}")
        break

print("\n" + "="*80)
