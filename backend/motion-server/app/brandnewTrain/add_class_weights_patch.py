"""
train_gcn_cnn.py에 클래스 가중치를 추가하는 패치

Line 526의 criterion = nn.CrossEntropyLoss()를
클래스 가중치가 적용된 버전으로 교체합니다.
"""

from pathlib import Path

TRAIN_FILE = Path("app/brandnewTrain/train_gcn_cnn.py")

print("=" * 80)
print("🔧 train_gcn_cnn.py 패치 적용")
print("=" * 80)

# 파일 읽기
with open(TRAIN_FILE, "r", encoding="utf-8") as f:
    content = f.read()

# 패치 적용할 코드 찾기
old_code = """    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)
    criterion = nn.CrossEntropyLoss()"""

new_code = """    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    # 클래스 가중치 계산 (불균형 해결)
    class_sample_counts = Counter()
    for sample in train_samples:
        class_sample_counts[sample.label] += 1

    print("\\n" + "=" * 60)
    print("⚖️  클래스 가중치 계산 (불균형 해결)")
    print("=" * 60)

    num_classes = len(action_to_label)
    total_samples = len(train_samples)
    class_weights = torch.zeros(num_classes)

    for label, count in class_sample_counts.items():
        # Inverse frequency weighting
        class_weights[label] = total_samples / (num_classes * count)

    # 정규화
    class_weights = class_weights / class_weights.sum() * num_classes

    print("\\n클래스별 샘플 수 및 가중치:")
    for label in sorted(class_sample_counts.keys()):
        action = label_to_action[label]
        count = class_sample_counts[label]
        weight = class_weights[label].item()
        print(f"  {action:10s}: {count:4d}개 → 가중치 {weight:.3f}")

    print("=" * 60 + "\\n")

    # 가중치 적용
    criterion = nn.CrossEntropyLoss(weight=class_weights.to(device))"""

if old_code in content:
    # 패치 적용
    new_content = content.replace(old_code, new_code)

    # Counter import 추가 확인
    if "from collections import Counter" not in new_content:
        # import 섹션에 추가
        import_line = "from collections import Counter, defaultdict"
        if "from collections import" in new_content:
            new_content = new_content.replace(
                "from collections import",
                f"from collections import Counter,"
            )
        else:
            new_content = new_content.replace(
                "import argparse",
                f"import argparse\nfrom collections import Counter"
            )

    # 백업 저장
    backup_file = TRAIN_FILE.with_suffix(".py.backup")
    with open(backup_file, "w", encoding="utf-8") as f:
        f.write(content)

    # 패치된 파일 저장
    with open(TRAIN_FILE, "w", encoding="utf-8") as f:
        f.write(new_content)

    print("✅ 패치 적용 완료!")
    print(f"   백업: {backup_file}")
    print(f"   수정: {TRAIN_FILE}")
    print("\n다음 명령으로 학습:")
    print("   python app/brandnewTrain/train_gcn_cnn.py \\")
    print("     --data_dir ./app/brandnewTrain/pose_sequences \\")
    print("     --epochs 150 \\")
    print("     --save_name brandnew_balanced_v1.pt")
else:
    print("❌ 패치할 코드를 찾지 못했습니다.")
    print("   train_gcn_cnn.py가 이미 수정되었거나 버전이 다를 수 있습니다.")

print("=" * 80)
