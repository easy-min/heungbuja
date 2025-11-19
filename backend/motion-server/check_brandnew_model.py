"""
Brandnew 모델 파일 검사

모델 파일의 내용을 확인하여 문제를 진단합니다.
"""

import torch
from pathlib import Path


def check_model():
    model_path = Path("app/brandnewTrain/checkpoints/brandnew_model_v1.pt")

    if not model_path.exists():
        print(f"❌ 모델 파일을 찾을 수 없습니다: {model_path}")
        return

    print(f"✅ 모델 파일 발견: {model_path}")
    print(f"📦 파일 크기: {model_path.stat().st_size / 1024 / 1024:.2f} MB\n")

    # 모델 로드
    checkpoint = torch.load(model_path, map_location="cpu", weights_only=False)

    print("=" * 80)
    print("📋 체크포인트 키 목록:")
    print("=" * 80)
    for key in checkpoint.keys():
        print(f"  - {key}")

    print("\n" + "=" * 80)
    print("🏷️  클래스 매핑 (class_mapping):")
    print("=" * 80)
    if "class_mapping" in checkpoint:
        class_mapping = checkpoint["class_mapping"]
        for label, idx in sorted(class_mapping.items(), key=lambda x: x[1]):
            print(f"  {idx}: {label}")
    else:
        print("  ❌ class_mapping이 없습니다!")

    print("\n" + "=" * 80)
    print("⚙️  학습 설정 (args):")
    print("=" * 80)
    if "args" in checkpoint:
        args = checkpoint["args"]
        for key, value in sorted(args.items()):
            print(f"  {key}: {value}")
    else:
        print("  ❌ args가 없습니다!")

    print("\n" + "=" * 80)
    print("📊 학습 정보:")
    print("=" * 80)

    if "epoch" in checkpoint:
        print(f"  Epoch: {checkpoint['epoch']}")

    if "best_val_acc" in checkpoint:
        print(f"  최고 검증 정확도: {checkpoint['best_val_acc']:.2%}")

    if "best_val_loss" in checkpoint:
        print(f"  최저 검증 손실: {checkpoint['best_val_loss']:.4f}")

    if "train_acc" in checkpoint:
        print(f"  학습 정확도: {checkpoint['train_acc']:.2%}")

    if "train_loss" in checkpoint:
        print(f"  학습 손실: {checkpoint['train_loss']:.4f}")

    print("\n" + "=" * 80)
    print("🔧 모델 구조 정보:")
    print("=" * 80)

    if "model_state_dict" in checkpoint:
        state_dict = checkpoint["model_state_dict"]

        # GCN 레이어 정보
        if "gcn_layers.0.adjacency" in state_dict:
            adj_shape = state_dict["gcn_layers.0.adjacency"].shape
            print(f"  노드 수 (num_nodes): {adj_shape[0]}")

        if "gcn_layers.0.linear.weight" in state_dict:
            weight_shape = state_dict["gcn_layers.0.linear.weight"].shape
            print(f"  입력 차원 (input_dim): {weight_shape[1]}")

        # 분류기 정보
        classifier_keys = [k for k in state_dict.keys() if "classifier" in k]
        if classifier_keys:
            last_classifier = sorted([k for k in classifier_keys if "weight" in k])[-1]
            num_classes = state_dict[last_classifier].shape[0]
            print(f"  클래스 수 (num_classes): {num_classes}")

    print("\n" + "=" * 80)
    print("🔍 잠재적 문제 진단:")
    print("=" * 80)

    issues = []

    # 클래스 매핑 확인
    if "class_mapping" not in checkpoint:
        issues.append("❌ class_mapping이 없습니다!")
    elif len(checkpoint["class_mapping"]) != 7:
        issues.append(f"⚠️  클래스 개수가 7개가 아닙니다: {len(checkpoint['class_mapping'])}개")

    # 정확도 확인
    if "best_val_acc" in checkpoint:
        if checkpoint["best_val_acc"] < 0.3:
            issues.append(f"⚠️  검증 정확도가 매우 낮습니다: {checkpoint['best_val_acc']:.2%}")
        elif checkpoint["best_val_acc"] < 0.5:
            issues.append(f"⚠️  검증 정확도가 낮습니다: {checkpoint['best_val_acc']:.2%}")

    # Epoch 확인
    if "epoch" in checkpoint:
        if checkpoint["epoch"] < 10:
            issues.append(f"⚠️  학습 에포크가 적습니다: {checkpoint['epoch']}epoch")

    if not issues:
        print("  ✅ 특별한 문제가 발견되지 않았습니다.")
    else:
        for issue in issues:
            print(f"  {issue}")

    print("=" * 80 + "\n")


if __name__ == "__main__":
    check_model()
