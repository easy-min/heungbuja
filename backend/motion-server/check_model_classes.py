"""
모델 파일에서 학습된 클래스 확인
"""
import torch
from pathlib import Path

model_path = Path(__file__).parent / "app" / "trained_model" / "gcn_cnn_best.pt"

print(f"Loading model from: {model_path}")
checkpoint = torch.load(model_path, map_location='cpu', weights_only=False)

class_mapping = checkpoint.get('class_mapping', {})

print("\n" + "="*60)
print("=== Model Class Mapping ===")
print("="*60)
print(f"Total classes: {len(class_mapping)}")
print("\nClass Index -> Action Name:")

for name, idx in sorted(class_mapping.items(), key=lambda x: x[1]):
    print(f"  class {idx}: {name}")

print("\n" + "="*60)
print("\nDB actionCode -> Model class_index mapping:")
print("(actionCode - 1 = class_index)")
print("="*60)

# 예상 매핑 출력
for name, idx in sorted(class_mapping.items(), key=lambda x: x[1]):
    action_code = idx + 1
    print(f"  actionCode {action_code} -> class {idx} ({name})")

print("\n")
