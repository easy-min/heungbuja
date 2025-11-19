"""
GCN + CNN 하이브리드 모델로 포즈 시퀀스를 학습하는 스크립트.

입력: pose_sequence_extractor.py가 생성한 .npz 파일 (landmarks, metadata 포함)
구조: pose_sequences/이니셜/동작/*.npz
특징: z 좌표와 visibility를 제외하고, 얼굴 랜드마크(0~10번)도 제외한 신체 랜드마크만 사용합니다.

사용 예시:
    python train_gcn_cnn.py --data_dir ./pose_sequences --epochs 50
    python train_gcn_cnn.py --data_dir ./pose_sequences --persons JSY YHS --actions CLAP EXIT
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
import json
import math
import random
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset

try:
    from mediapipe.python.solutions.pose_connections import POSE_CONNECTIONS
except ImportError:  # pragma: no cover
    from mediapipe.solutions.pose_connections import POSE_CONNECTIONS


SUPPORTED_ACTIONS = ["CLAP", "ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]  # 7개 동작
USED_LANDMARK_INDICES = list(range(11, 33))  # 얼굴(0~10) 제외
HIP_INDICES = (23, 24)


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def build_adjacency(landmark_indices: Sequence[int]) -> torch.Tensor:
    size = len(landmark_indices)
    adjacency = torch.zeros((size, size), dtype=torch.float32)
    index_map = {lm: idx for idx, lm in enumerate(landmark_indices)}

    for i, j in POSE_CONNECTIONS:
        if i in index_map and j in index_map:
            a = index_map[i]
            b = index_map[j]
            adjacency[a, b] = 1.0
            adjacency[b, a] = 1.0

    adjacency += torch.eye(size, dtype=torch.float32)
    degree = adjacency.sum(dim=1)
    degree_inv_sqrt = torch.pow(degree + 1e-6, -0.5)
    degree_inv_sqrt = torch.diag(degree_inv_sqrt)
    normalized = degree_inv_sqrt @ adjacency @ degree_inv_sqrt
    return normalized


def normalize_landmarks(landmarks: np.ndarray) -> np.ndarray:
    """
    landmarks: (T, 33, C) - C는 최소 2(x, y)
    """
    coords = landmarks[..., :2]  # x, y만 사용

    pelvis = (coords[:, HIP_INDICES[0], :] + coords[:, HIP_INDICES[1], :]) / 2.0
    coords = coords - pelvis[:, None, :]

    body_coords = coords[:, USED_LANDMARK_INDICES, :]
    max_range = np.max(np.linalg.norm(body_coords, axis=-1, ord=2))
    if max_range < 1e-6:
        max_range = 1.0
    body_coords = body_coords / max_range

    return body_coords.astype(np.float32)


@dataclass
class SampleInfo:
    path: Path
    label: int
    person: str
    action: str


def print_split_summary(name: str, samples: Sequence[SampleInfo]) -> None:
    print(f"\n{'-'*60}")
    print(f"{name} 샘플 통계")
    print(f"{'-'*60}")
    total = len(samples)
    print(f"총 샘플 수: {total}")
    if not samples:
        return

    action_counts = Counter(sample.action for sample in samples)
    person_counts = Counter(sample.person for sample in samples)
    print("동작별 분포:")
    for action, count in sorted(action_counts.items()):
        print(f"  - {action}: {count}")
    print("이니셜별 분포:")
    for person, count in sorted(person_counts.items()):
        print(f"  - {person}: {count}")

    path_counts = Counter(str(sample.path.resolve()) for sample in samples)
    duplicated = [path for path, count in path_counts.items() if count > 1]
    if duplicated:
        print(f"WARNING: 동일한 파일 경로가 중복으로 감지되었습니다. 예시: {duplicated[:3]}")


class PoseSequenceDataset(Dataset):
    def __init__(
        self,
        samples: Sequence[SampleInfo],
        frames_per_sample: int,
    ) -> None:
        self.samples = list(samples)
        self.frames_per_sample = frames_per_sample

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int) -> Tuple[torch.Tensor, int]:
        sample = self.samples[index]
        data = np.load(sample.path, allow_pickle=True)
        landmarks = data["landmarks"]

        if landmarks.shape[0] != self.frames_per_sample:
            raise ValueError(
                f"프레임 수가 일치하지 않습니다. 기대: {self.frames_per_sample}, "
                f"실제: {landmarks.shape[0]}, 파일: {sample.path}"
            )

        landmarks = normalize_landmarks(landmarks)
        tensor = torch.from_numpy(landmarks)  # (T, N_used, 2)
        return tensor, sample.label


class GCNLayer(nn.Module):
    def __init__(self, in_features: int, out_features: int, adjacency: torch.Tensor, dropout: float = 0.0):
        super().__init__()
        self.linear = nn.Linear(in_features, out_features)
        self.dropout = nn.Dropout(dropout)
        self.norm = nn.LayerNorm(out_features)
        self.register_buffer("adjacency", adjacency)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (B, T, N, F)
        agg = torch.einsum("ij,btnf->btif", self.adjacency, x)
        out = self.linear(agg)
        out = self.dropout(out)
        out = self.norm(out)
        return out


class TemporalCNN(nn.Module):
    def __init__(self, in_channels: int, hidden_channels: Sequence[int], kernel_size: int = 3, dropout: float = 0.2):
        super().__init__()
        layers: List[nn.Module] = []
        prev = in_channels
        padding = kernel_size // 2
        for channels in hidden_channels:
            layers.extend(
                [
                    nn.Conv1d(prev, channels, kernel_size=kernel_size, padding=padding),
                    nn.BatchNorm1d(channels),
                    nn.ReLU(inplace=True),
                    nn.Dropout(dropout),
                ]
            )
            prev = channels
        self.network = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (B, C, T)
        out = self.network(x)
        return out.mean(dim=-1)  # Global average pooling over time


class GCNTemporalModel(nn.Module):
    def __init__(
        self,
        input_dim: int,
        num_classes: int,
        adjacency: torch.Tensor,
        gcn_hidden_dims: Sequence[int] = (64, 128),
        temporal_channels: Sequence[int] = (128, 256),
        dropout: float = 0.3,
    ) -> None:
        super().__init__()
        self.gcn_layers = nn.ModuleList()
        prev_dim = input_dim
        for hidden_dim in gcn_hidden_dims:
            self.gcn_layers.append(GCNLayer(prev_dim, hidden_dim, adjacency, dropout=dropout))
            prev_dim = hidden_dim

        self.temporal_cnn = TemporalCNN(prev_dim, temporal_channels, dropout=dropout)
        temporal_out_dim = temporal_channels[-1] if temporal_channels else prev_dim

        self.classifier = nn.Sequential(
            nn.Linear(temporal_out_dim, temporal_out_dim),
            nn.ReLU(inplace=True),
            nn.Dropout(dropout),
            nn.Linear(temporal_out_dim, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (B, T, N, C)
        for gcn in self.gcn_layers:
            x = F.relu(gcn(x))

        x = x.mean(dim=2)  # (B, T, C_gcn)
        x = x.permute(0, 2, 1)  # (B, C_gcn, T)
        features = self.temporal_cnn(x)  # (B, C_out)
        logits = self.classifier(features)
        return logits


def collect_samples(
    data_dir: Path,
    action_to_label: Dict[str, int],
    frames_per_sample: int,
    persons: Optional[Iterable[str]] = None,
    actions: Optional[Iterable[str]] = None,
) -> List[SampleInfo]:
    person_filter = {p.upper() for p in persons} if persons else None
    action_filter = {a.upper() for a in actions} if actions else None

    samples: List[SampleInfo] = []
    for person_dir in sorted(p for p in data_dir.iterdir() if p.is_dir()):
        person_name = person_dir.name.upper()
        if person_filter and person_name not in person_filter:
            continue

        for action_dir in sorted(p for p in person_dir.iterdir() if p.is_dir()):
            action_name = action_dir.name.upper()
            if action_filter and action_name not in action_filter:
                continue
            if action_name not in action_to_label:
                print(f"⚠️  미지원 동작 건너뜀: {action_dir}")
                continue

            for npz_file in sorted(action_dir.glob("*.npz")):
                try:
                    with np.load(npz_file, allow_pickle=True) as data:
                        landmarks = data["landmarks"]
                        if landmarks.shape[0] != frames_per_sample:
                            print(
                                f"⚠️  프레임 수 불일치로 건너뜀: {npz_file} "
                                f"(expect {frames_per_sample}, got {landmarks.shape[0]})"
                            )
                            continue
                except Exception as error:
                    print(f"⚠️  파일 로드 실패: {npz_file}, 오류: {error}")
                    continue

                samples.append(
                    SampleInfo(
                        path=npz_file,
                        label=action_to_label[action_name],
                        person=person_name,
                        action=action_name,
                    )
                )

    return samples


def split_samples(
    samples: Sequence[SampleInfo],
    val_split: float,
    seed: int,
) -> Tuple[List[SampleInfo], List[SampleInfo]]:
    if not samples or val_split <= 0.0:
        return [], []

    samples = list(samples)
    random.Random(seed).shuffle(samples)

    if len(samples) < 2:
        return samples, []

    val_size = int(round(len(samples) * val_split))
    val_size = max(1, min(val_size, len(samples) - 1))

    val_samples = samples[:val_size]
    train_samples = samples[val_size:]
    return train_samples, val_samples


@dataclass
class EpochResult:
    loss: float
    accuracy: float
    per_action: Optional[Dict[str, Tuple[int, int]]] = None


def train_one_epoch(
    model: nn.Module,
    loader: DataLoader,
    criterion: nn.Module,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
    grad_clip: Optional[float] = None,
) -> EpochResult:
    model.train()
    total_loss = 0.0
    correct = 0
    total = 0

    for sequences, labels in loader:
        sequences = sequences.to(device)
        labels = labels.to(device)

        optimizer.zero_grad()
        logits = model(sequences)
        loss = criterion(logits, labels)
        loss.backward()

        if grad_clip:
            torch.nn.utils.clip_grad_norm_(model.parameters(), grad_clip)

        optimizer.step()

        total_loss += loss.item() * labels.size(0)
        preds = logits.argmax(dim=1)
        correct += (preds == labels).sum().item()
        total += labels.size(0)

    average_loss = total_loss / max(total, 1)
    accuracy = correct / max(total, 1)
    return EpochResult(loss=average_loss, accuracy=accuracy)


@torch.no_grad()
def evaluate(
    model: nn.Module,
    loader: DataLoader,
    criterion: nn.Module,
    device: torch.device,
    label_to_action: Dict[int, str],
) -> EpochResult:
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0
    per_action_correct = Counter()
    per_action_total = Counter()

    for sequences, labels in loader:
        sequences = sequences.to(device)
        labels = labels.to(device)

        logits = model(sequences)
        loss = criterion(logits, labels)

        total_loss += loss.item() * labels.size(0)
        preds = logits.argmax(dim=1)
        correct += (preds == labels).sum().item()
        total += labels.size(0)

        for label in labels:
            per_action_total[label.item()] += 1
        for pred, label in zip(preds, labels):
            if pred == label:
                per_action_correct[label.item()] += 1

    average_loss = total_loss / max(total, 1)
    accuracy = correct / max(total, 1)
    per_action_summary = {
        label_to_action[idx]: (per_action_correct.get(idx, 0), per_action_total.get(idx, 0))
        for idx in label_to_action.keys()
        if per_action_total.get(idx, 0) > 0
    }
    return EpochResult(loss=average_loss, accuracy=accuracy, per_action=per_action_summary)


def save_checkpoint(
    path: Path,
    model: nn.Module,
    optimizer: torch.optim.Optimizer,
    epoch: int,
    best_val_acc: float,
    args: argparse.Namespace,
    class_mapping: Dict[str, int],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "model_state_dict": model.state_dict(),
            "optimizer_state_dict": optimizer.state_dict(),
            "epoch": epoch,
            "best_val_acc": best_val_acc,
            "args": vars(args),
            "class_mapping": class_mapping,
        },
        path,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="GCN+CNN 하이브리드 모델 학습 스크립트")
    parser.add_argument("--data_dir", type=str, required=True, help="pose_sequence_extractor.py 산출물 경로")
    parser.add_argument("--persons", nargs="*", default=None, help="특정 이니셜만 학습 (예: JSY YHS)")
    parser.add_argument("--actions", nargs="*", default=None, help="특정 동작만 학습 (예: CLAP EXIT)")
    parser.add_argument("--frames_per_sample", type=int, default=8, help="시퀀스 길이 (기본: 8)")
    parser.add_argument("--batch_size", type=int, default=32)
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--learning_rate", type=float, default=1e-3)
    parser.add_argument("--weight_decay", type=float, default=1e-4)
    parser.add_argument("--val_split", type=float, default=0.2, help="검증 데이터 비율 (0~0.5 권장)")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--num_workers", type=int, default=2)
    parser.add_argument("--grad_clip", type=float, default=1.0)
    parser.add_argument("--dropout", type=float, default=0.3)
    parser.add_argument("--temporal_channels", type=int, nargs="*", default=[128, 256])
    parser.add_argument("--gcn_hidden_dims", type=int, nargs="*", default=[64, 128])
    parser.add_argument("--device", type=str, default="auto", help="'cpu', 'cuda', 'mps', 또는 'auto'")
    parser.add_argument("--save_dir", type=str, default="checkpoints", help="모델 저장 경로")
    parser.add_argument("--save_name", type=str, default="gcn_cnn_best.pt")
    parser.add_argument("--no_checkpoint", action="store_true", help="체크포인트 저장 비활성화")
    parser.add_argument("--report_json", type=str, default=None, help="최종 결과를 JSON으로 저장")
    return parser.parse_args()


def auto_device(device_str: str) -> torch.device:
    if device_str != "auto":
        return torch.device(device_str)
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def main() -> None:
    args = parse_args()
    set_seed(args.seed)

    data_dir = Path(args.data_dir)
    if not data_dir.exists():
        raise FileNotFoundError(f"데이터 디렉토리를 찾을 수 없습니다: {data_dir}")

    device = auto_device(args.device)
    print(f"▶ 사용 디바이스: {device}")

    if args.actions:
        selected_actions = [action.upper() for action in args.actions]
    else:
        selected_actions = SUPPORTED_ACTIONS
    action_to_label = {action: idx for idx, action in enumerate(sorted(set(selected_actions)))}
    label_to_action = {label: action for action, label in action_to_label.items()}
    print(f"▶ 학습 대상 동작: {', '.join(action_to_label.keys())}")

    samples = collect_samples(
        data_dir=data_dir,
        action_to_label=action_to_label,
        frames_per_sample=args.frames_per_sample,
        persons=args.persons,
        actions=selected_actions,
    )

    if not samples:
        raise RuntimeError(f"학습 가능한 시퀀스를 찾을 수 없습니다: {data_dir}")

    train_samples, val_samples = split_samples(samples, args.val_split, args.seed)
    if not train_samples:
        raise RuntimeError("학습 세트가 비어 있습니다. val_split을 줄여보세요.")

    print(f"▶ 학습 샘플: {len(train_samples)}개, 검증 샘플: {len(val_samples)}개")
    print_split_summary("TRAIN", train_samples)
    print_split_summary("VAL", val_samples)

    train_paths = {sample.path.resolve() for sample in train_samples}
    val_paths = {sample.path.resolve() for sample in val_samples}
    overlap = train_paths & val_paths
    if overlap:
        print(f"⚠️  학습/검증 세트에 동일한 파일이 있습니다. 예시: {list(overlap)[:3]}")

    if val_samples and len(val_samples) < 5:
        print("ℹ️  검증 샘플 수가 매우 적습니다. 교차 검증 또는 val_split 조정이 필요할 수 있습니다.")

    dataset_args = dict(frames_per_sample=args.frames_per_sample)
    train_dataset = PoseSequenceDataset(train_samples, **dataset_args)
    train_loader = DataLoader(
        train_dataset,
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.num_workers,
        pin_memory=True,
    )

    val_loader = None
    if val_samples:
        val_dataset = PoseSequenceDataset(val_samples, **dataset_args)
        val_loader = DataLoader(
            val_dataset,
            batch_size=args.batch_size,
            shuffle=False,
            num_workers=args.num_workers,
            pin_memory=True,
        )

    input_dim = 2
    adjacency = build_adjacency(USED_LANDMARK_INDICES)
    model = GCNTemporalModel(
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
    criterion = nn.CrossEntropyLoss()

    best_val_acc = -math.inf
    best_epoch = -1
    checkpoint_path = Path(args.save_dir) / args.save_name

    history = []
    for epoch in range(1, args.epochs + 1):
        train_result = train_one_epoch(
            model,
            train_loader,
            criterion,
            optimizer,
            device,
            grad_clip=args.grad_clip,
        )

        scheduler.step()

        if val_loader:
            val_result = evaluate(model, val_loader, criterion, device, label_to_action)
            val_loss = val_result.loss
            val_acc = val_result.accuracy
        else:
            val_result = None
            val_loss = float("nan")
            val_acc = float("nan")

        history.append(
            {
                "epoch": epoch,
                "train_loss": train_result.loss,
                "train_acc": train_result.accuracy,
                "val_loss": val_loss,
                "val_acc": val_acc,
                "lr": scheduler.get_last_lr()[0],
            }
        )

        val_loss_str = f"{val_loss:.4f}" if not math.isnan(val_loss) else "n/a"
        val_acc_str = f"{val_acc*100:.2f}%" if not math.isnan(val_acc) else "n/a"
        print(
            f"[Epoch {epoch:03d}] "
            f"train_loss={train_result.loss:.4f}, train_acc={train_result.accuracy*100:.2f}% "
            f"| val_loss={val_loss_str}, val_acc={val_acc_str}"
        )

        if val_result and val_result.per_action:
            print("  - 검증 동작별 정확도:")
            for action, (correct, total) in sorted(val_result.per_action.items()):
                if total == 0:
                    continue
                acc = correct / total
                print(f"    - {action}: {correct}/{total} ({acc*100:.2f}%)")

        if val_loader and val_result and val_result.accuracy > best_val_acc:
            best_val_acc = val_result.accuracy
            best_epoch = epoch
            if not args.no_checkpoint:
                save_checkpoint(
                    checkpoint_path,
                    model,
                    optimizer,
                    epoch,
                    best_val_acc,
                    args,
                    action_to_label,
                )

    if val_loader and best_epoch > 0:
        print(f"최적 검증 정확도: {best_val_acc*100:.2f}% @ epoch {best_epoch}")
        if not args.no_checkpoint:
            print(f"체크포인트 저장 위치: {checkpoint_path}")
    elif not val_loader:
        if not args.no_checkpoint:
            save_checkpoint(
                checkpoint_path,
                model,
                optimizer,
                args.epochs,
                float("nan"),
                args,
                action_to_label,
            )
            print(f"체크포인트 저장 위치: {checkpoint_path}")

    if args.report_json:
        report = {
            "best_val_acc": best_val_acc if best_val_acc > -math.inf else None,
            "best_epoch": best_epoch if best_epoch > 0 else None,
            "history": history,
            "class_mapping": action_to_label,
        }
        report_path = Path(args.report_json)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with report_path.open("w", encoding="utf-8") as file:
            json.dump(report, file, ensure_ascii=False, indent=2)
        print(f"학습 리포트 저장: {report_path}")


if __name__ == "__main__":
    main()

