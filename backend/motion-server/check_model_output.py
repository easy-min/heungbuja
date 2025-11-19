"""
모델의 실제 출력값 확인

CLAP과 다른 클래스들의 확률 분포를 보여줍니다.
"""

import base64
import glob
import os

from app.services.brandnew_inference import get_brandnew_inference_service


def load_sample_images(action, seq_num=1):
    """샘플 이미지 로드"""
    base_path = f"app/brandnewTrain/extracted_data/JSY/{action.upper()}"

    frames = []
    for i in range(1, 9):
        img_path = os.path.join(base_path, f"{action.lower()}_seq{seq_num:03d}_frame{i}.jpg")

        if not os.path.exists(img_path):
            print(f"⚠️  파일 없음: {img_path}")
            return None

        with open(img_path, "rb") as f:
            img_data = f.read()
            b64 = base64.b64encode(img_data).decode("utf-8")
            frames.append(b64)

    return frames


print("\n" + "=" * 80)
print("🔍 모델 출력 확률 분석")
print("=" * 80)

service = get_brandnew_inference_service()

print(f"\n모델 클래스 매핑: {service.id_to_label}")
print(f"클래스 개수: {len(service.id_to_label)}")

# 각 동작별로 테스트
actions = ["CLAP", "ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]

for action in actions:
    print(f"\n{'=' * 80}")
    print(f"📂 {action} 테스트")
    print('=' * 80)

    frames = load_sample_images(action, seq_num=1)
    if not frames:
        continue

    # 내부 추론 로직 직접 호출
    import torch
    import numpy as np
    from time import perf_counter

    sampled_frames = service._sample_frames(frames, service.frames_per_sample)
    keypoint_sequence, _, _ = service._frames_to_keypoints_corrected(sampled_frames)

    input_tensor = torch.from_numpy(keypoint_sequence).unsqueeze(0)
    input_tensor = input_tensor.to(service.device)

    with torch.no_grad():
        logits = service.model(input_tensor)
        probabilities = torch.softmax(logits, dim=-1).cpu().numpy()[0]

    print(f"\n📊 Logits (raw 출력):")
    for idx, logit in enumerate(logits.cpu().numpy()[0]):
        label = service.id_to_label.get(idx, "UNKNOWN")
        print(f"   {idx}: {label:10s} = {logit:8.4f}")

    print(f"\n📊 Probabilities (softmax 적용):")
    sorted_probs = sorted(enumerate(probabilities), key=lambda x: x[1], reverse=True)

    for idx, prob in sorted_probs:
        label = service.id_to_label.get(idx, "UNKNOWN")
        bar = "█" * int(prob * 50)
        print(f"   {idx}: {label:10s} = {prob*100:6.2f}% {bar}")

    # 상위 3개
    print(f"\n🏆 상위 3개 예측:")
    for rank, (idx, prob) in enumerate(sorted_probs[:3], 1):
        label = service.id_to_label.get(idx, "UNKNOWN")
        print(f"   {rank}위: {label} ({prob*100:.2f}%)")

print("\n" + "=" * 80)
print("✅ 분석 완료")
print("=" * 80)
print("\n💡 관찰 포인트:")
print("   1. CLAP의 확률이 항상 높은가?")
print("   2. 다른 클래스들의 확률 분포는?")
print("   3. 2등과의 확률 차이는?")
print("=" * 80 + "\n")
