"""
게임 데이터로 배포된 모델 테스트

game_data로 실제 배포 모델의 성능을 확인합니다.
"""

import sys
from pathlib import Path
import torch
import numpy as np
from collections import Counter, defaultdict

sys.path.insert(0, str(Path(__file__).parent))
from brandnewTrain import train_gcn_cnn


def main():
    print(f"\n{'='*70}")
    print("🎮 게임 데이터로 배포 모델 테스트")
    print(f"{'='*70}\n")

    device = train_gcn_cnn.auto_device("auto")
    checkpoint_path = Path("./app/brandnewTrain/checkpoints/brandnew_model_v2.pt")

    if not checkpoint_path.exists():
        print(f"❌ 모델을 찾을 수 없습니다: {checkpoint_path}")
        return

    # 모델 로드
    print(f"📂 모델 로딩: {checkpoint_path.name}\n")
    checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)

    # args 복원
    original_args = checkpoint["args"]
    if isinstance(original_args, dict):
        from argparse import Namespace
        original_args = Namespace(**original_args)

    # action_to_label 복원
    if "action_to_label" in checkpoint:
        action_to_label = checkpoint["action_to_label"]
    elif "class_mapping" in checkpoint:
        action_to_label = checkpoint["class_mapping"]
    else:
        raise KeyError("action_to_label을 찾을 수 없습니다")

    label_to_action = {label: action for action, label in action_to_label.items()}

    # 모델 생성
    input_dim = 2
    adjacency = train_gcn_cnn.build_adjacency(train_gcn_cnn.USED_LANDMARK_INDICES)

    model = train_gcn_cnn.GCNTemporalModel(
        input_dim=input_dim,
        num_classes=len(action_to_label),
        adjacency=adjacency,
        gcn_hidden_dims=original_args.gcn_hidden_dims,
        temporal_channels=original_args.temporal_channels,
        dropout=original_args.dropout,
    )

    model.load_state_dict(checkpoint["model_state_dict"])
    model.to(device)
    model.eval()

    print(f"✅ 모델 로딩 완료 (클래스 수: {len(action_to_label)})")
    print(f"   동작 레이블: {sorted(action_to_label.keys())}\n")

    # 게임 데이터 로드
    game_pose_dir = Path("./app/brandnewTrain/game_pose_sequences")

    if not game_pose_dir.exists():
        print(f"❌ 게임 pose 데이터를 찾을 수 없습니다: {game_pose_dir}")
        return

    print(f"{'='*70}")
    print("📊 게임 데이터 로딩")
    print(f"{'='*70}\n")

    game_samples = train_gcn_cnn.collect_samples(
        data_dir=game_pose_dir,
        action_to_label=action_to_label,
        frames_per_sample=8,
        persons=None,
        actions=list(action_to_label.keys()),
    )

    print(f"✅ 게임 데이터: {len(game_samples)}개 시퀀스")

    # 동작별 분포
    action_counts = Counter(sample.label for sample in game_samples)
    print(f"\n동작별 분포:")
    for label in sorted(action_counts.keys()):
        action = label_to_action[label]
        count = action_counts[label]
        print(f"  {action:10s}: {count:3d}개")

    # 평가
    print(f"\n{'='*70}")
    print("🔬 모델 평가 중...")
    print(f"{'='*70}\n")

    dataset = train_gcn_cnn.PoseSequenceDataset(game_samples, frames_per_sample=8)
    loader = torch.utils.data.DataLoader(
        dataset,
        batch_size=32,
        shuffle=False,
        num_workers=0,
    )

    all_preds = []
    all_labels = []

    with torch.no_grad():
        for sequences, labels in loader:
            sequences = sequences.to(device)
            labels = labels.to(device)

            outputs = model(sequences)
            preds = outputs.argmax(dim=1)

            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())

    # 전체 정확도
    correct = sum(p == l for p, l in zip(all_preds, all_labels))
    total = len(all_labels)
    accuracy = correct / total * 100

    print(f"전체 정확도: {accuracy:.2f}% ({correct}/{total})")

    # 동작별 정확도
    per_action = defaultdict(lambda: {"correct": 0, "total": 0})

    for pred, label in zip(all_preds, all_labels):
        action = label_to_action[label]
        per_action[action]["total"] += 1
        if pred == label:
            per_action[action]["correct"] += 1

    print(f"\n동작별 정확도:")
    for action in sorted(per_action.keys()):
        stats = per_action[action]
        acc = stats["correct"] / stats["total"] * 100
        bar = "█" * int(acc / 5)
        print(f"  {action:10s}: {stats['correct']:3d}/{stats['total']:3d} ({acc:5.1f}%) {bar}")

    # 오분류 분석
    print(f"\n{'='*70}")
    print("❌ 오분류 분석")
    print(f"{'='*70}\n")

    confusion = defaultdict(lambda: defaultdict(int))

    for pred, label in zip(all_preds, all_labels):
        if pred != label:
            true_action = label_to_action[label]
            pred_action = label_to_action[pred]
            confusion[true_action][pred_action] += 1

    if confusion:
        print("정답 → 예측 (오류 횟수):")
        for true_action in sorted(confusion.keys()):
            for pred_action, count in sorted(confusion[true_action].items(), key=lambda x: -x[1]):
                print(f"  {true_action:10s} → {pred_action:10s}: {count}회")
    else:
        print("🎉 오분류 없음! 완벽한 성능!")

    print(f"\n{'='*70}\n")


if __name__ == "__main__":
    main()
