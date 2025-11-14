"""
Motion Recognition Model Training Script

데이터 준비:
1. data/ 폴더에 동작별로 폴더 생성 (예: CLAP, ELBOW, WAVE, JUMP)
2. 각 폴더에 해당 동작의 이미지 50-100장 넣기
3. python train.py 실행

사용법:
    python train.py --data_dir ./data --epochs 50 --batch_size 16
"""

import argparse
import logging
from pathlib import Path
from typing import Dict, List, Tuple
import sys

import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import numpy as np
from tqdm import tqdm

# motion-server 모듈 import
sys.path.insert(0, str(Path(__file__).parent))
from app.ml.models import PoseGCNTemporalModel
from app.ml.constants import BODY_LANDMARK_INDICES
from app.ml.inference import extract_landmarks_from_image

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class MotionDataset(Dataset):
    """동작 인식 데이터셋"""

    def __init__(self, image_paths: List[Path], labels: List[int], sequence_length: int = 32):
        self.image_paths = image_paths
        self.labels = labels
        self.sequence_length = sequence_length

    def __len__(self):
        return len(self.image_paths)

    def __getitem__(self, idx):
        image_path = self.image_paths[idx]
        label = self.labels[idx]

        # MediaPipe로 랜드마크 추출
        try:
            landmarks = extract_landmarks_from_image(image_path)
            # 신체 주요 관절만 선택
            selected = landmarks[BODY_LANDMARK_INDICES, :]  # (24, 2)

            # 시퀀스 생성 (같은 포즈를 반복)
            sequence = np.tile(selected[None, :, :], (self.sequence_length, 1, 1))  # (32, 24, 2)
            sequence = torch.from_numpy(sequence).float()

            return sequence, label
        except Exception as e:
            logger.warning(f"Failed to process {image_path}: {e}")
            # 실패 시 더미 데이터 반환
            dummy_sequence = torch.zeros((self.sequence_length, 24, 2))
            return dummy_sequence, label


def load_dataset(data_dir: Path) -> Tuple[Dict[str, int], List[Path], List[int]]:
    """
    데이터 폴더에서 이미지와 레이블 로드

    Returns:
        label_to_index: 라벨명 -> 인덱스 매핑
        image_paths: 이미지 경로 리스트
        labels: 레이블 인덱스 리스트
    """
    data_dir = Path(data_dir)
    if not data_dir.exists():
        raise ValueError(f"Data directory not found: {data_dir}")

    # 각 폴더명을 클래스로 간주
    class_folders = [d for d in data_dir.iterdir() if d.is_dir()]
    if not class_folders:
        raise ValueError(f"No class folders found in {data_dir}")

    label_to_index = {folder.name: idx for idx, folder in enumerate(sorted(class_folders))}
    logger.info(f"Found {len(label_to_index)} classes: {list(label_to_index.keys())}")

    image_paths = []
    labels = []

    for folder in class_folders:
        class_name = folder.name
        class_idx = label_to_index[class_name]

        # 이미지 파일 찾기 (.jpg, .png, .jpeg)
        images = list(folder.glob("*.jpg")) + list(folder.glob("*.png")) + list(folder.glob("*.jpeg"))
        logger.info(f"Class '{class_name}': {len(images)} images")

        for img_path in images:
            image_paths.append(img_path)
            labels.append(class_idx)

    logger.info(f"Total: {len(image_paths)} images")
    return label_to_index, image_paths, labels


def train_one_epoch(model, dataloader, criterion, optimizer, device):
    """1 에폭 학습"""
    model.train()
    total_loss = 0.0
    correct = 0
    total = 0

    for sequences, labels in tqdm(dataloader, desc="Training"):
        # (batch, seq_len, nodes, coords) -> (batch, seq_len, coords, nodes)
        sequences = sequences.permute(0, 1, 3, 2).to(device)
        labels = labels.to(device)

        optimizer.zero_grad()
        outputs = model(sequences)
        loss = criterion(outputs, labels)
        loss.backward()
        optimizer.step()

        total_loss += loss.item()
        _, predicted = outputs.max(1)
        total += labels.size(0)
        correct += predicted.eq(labels).sum().item()

    avg_loss = total_loss / len(dataloader)
    accuracy = 100.0 * correct / total
    return avg_loss, accuracy


