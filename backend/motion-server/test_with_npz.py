"""
.npz 파일로 직접 모델 테스트

이미지 → MediaPipe 변환 과정을 건너뛰고
학습 데이터와 동일한 .npz 파일로 직접 테스트
"""
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from pathlib import Path
from collections import defaultdict
from typing import Sequence, List

# 모델 클래스 정의 (train_gcn_cnn.py에서 복사, MediaPipe 제거)
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

print("\n" + "=" * 80)
print("🧪 .npz 파일로 직접 모델 테스트")
print("=" * 80)

# 모델 로드
model_path = Path("app/brandnewTrain/checkpoints/brandnew_model_v1.pt")
checkpoint = torch.load(model_path, map_location="cpu", weights_only=False)

args = checkpoint.get("args", {})
class_mapping = checkpoint.get("class_mapping", {})

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
id_to_label = {index: label for label, index in class_mapping.items()}

print(f"\n모델 정보:")
print(f"  클래스 매핑: {id_to_label}")
print(f"  Device: {device}")

# 모델 생성
gcn_hidden_dims = args.get("gcn_hidden_dims", [64, 128])
temporal_channels = args.get("temporal_channels", [128, 256])
dropout = float(args.get("dropout", 0.3))

# adjacency matrix 로드
adjacency = checkpoint["model_state_dict"]["gcn_layers.0.adjacency"]

model = GCNTemporalModel(
    input_dim=checkpoint["model_state_dict"]["gcn_layers.0.linear.weight"].shape[1],
    num_classes=len(class_mapping),
    adjacency=adjacency,
    gcn_hidden_dims=gcn_hidden_dims,
    temporal_channels=temporal_channels,
    dropout=dropout,
)
model.load_state_dict(checkpoint["model_state_dict"])
model.to(device)
model.eval()

print(f"  파라미터 수: {sum(p.numel() for p in model.parameters()):,}")

# 정규화 함수 (train_gcn_cnn.py와 동일)
def normalize_landmarks(landmarks: np.ndarray) -> np.ndarray:
    """
    landmarks: (T, 33, C) - C는 최소 2(x, y)
    """
    HIP_INDICES = (23, 24)
    USED_LANDMARK_INDICES = list(range(11, 33))

    coords = landmarks[..., :2]  # x, y만 사용
    pelvis = (coords[:, HIP_INDICES[0], :] + coords[:, HIP_INDICES[1], :]) / 2.0
    coords = coords - pelvis[:, None, :]

    body_coords = coords[:, USED_LANDMARK_INDICES, :]
    max_range = np.max(np.linalg.norm(body_coords, axis=-1, ord=2))
    if max_range < 1e-6:
        max_range = 1.0
    body_coords = body_coords / max_range

    return body_coords.astype(np.float32)

# 테스트할 샘플들
data_dir = Path("app/brandnewTrain/pose_sequences_from_images")
test_samples = [
    ("JSY", "ELBOW", "elbow_seq001.npz"),
    ("JSY", "ELBOW", "elbow_seq002.npz"),
    ("JSY", "ELBOW", "elbow_seq003.npz"),
    ("JSY", "STRETCH", "stretch_seq001.npz"),
    ("JSY", "STRETCH", "stretch_seq002.npz"),
    ("JSY", "STRETCH", "stretch_seq003.npz"),
    ("JSY", "TILT", "tilt_seq001.npz"),
    ("JSY", "TILT", "tilt_seq002.npz"),
    ("JSY", "TILT", "tilt_seq003.npz"),
    ("JSY", "EXIT", "exit_seq001.npz"),
    ("JSY", "EXIT", "exit_seq002.npz"),
    ("JSY", "EXIT", "exit_seq003.npz"),
    ("JSY", "UNDERARM", "underarm_seq001.npz"),
    ("JSY", "UNDERARM", "underarm_seq002.npz"),
    ("JSY", "UNDERARM", "underarm_seq003.npz"),
    ("JSY", "STAY", "stay_seq001.npz"),
    ("JSY", "STAY", "stay_seq002.npz"),
    ("JSY", "STAY", "stay_seq003.npz"),
]

results = []
action_results = defaultdict(lambda: {"correct": 0, "total": 0, "predictions": []})

print("\n" + "=" * 80)
print("📊 테스트 진행")
print("=" * 80)

for person, action, filename in test_samples:
    npz_path = data_dir / person / action / filename

    if not npz_path.exists():
        print(f"⚠️  {person}/{action}/{filename}: 파일 없음")
        continue

    # .npz 로드
    data = np.load(npz_path, allow_pickle=True)
    landmarks = data["landmarks"]  # (8, 33, 4)

    # 정규화 (train_gcn_cnn.py의 normalize_landmarks와 동일)
    normalized = normalize_landmarks(landmarks)  # (8, 22, 2)

    # 모델 추론
    input_tensor = torch.from_numpy(normalized).unsqueeze(0)  # (1, 8, 22, 2)
    input_tensor = input_tensor.to(device)

    with torch.no_grad():
        logits = model(input_tensor)
        probabilities = torch.softmax(logits, dim=-1).cpu().numpy()[0]

    # 예측
    best_idx = int(np.argmax(probabilities))
    predicted_label = id_to_label.get(best_idx, "UNKNOWN")
    confidence = float(probabilities[best_idx])

    # 정답 확인
    correct = (predicted_label == action)
    emoji = "✅" if correct else "❌"

    print(f"{emoji} {person}/{action}/{filename}: 예측={predicted_label} ({confidence*100:.1f}%)")

    # 통계
    action_results[action]["total"] += 1
    action_results[action]["predictions"].append(predicted_label)
    if correct:
        action_results[action]["correct"] += 1

    results.append({
        "action": action,
        "predicted": predicted_label,
        "confidence": confidence,
        "correct": correct,
    })

# 요약
print("\n" + "=" * 80)
print("📊 테스트 결과 요약")
print("=" * 80)

total = len(results)
correct_total = sum(1 for r in results if r["correct"])
accuracy = correct_total / total * 100 if total > 0 else 0

print(f"\n전체 정확도: {correct_total}/{total} ({accuracy:.1f}%)")

print(f"\n동작별 정확도:")
for action in sorted(action_results.keys()):
    stats = action_results[action]
    action_acc = stats["correct"] / stats["total"] * 100 if stats["total"] > 0 else 0
    emoji = "✅" if stats["correct"] == stats["total"] else "❌"

    print(f"  {emoji} {action:10s}: {stats['correct']}/{stats['total']} ({action_acc:.0f}%)")

    # 잘못 예측한 경우 어떤 클래스로 예측했는지
    if stats["correct"] < stats["total"]:
        from collections import Counter
        pred_counter = Counter(stats["predictions"])
        print(f"      예측 분포: {dict(pred_counter)}")

print("\n" + "=" * 80)
print("💡 분석")
print("=" * 80)
print("만약 이 결과도 낮다면:")
print("  → 모델 자체가 잘못 학습됨 (데이터 문제 or 모델 문제)")
print("\n만약 이 결과가 높다면:")
print("  → 이미지 → MediaPipe 변환 과정에 문제 있음")
print("  → 인코딩/디코딩 or MediaPipe 일관성 문제")
print("=" * 80 + "\n")
