"""
클래스 불균형 문제를 해결하는 학습 스크립트

문제:
- CLAP: ~100개
- 나머지: ~50개씩
- CLAP이 2배 많아서 모델이 CLAP으로만 예측

해결:
- 클래스 가중치를 자동 계산하여 균형 맞춤
- CLAP의 손실 가중치를 낮추고 나머지를 높임
"""

import sys
import subprocess
from pathlib import Path
from collections import Counter

# 데이터 분포 확인
def check_class_distribution():
    """클래스별 데이터 개수 확인"""
    import glob

    data_dir = Path("app/brandnewTrain/pose_sequences")
    if not data_dir.exists():
        print(f"❌ 데이터 폴더가 없습니다: {data_dir}")
        return None

    class_counts = Counter()

    # 모든 npz 파일 수집
    for person_dir in data_dir.iterdir():
        if not person_dir.is_dir():
            continue

        for action_dir in person_dir.iterdir():
            if not action_dir.is_dir():
                continue

            action = action_dir.name
            npz_files = list(action_dir.glob("*.npz"))
            class_counts[action] += len(npz_files)

    return class_counts


print("\n" + "=" * 80)
print("🔍 클래스 분포 확인")
print("=" * 80)

class_counts = check_class_distribution()
if class_counts:
    total = sum(class_counts.values())
    print(f"\n총 샘플 수: {total}개\n")

    for action in sorted(class_counts.keys()):
        count = class_counts[action]
        percentage = count / total * 100
        bar = "█" * int(percentage / 2)
        print(f"  {action:10s}: {count:4d}개 ({percentage:5.1f}%) {bar}")

    # 불균형 비율 계산
    max_count = max(class_counts.values())
    min_count = min(class_counts.values())
    imbalance_ratio = max_count / min_count

    print(f"\n⚠️  클래스 불균형 비율: {imbalance_ratio:.2f}배")

    if imbalance_ratio > 1.5:
        print("   → 클래스 가중치 적용 필요!")

    print("=" * 80 + "\n")

# train_gcn_cnn.py를 import해서 수정된 버전으로 학습
print("📚 학습 시작 (클래스 가중치 자동 적용)")
print("=" * 80 + "\n")