def validate(model, dataloader, criterion, device):
    """검증"""
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0

    with torch.no_grad():
        for sequences, labels in dataloader:
            sequences = sequences.permute(0, 1, 3, 2).to(device)
            labels = labels.to(device)

            outputs = model(sequences)
            loss = criterion(outputs, labels)

            total_loss += loss.item()
            _, predicted = outputs.max(1)
            total += labels.size(0)
            correct += predicted.eq(labels).sum().item()

    avg_loss = total_loss / len(dataloader)
    accuracy = 100.0 * correct / total
    return avg_loss, accuracy


def main():
    parser = argparse.ArgumentParser(description="Train motion recognition model")
    parser.add_argument("--data_dir", type=str, default="./data", help="데이터 디렉토리 경로")
    parser.add_argument("--output_dir", type=str, default="./app/trained_model", help="모델 저장 경로")
    parser.add_argument("--epochs", type=int, default=50, help="학습 에폭 수")
    parser.add_argument("--batch_size", type=int, default=16, help="배치 크기")
    parser.add_argument("--lr", type=float, default=0.001, help="학습률")
    parser.add_argument("--val_split", type=float, default=0.2, help="검증 데이터 비율")
    parser.add_argument("--device", type=str, default="cuda", help="학습 디바이스 (cuda/cpu)")
    args = parser.parse_args()

    # 디바이스 설정
    device = torch.device(args.device if torch.cuda.is_available() and args.device == "cuda" else "cpu")
    logger.info(f"Using device: {device}")

    # 데이터 로드
    label_to_index, image_paths, labels = load_dataset(args.data_dir)
    num_classes = len(label_to_index)

    # Train/Val 분할
    num_samples = len(image_paths)
    indices = np.random.permutation(num_samples)
    val_size = int(num_samples * args.val_split)

    train_indices = indices[val_size:]
    val_indices = indices[:val_size]

    train_paths = [image_paths[i] for i in train_indices]
    train_labels = [labels[i] for i in train_indices]
    val_paths = [image_paths[i] for i in val_indices]
    val_labels = [labels[i] for i in val_indices]

    logger.info(f"Train: {len(train_paths)} samples, Val: {len(val_paths)} samples")

    # 데이터셋 생성
    train_dataset = MotionDataset(train_paths, train_labels)
    val_dataset = MotionDataset(val_paths, val_labels)

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True, num_workers=0)
    val_loader = DataLoader(val_dataset, batch_size=args.batch_size, shuffle=False, num_workers=0)

    # 모델 생성
    model = PoseGCNTemporalModel(num_classes=num_classes, in_channels=2)
    model = model.to(device)

    # 손실 함수 및 옵티마이저
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=args.lr)

    # 학습
    best_val_acc = 0.0

    for epoch in range(1, args.epochs + 1):
        logger.info(f"\n=== Epoch {epoch}/{args.epochs} ===")

        train_loss, train_acc = train_one_epoch(model, train_loader, criterion, optimizer, device)
        val_loss, val_acc = validate(model, val_loader, criterion, device)

        logger.info(f"Train Loss: {train_loss:.4f}, Train Acc: {train_acc:.2f}%")
        logger.info(f"Val Loss: {val_loss:.4f}, Val Acc: {val_acc:.2f}%")

        # Best 모델 저장
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            output_path = Path(args.output_dir) / "best_model.pt"
            output_path.parent.mkdir(parents=True, exist_ok=True)

            checkpoint = {
                "model_state_dict": model.state_dict(),
                "label_to_index": label_to_index,
                "epoch": epoch,
                "val_acc": val_acc,
            }
            torch.save(checkpoint, output_path)
            logger.info(f"✓ Best model saved: {output_path} (Val Acc: {val_acc:.2f}%)")

    logger.info(f"\n=== Training Complete ===")
    logger.info(f"Best Val Accuracy: {best_val_acc:.2f}%")
    logger.info(f"Model saved to: {args.output_dir}/best_model.pt")


if __name__ == "__main__":
    main()
