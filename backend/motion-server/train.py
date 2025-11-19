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
from app.ml import extract_landmarks_from_image

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class MotionDataset(Dataset):
    """동작 인식 데이터셋 (inference.py의 _prepare_sequence_from_landmark_list와 동일한 방식)"""

    def __init__(self, image_groups: List[List[Path]], labels: List[int], sequence_length: int = 32):
        """
        Args:
            image_groups: 각 샘플당 여러 이미지 경로 리스트의 리스트
                         예: [[img1_1.jpg, img1_2.jpg, ...], [img2_1.jpg, img2_2.jpg, ...]]
            labels: 각 그룹의 레이블
            sequence_length: 시퀀스 길이 (기본 32)
        """
        self.image_groups = image_groups
        self.labels = labels
        self.sequence_length = sequence_length

    def __len__(self):
        return len(self.image_groups)

    def __getitem__(self, idx):
        image_paths = self.image_groups[idx]
        label = self.labels[idx]

        try:
            # 각 이미지에서 랜드마크 추출
            landmarks_list = []
            for image_path in image_paths:
                landmarks = extract_landmarks_from_image(image_path)
                selected = landmarks[BODY_LANDMARK_INDICES, :]  # (24, 2)
                landmarks_list.append(selected)

            # inference.py와 동일한 방식으로 시퀀스 생성
            sequence = self._prepare_sequence_from_landmark_list(landmarks_list)
            sequence = torch.from_numpy(sequence).float()

            return sequence, label
        except Exception as e:
            logger.warning(f"Failed to process image group {idx}: {e}")
            dummy_sequence = torch.zeros((self.sequence_length, 24, 2))
            return dummy_sequence, label

    def _prepare_sequence_from_landmark_list(self, landmark_list: List[np.ndarray]) -> np.ndarray:
        """
        inference.py의 _prepare_sequence_from_landmark_list와 동일한 로직
        여러 프레임을 32개 시퀀스로 보간
        """
        if not landmark_list:
            raise ValueError("랜드마크 리스트가 비어있습니다.")

        # 1개만 있으면 32번 복사 (기존 방식)
        if len(landmark_list) == 1:
            selected = landmark_list[0]
            return np.tile(selected[None, :, :], (self.sequence_length, 1, 1)).astype(np.float32)

        # 여러 개 있으면 보간 (현재 추론 방식과 동일)
        landmark_array = np.stack(landmark_list, axis=0)  # (N, 24, 2)
        N = len(landmark_list)

        if N >= self.sequence_length:
            # 프레임이 충분하면 균등 샘플링
            indices = np.linspace(0, N - 1, self.sequence_length, dtype=int)
            return landmark_array[indices].astype(np.float32)
        else:
            # 프레임이 부족하면 선형 보간
            indices = np.linspace(0, N - 1, self.sequence_length)
            int_indices = indices.astype(int)
            frac_indices = indices - int_indices

            # 각 관절과 좌표에 대해 선형 보간
            result = np.zeros((self.sequence_length, 24, 2), dtype=np.float32)
            for i in range(self.sequence_length):
                idx = int_indices[i]
                frac = frac_indices[i]

                if idx + 1 < N:
                    # 선형 보간: (1-frac) * current + frac * next
                    result[i] = (1 - frac) * landmark_array[idx] + frac * landmark_array[idx + 1]
                else:
                    # 마지막 프레임
                    result[i] = landmark_array[idx]

            return result


def load_dataset(data_dir: Path, frames_per_sample: int = 1) -> Tuple[Dict[str, int], List[List[Path]], List[int]]:
    """
    데이터 폴더에서 이미지 그룹과 레이블 로드

    Args:
        data_dir: 데이터 디렉토리 경로
        frames_per_sample: 샘플당 프레임 수
            - 1: 단일 이미지 (기존 방식, 각 이미지가 1개 샘플)
            - N: 연속된 N개 이미지를 1개 샘플로 그룹화

    Returns:
        label_to_index: 라벨명 -> 인덱스 매핑
        image_groups: 이미지 경로 그룹 리스트 [[img1, img2, ...], [...], ...]
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

    image_groups = []
    labels = []

    for folder in class_folders:
        class_name = folder.name
        class_idx = label_to_index[class_name]

        # 이미지 파일 찾기 (.jpg, .png, .jpeg)
        images = sorted(list(folder.glob("*.jpg")) + list(folder.glob("*.png")) + list(folder.glob("*.jpeg")))

        if frames_per_sample == 1:
            # 단일 이미지 모드: 각 이미지가 1개 샘플
            logger.info(f"Class '{class_name}': {len(images)} samples (1 frame each)")
            for img_path in images:
                image_groups.append([img_path])  # 리스트로 감싸기
                labels.append(class_idx)
        else:
            # 다중 이미지 모드: N개씩 그룹화
            num_groups = len(images) // frames_per_sample
            logger.info(f"Class '{class_name}': {len(images)} images → {num_groups} samples ({frames_per_sample} frames each)")

            for i in range(num_groups):
                start_idx = i * frames_per_sample
                end_idx = start_idx + frames_per_sample
                group = images[start_idx:end_idx]
                image_groups.append(group)
                labels.append(class_idx)

    logger.info(f"Total: {len(image_groups)} samples")
    return label_to_index, image_groups, labels


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
    parser.add_argument("--frames_per_sample", type=int, default=8, help="샘플당 프레임 수 (기본: 8, Spring과 동일)")
    args = parser.parse_args()

    # 디바이스 설정
    device = torch.device(args.device if torch.cuda.is_available() and args.device == "cuda" else "cpu")
    logger.info(f"Using device: {device}")

    # 데이터 로드
    label_to_index, image_groups, labels = load_dataset(args.data_dir, args.frames_per_sample)
    num_classes = len(label_to_index)

    # Train/Val 분할
    num_samples = len(image_groups)
    indices = np.random.permutation(num_samples)
    val_size = int(num_samples * args.val_split)

    train_indices = indices[val_size:]
    val_indices = indices[:val_size]

    train_groups = [image_groups[i] for i in train_indices]
    train_labels = [labels[i] for i in train_indices]
    val_groups = [image_groups[i] for i in val_indices]
    val_labels = [labels[i] for i in val_indices]

    logger.info(f"Train: {len(train_groups)} samples, Val: {len(val_groups)} samples")

    # 데이터셋 생성
    train_dataset = MotionDataset(train_groups, train_labels)
    val_dataset = MotionDataset(val_groups, val_labels)

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