# 학습 실행
cmd = [
    sys.executable,
    "-c",
    """
import sys
sys.path.insert(0, '.')

# train_gcn_cnn 임포트
from app.brandnewTrain import train_gcn_cnn
import torch
import torch.nn as nn
from collections import Counter

# 원본 main 함수 백업
original_main = train_gcn_cnn.main

def main_with_class_weights():
    # 원본 parse_args 호출
    args = train_gcn_cnn.parse_args()
    train_gcn_cnn.set_seed(args.seed)

    from pathlib import Path
    data_dir = Path(args.data_dir)
    device = train_gcn_cnn.auto_device(args.device)

    if args.actions:
        selected_actions = [action.upper() for action in args.actions]
    else:
        selected_actions = train_gcn_cnn.SUPPORTED_ACTIONS

    action_to_label = {action: idx for idx, action in enumerate(sorted(set(selected_actions)))}
    label_to_action = {label: action for action, label in action_to_label.items()}

    # 샘플 수집
    samples = train_gcn_cnn.collect_samples(
        data_dir=data_dir,
        action_to_label=action_to_label,
        frames_per_sample=args.frames_per_sample,
        persons=args.persons,
        actions=selected_actions,
    )

    # 클래스별 샘플 수 계산
    class_counts = Counter()
    for sample in samples:
        class_counts[sample.label] += 1

    print("\\n" + "=" * 60)
    print("⚖️  클래스 가중치 계산")
    print("=" * 60)

    # 클래스 가중치 계산 (inverse frequency)
    total_samples = len(samples)
    num_classes = len(action_to_label)

    class_weights = torch.zeros(num_classes)
    for label, count in class_counts.items():
        # weight = total / (num_classes * count)
        class_weights[label] = total_samples / (num_classes * count)

    # 정규화 (선택적)
    class_weights = class_weights / class_weights.sum() * num_classes

    print("\\n클래스별 샘플 수 및 가중치:")
    for label in sorted(class_counts.keys()):
        action = label_to_action[label]
        count = class_counts[label]
        weight = class_weights[label].item()
        print(f"  {action:10s} (label={label}): {count:4d}개 → 가중치 {weight:.3f}")

    print("=" * 60 + "\\n")

    # 원본 코드 실행하되 criterion만 교체
    train_samples, val_samples = train_gcn_cnn.split_samples(samples, args.val_split, args.seed)

    print(f"▶ 학습 샘플: {len(train_samples)}개, 검증 샘플: {len(val_samples)}개")
    train_gcn_cnn.print_split_summary("TRAIN", train_samples)
    train_gcn_cnn.print_split_summary("VAL", val_samples)

    dataset_args = dict(frames_per_sample=args.frames_per_sample)
    train_dataset = train_gcn_cnn.PoseSequenceDataset(train_samples, **dataset_args)
    train_loader = torch.utils.data.DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.num_workers,
        pin_memory=True,
    )

    val_loader = None
    if val_samples:
        val_dataset = train_gcn_cnn.PoseSequenceDataset(val_samples, **dataset_args)
        val_loader = torch.utils.data.DataLoader(
            val_dataset,
            batch_size=args.batch_size,
            shuffle=False,
            num_workers=args.num_workers,
            pin_memory=True,
        )

    # 모델 생성
    input_dim = 2
    adjacency = train_gcn_cnn.build_adjacency(train_gcn_cnn.USED_LANDMARK_INDICES)
    model = train_gcn_cnn.GCNTemporalModel(
        input_dim=input_dim,
        num_classes=len(action_to_label),
        adjacency=adjacency,
        gcn_hidden_dims=args.gcn_hidden_dims,
        temporal_channels=args.temporal_channels,
        dropout=args.dropout,
    )
    model.to(device)

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    # 🔥 클래스 가중치 적용한 손실 함수
    criterion = nn.CrossEntropyLoss(weight=class_weights.to(device))

    # 학습 루프 (원본과 동일)
    import math
    best_val_acc = -math.inf
    best_epoch = -1
    checkpoint_path = Path(args.save_dir) / args.save_name

    history = []
    for epoch in range(1, args.epochs + 1):
        train_result = train_gcn_cnn.train_one_epoch(
            model,
            train_loader,
            criterion,
            optimizer,
            device,
            grad_clip=args.grad_clip,
        )

        scheduler.step()

        if val_loader:
            val_result = train_gcn_cnn.evaluate(model, val_loader, criterion, device, label_to_action)
            val_loss = val_result.loss
            val_acc = val_result.accuracy

            print(f"[Epoch {epoch:3d}/{args.epochs}] Train Loss={train_result.loss:.4f} Acc={train_result.accuracy:.2%} | "
                  f"Val Loss={val_loss:.4f} Acc={val_acc:.2%}")

            if val_result.per_action:
                for action, (correct, total) in sorted(val_result.per_action.items()):
                    acc = correct / total if total > 0 else 0
                    print(f"  {action:10s}: {correct:3d}/{total:3d} ({acc:.1%})")

            if val_acc > best_val_acc:
                best_val_acc = val_acc
                best_epoch = epoch

                if not args.no_checkpoint:
                    train_gcn_cnn.save_checkpoint(
                        checkpoint_path,
                        model,
                        optimizer,
                        epoch,
                        best_val_acc,
                        args,
                        action_to_label,
                    )
                    print(f"  ✅ 체크포인트 저장: {checkpoint_path}")
        else:
            print(f"[Epoch {epoch:3d}/{args.epochs}] Train Loss={train_result.loss:.4f} Acc={train_result.accuracy:.2%}")

    print(f"\\n🎉 학습 완료! 최고 검증 정확도: {best_val_acc:.2%} (Epoch {best_epoch})")
    if not args.no_checkpoint:
        print(f"✅ 모델 저장 위치: {checkpoint_path}")

# 수정된 main 실행
main_with_class_weights()
""",
    "--",
    "--data_dir", "./app/brandnewTrain/pose_sequences",
    "--epochs", "150",
    "--save_name", "brandnew_balanced_v1.pt",
    "--batch_size", "32",
]

print(f"실행 명령: python [class_weight_training_logic]\n")
result = subprocess.run(cmd)

if result.returncode == 0:
    print("\n" + "=" * 80)
    print("✅ 학습 완료!")
    print("=" * 80)
    print("모델 저장: app/brandnewTrain/checkpoints/brandnew_balanced_v1.pt")
    print("\n다음 단계:")
    print("  1. 모델 교체:")
    print("     mv app/brandnewTrain/checkpoints/brandnew_model_v1.pt app/brandnewTrain/checkpoints/brandnew_model_v1_old.pt")
    print("     cp app/brandnewTrain/checkpoints/brandnew_balanced_v1.pt app/brandnewTrain/checkpoints/brandnew_model_v1.pt")
    print("  2. 테스트:")
    print("     python test_brandnew_server.py --samples 3")
    print("=" * 80)
else:
    print("\n❌ 학습 실패!")
    sys.exit(1)
