"""
게임 플레이 데이터를 활용한 Fine-tuning 스크립트

게임 중 수집된 실제 플레이 데이터(game_data/)를 사용하여
기존 학습된 모델을 추가로 fine-tuning합니다.

실행 방법:
    1. 게임 데이터를 pose sequence로 변환
    2. 기존 모델을 로드하여 추가 학습

사용 예시:
    python finetune_with_game_data.py --checkpoint ./checkpoints/brandnew_model_v1.pt --epochs 30
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import cv2
import mediapipe as mp
import numpy as np
import torch
import torch.nn as nn
from PIL import Image, ImageOps

# train_gcn_cnn 모듈 import
sys.path.insert(0, str(Path(__file__).parent.parent))
from brandnewTrain import train_gcn_cnn


# 게임 데이터 파일명 패턴
# 예: 20251119_102637_258879_손 박수_1_frame00.jpg
GAME_DATA_PATTERN = re.compile(
    r"(?P<timestamp>\d{8}_\d{6}_\d+)_(?P<action_kr>[^_]+)_(?P<seq>\d+)_frame(?P<frame>\d+)\.(?P<ext>jpg|jpeg|png)$",
    re.IGNORECASE,
)

# 한글 동작명 -> 영어 매핑
ACTION_KR_TO_EN = {
    "손 박수": "CLAP",
    "팔 치기": "ELBOW",  # 또는 다른 동작
    "비상구": "EXIT",
    "손뻗기": "STRETCH",
    "팔뻗기": "STRETCH",
    "기우뚱": "TILT",
    "겨드랑이": "UNDERARM",
    "가만히": "STAY",
}


@dataclass
class GameSequence:
    timestamp: str
    action_kr: str
    action_en: str
    seq_id: int
    frames: List[Tuple[int, Path]]  # (frame_id, path)


def collect_game_sequences(game_data_dir: Path) -> List[GameSequence]:
    """
    game_data 디렉토리에서 프레임 이미지를 수집하고 시퀀스별로 그룹화
    """
    sequences_dict: Dict[str, GameSequence] = {}

    for image_path in sorted(game_data_dir.glob("*.jpg")):
        match = GAME_DATA_PATTERN.match(image_path.name)
        if not match:
            continue

        timestamp = match.group("timestamp")
        action_kr = match.group("action_kr")
        seq_id = int(match.group("seq"))
        frame_id = int(match.group("frame"))

        # 한글 동작명을 영어로 변환
        action_en = ACTION_KR_TO_EN.get(action_kr)
        if not action_en:
            print(f"⚠️  알 수 없는 동작명: {action_kr} (파일: {image_path.name})")
            continue

        # 시퀀스 키 생성
        seq_key = f"{timestamp}_{action_kr}_{seq_id}"

        if seq_key not in sequences_dict:
            sequences_dict[seq_key] = GameSequence(
                timestamp=timestamp,
                action_kr=action_kr,
                action_en=action_en,
                seq_id=seq_id,
                frames=[],
            )

        sequences_dict[seq_key].frames.append((frame_id, image_path))

    # 프레임을 프레임 ID 순으로 정렬
    for seq in sequences_dict.values():
        seq.frames.sort(key=lambda x: x[0])

    return list(sequences_dict.values())


def extract_landmarks_from_image(
    pose: mp.solutions.pose.Pose,
    image_path: Path,
) -> Optional[np.ndarray]:
    """이미지에서 MediaPipe 포즈 랜드마크 추출"""
    with Image.open(image_path) as pil_img:
        # EXIF 회전 보정
        pil_img = ImageOps.exif_transpose(pil_img)
        if pil_img is None:
            pil_img = Image.open(image_path)

        image_rgb = np.array(pil_img.convert("RGB"))
        image = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2BGR)

    if image is None:
        return None

    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    results = pose.process(image_rgb)

    if not results.pose_landmarks:
        return None

    landmarks = np.array(
        [[lm.x, lm.y] for lm in results.pose_landmarks.landmark],
        dtype=np.float32,
    )
    return landmarks


def convert_game_data_to_sequences(
    game_data_dir: Path,
    output_dir: Path,
    frames_per_sample: int = 8,
    model_complexity: int = 1,
    min_detection_confidence: float = 0.5,
) -> List[Path]:
    """
    게임 데이터를 pose sequence (.npz)로 변환

    Returns:
        저장된 .npz 파일 경로 리스트
    """
    print(f"\n{'='*70}")
    print(f"🎮 게임 데이터를 Pose Sequence로 변환 중...")
    print(f"{'='*70}")
    print(f"입력: {game_data_dir}")
    print(f"출력: {output_dir}")
    print(f"{'='*70}\n")

    sequences = collect_game_sequences(game_data_dir)

    if not sequences:
        print("⚠️  게임 데이터를 찾을 수 없습니다.")
        return []

    print(f"총 {len(sequences)}개 시퀀스 발견\n")

    # 동작별 통계
    action_counts = Counter(seq.action_en for seq in sequences)
    print("동작별 시퀀스 수:")
    for action, count in sorted(action_counts.items()):
        print(f"  {action:10s}: {count:3d}개")
    print()

    saved_paths: List[Path] = []
    mp_pose = mp.solutions.pose

    with mp_pose.Pose(
        static_image_mode=True,
        model_complexity=model_complexity,
        enable_segmentation=False,
        min_detection_confidence=min_detection_confidence,
    ) as pose:
        for seq in sequences:
            if len(seq.frames) != frames_per_sample:
                print(
                    f"⚠️  프레임 수 불일치: {seq.action_en} (seq {seq.seq_id}) "
                    f"- {len(seq.frames)}개 (기대: {frames_per_sample})"
                )
                continue

            # 각 프레임에서 랜드마크 추출
            frame_landmarks: List[np.ndarray] = []
            skip_sequence = False

            for frame_idx, image_path in seq.frames:
                landmarks = extract_landmarks_from_image(pose, image_path)
                if landmarks is None:
                    print(f"⚠️  포즈 추출 실패: {image_path.name}")
                    skip_sequence = True
                    break
                frame_landmarks.append(landmarks)

            if skip_sequence:
                continue

            # 시퀀스 저장
            landmarks_array = np.stack(frame_landmarks, axis=0)

            # 출력 디렉토리 생성 (GAME_DATA/동작/)
            action_output_dir = output_dir / "GAME_DATA" / seq.action_en
            action_output_dir.mkdir(parents=True, exist_ok=True)

            # 파일명 생성
            filename = f"{seq.action_en.lower()}_{seq.timestamp}_seq{seq.seq_id:03d}.npz"
            output_path = action_output_dir / filename

            # 메타데이터
            metadata = {
                "person": "GAME_DATA",
                "action": seq.action_en,
                "sequence_id": seq.seq_id,
                "timestamp": seq.timestamp,
                "frames_per_sample": frames_per_sample,
                "landmark_count": landmarks_array.shape[1],
            }

            np.savez_compressed(
                output_path,
                landmarks=landmarks_array,
                metadata=json.dumps(metadata),
            )

            saved_paths.append(output_path)

            if len(saved_paths) % 10 == 0:
                print(f"  ✓ {len(saved_paths)}개 시퀀스 변환 완료...")

    print(f"\n✅ 총 {len(saved_paths)}개 시퀀스 변환 완료")
    print(f"{'='*70}\n")

    return saved_paths


def load_checkpoint_for_finetuning(
    checkpoint_path: Path,
    device: torch.device,
) -> Tuple[nn.Module, Dict, argparse.Namespace]:
    """
    체크포인트 로드 및 fine-tuning 준비

    Returns:
        (model, action_to_label, original_args)
    """
    print(f"📂 체크포인트 로딩: {checkpoint_path}")

    checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)

    # 저장된 args 복원
    original_args = checkpoint["args"]

    # args가 dict인 경우 argparse.Namespace로 변환
    if isinstance(original_args, dict):
        from argparse import Namespace
        original_args = Namespace(**original_args)

    # action_to_label 또는 class_mapping 키 확인
    if "action_to_label" in checkpoint:
        action_to_label = checkpoint["action_to_label"]
    elif "class_mapping" in checkpoint:
        action_to_label = checkpoint["class_mapping"]
    else:
        raise KeyError(f"체크포인트에 'action_to_label' 또는 'class_mapping' 키가 없습니다. 사용 가능한 키: {list(checkpoint.keys())}")

    # 모델 재생성
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

    # 학습된 가중치 로드
    model.load_state_dict(checkpoint["model_state_dict"])
    model.to(device)

    print(f"✅ 모델 로딩 완료 (클래스 수: {len(action_to_label)})")
    print(f"   동작 레이블: {sorted(action_to_label.keys())}")

    return model, action_to_label, original_args


def finetune_with_game_data(
    checkpoint_path: Path,
    game_pose_dir: Path,
    original_pose_dir: Optional[Path],
    epochs: int,
    learning_rate: float,
    batch_size: int,
    save_dir: Path,
    save_name: str,
    val_split: float,
    seed: int,
    device: str,
    use_class_weights: bool,
) -> None:
    """
    게임 데이터로 모델 Fine-tuning
    """
    train_gcn_cnn.set_seed(seed)
    device = train_gcn_cnn.auto_device(device)

    # 1. 체크포인트 로드
    model, action_to_label, original_args = load_checkpoint_for_finetuning(
        checkpoint_path, device
    )
    label_to_action = {label: action for action, label in action_to_label.items()}

    # 2. 샘플 수집
    print(f"\n{'='*70}")
    print("📊 학습 데이터 수집")
    print(f"{'='*70}")

    samples = []

    # 게임 데이터 수집
    game_samples = train_gcn_cnn.collect_samples(
        data_dir=game_pose_dir,
        action_to_label=action_to_label,
        frames_per_sample=original_args.frames_per_sample,
        persons=None,
        actions=list(action_to_label.keys()),
    )
    print(f"🎮 게임 데이터: {len(game_samples)}개")

    samples.extend(game_samples)

    # 기존 학습 데이터 추가 (선택적)
    if original_pose_dir and original_pose_dir.exists():
        original_samples = train_gcn_cnn.collect_samples(
            data_dir=original_pose_dir,
            action_to_label=action_to_label,
            frames_per_sample=original_args.frames_per_sample,
            persons=None,
            actions=list(action_to_label.keys()),
        )
        print(f"📚 기존 데이터: {len(original_samples)}개")
        samples.extend(original_samples)

    print(f"📦 총 샘플: {len(samples)}개")

    # 클래스 분포 확인
    class_counts = Counter(sample.label for sample in samples)
    print("\n클래스별 샘플 수:")
    for label in sorted(class_counts.keys()):
        action = label_to_action[label]
        count = class_counts[label]
        print(f"  {action:10s}: {count:4d}개")

    # 3. Train/Val split
    train_samples, val_samples = train_gcn_cnn.split_samples(samples, val_split, seed)

    print(f"\n▶ 학습 샘플: {len(train_samples)}개, 검증 샘플: {len(val_samples)}개")
    train_gcn_cnn.print_split_summary("TRAIN", train_samples)
    if val_samples:
        train_gcn_cnn.print_split_summary("VAL", val_samples)

    # 4. DataLoader 생성
    dataset_args = dict(frames_per_sample=original_args.frames_per_sample)
    train_dataset = train_gcn_cnn.PoseSequenceDataset(train_samples, **dataset_args)
    train_loader = torch.utils.data.DataLoader(
        train_dataset,
        batch_size=batch_size,
        shuffle=True,
        num_workers=0,
        pin_memory=True,
    )

    val_loader = None
    if val_samples:
        val_dataset = train_gcn_cnn.PoseSequenceDataset(val_samples, **dataset_args)
        val_loader = torch.utils.data.DataLoader(
            val_dataset,
            batch_size=batch_size,
            shuffle=False,
            num_workers=0,
            pin_memory=True,
        )

    # 5. Optimizer & Loss
    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=learning_rate,
        weight_decay=original_args.weight_decay,
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    # 클래스 가중치 적용 (선택적)
    if use_class_weights:
        total_samples = len(samples)
        num_classes = len(action_to_label)
        class_weights = torch.zeros(num_classes)

        for label, count in class_counts.items():
            class_weights[label] = total_samples / (num_classes * count)

        class_weights = class_weights / class_weights.sum() * num_classes
        criterion = nn.CrossEntropyLoss(weight=class_weights.to(device))

        print("\n⚖️  클래스 가중치 적용:")
        for label in sorted(class_counts.keys()):
            action = label_to_action[label]
            weight = class_weights[label].item()
            print(f"  {action:10s}: {weight:.3f}")
    else:
        criterion = nn.CrossEntropyLoss()

    # 6. Fine-tuning 시작
    print(f"\n{'='*70}")
    print("🔥 Fine-tuning 시작")
    print(f"{'='*70}\n")

    import math
    best_val_acc = -math.inf
    best_epoch = -1
    checkpoint_path_out = save_dir / save_name

    for epoch in range(1, epochs + 1):
        train_result = train_gcn_cnn.train_one_epoch(
            model,
            train_loader,
            criterion,
            optimizer,
            device,
            grad_clip=original_args.grad_clip,
        )

        scheduler.step()

        if val_loader:
            val_result = train_gcn_cnn.evaluate(
                model, val_loader, criterion, device, label_to_action
            )
            val_loss = val_result.loss
            val_acc = val_result.accuracy

            print(
                f"[Epoch {epoch:3d}/{epochs}] "
                f"Train Loss={train_result.loss:.4f} Acc={train_result.accuracy:.2%} | "
                f"Val Loss={val_loss:.4f} Acc={val_acc:.2%}"
            )

            if val_result.per_action:
                for action, (correct, total) in sorted(val_result.per_action.items()):
                    acc = correct / total if total > 0 else 0
                    print(f"  {action:10s}: {correct:3d}/{total:3d} ({acc:.1%})")

            if val_acc > best_val_acc:
                best_val_acc = val_acc
                best_epoch = epoch

                # 체크포인트 저장
                train_gcn_cnn.save_checkpoint(
                    checkpoint_path_out,
                    model,
                    optimizer,
                    epoch,
                    best_val_acc,
                    original_args,
                    action_to_label,
                )
                print(f"  ✅ 체크포인트 저장: {checkpoint_path_out}")
        else:
            print(
                f"[Epoch {epoch:3d}/{epochs}] "
                f"Train Loss={train_result.loss:.4f} Acc={train_result.accuracy:.2%}"
            )

    print(f"\n{'='*70}")
    print(f"🎉 Fine-tuning 완료!")
    print(f"   최고 검증 정확도: {best_val_acc:.2%} (Epoch {best_epoch})")
    print(f"   모델 저장 위치: {checkpoint_path_out}")
    print(f"{'='*70}\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="게임 데이터로 모델 Fine-tuning")

    parser.add_argument(
        "--checkpoint",
        type=str,
        required=True,
        help="기존 학습된 모델 체크포인트 경로 (.pt)",
    )
    parser.add_argument(
        "--game_data_dir",
        type=str,
        default="./app/brandnewTrain/game_data",
        help="게임 데이터 디렉토리 (기본: ./app/brandnewTrain/game_data)",
    )
    parser.add_argument(
        "--original_pose_dir",
        type=str,
        default=None,
        help="기존 pose_sequences 디렉토리 (추가 학습 시)",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=30,
        help="Fine-tuning epoch 수 (기본: 30)",
    )
    parser.add_argument(
        "--learning_rate",
        type=float,
        default=1e-4,
        help="Learning rate (기본: 1e-4, 원래 학습보다 낮게 설정)",
    )
    parser.add_argument(
        "--batch_size",
        type=int,
        default=32,
        help="Batch size (기본: 32)",
    )
    parser.add_argument(
        "--save_dir",
        type=str,
        default="./app/brandnewTrain/checkpoints",
        help="모델 저장 디렉토리",
    )
    parser.add_argument(
        "--save_name",
        type=str,
        default="brandnew_finetuned_v1.pt",
        help="저장할 모델 파일명",
    )
    parser.add_argument(
        "--val_split",
        type=float,
        default=0.2,
        help="Validation split 비율 (기본: 0.2)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed",
    )
    parser.add_argument(
        "--device",
        type=str,
        default="auto",
        help="학습 디바이스 (auto, cpu, cuda)",
    )
    parser.add_argument(
        "--use_class_weights",
        action="store_true",
        help="클래스 가중치 적용 (불균형 데이터 처리)",
    )
    parser.add_argument(
        "--skip_conversion",
        action="store_true",
        help="게임 데이터 변환 스킵 (이미 변환된 경우)",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    checkpoint_path = Path(args.checkpoint)
    if not checkpoint_path.exists():
        print(f"❌ 체크포인트를 찾을 수 없습니다: {checkpoint_path}")
        sys.exit(1)

    game_data_dir = Path(args.game_data_dir)
    if not game_data_dir.exists():
        print(f"❌ 게임 데이터 디렉토리를 찾을 수 없습니다: {game_data_dir}")
        sys.exit(1)

    # 게임 데이터 변환 (pose sequence로)
    game_pose_dir = Path("./app/brandnewTrain/game_pose_sequences")

    if not args.skip_conversion:
        convert_game_data_to_sequences(
            game_data_dir=game_data_dir,
            output_dir=game_pose_dir,
            frames_per_sample=8,
            model_complexity=1,
            min_detection_confidence=0.5,
        )
    else:
        print("⏭️  게임 데이터 변환 스킵")

    # 기존 pose_sequences 경로
    original_pose_dir = None
    if args.original_pose_dir:
        original_pose_dir = Path(args.original_pose_dir)

    # Fine-tuning 실행
    save_dir = Path(args.save_dir)
    save_dir.mkdir(parents=True, exist_ok=True)

    finetune_with_game_data(
        checkpoint_path=checkpoint_path,
        game_pose_dir=game_pose_dir,
        original_pose_dir=original_pose_dir,
        epochs=args.epochs,
        learning_rate=args.learning_rate,
        batch_size=args.batch_size,
        save_dir=save_dir,
        save_name=args.save_name,
        val_split=args.val_split,
        seed=args.seed,
        device=args.device,
        use_class_weights=args.use_class_weights,
    )


if __name__ == "__main__":
    main()
