"""
서버 처리 과정을 완전히 시뮬레이션해서 테스트

이미지 → Base64 → 서버 처리 → 모델 예측
"""
import base64
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from pathlib import Path
from typing import Sequence, List
import cv2
import mediapipe as mp

# MediaPipe 초기화
mp_pose = mp.solutions.pose
pose_extractor = mp_pose.Pose(
    static_image_mode=True,
    model_complexity=1,
    enable_segmentation=False,
    min_detection_confidence=0.5,
)

# 모델 클래스 정의
class GCNLayer(nn.Module):
    def __init__(self, in_features: int, out_features: int, adjacency: torch.Tensor, dropout: float = 0.0):
        super().__init__()
        self.linear = nn.Linear(in_features, out_features)
        self.dropout = nn.Dropout(dropout)
        self.norm = nn.LayerNorm(out_features)
        self.register_buffer("adjacency", adjacency)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
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
            layers.extend([
                nn.Conv1d(prev, channels, kernel_size=kernel_size, padding=padding),
                nn.BatchNorm1d(channels),
                nn.ReLU(inplace=True),
                nn.Dropout(dropout),
            ])
            prev = channels
        self.network = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = self.network(x)
        return out.mean(dim=-1)

class GCNTemporalModel(nn.Module):
    def __init__(self, input_dim: int, num_classes: int, adjacency: torch.Tensor,
                 gcn_hidden_dims: Sequence[int] = (64, 128),
                 temporal_channels: Sequence[int] = (128, 256),
                 dropout: float = 0.3) -> None:
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
        for gcn in self.gcn_layers:
            x = F.relu(gcn(x))
        x = x.mean(dim=2)
        x = x.permute(0, 2, 1)
        features = self.temporal_cnn(x)
        logits = self.classifier(features)
        return logits

def normalize_sequence(landmarks_sequence: np.ndarray) -> np.ndarray:
    """정규화 (train_gcn_cnn.py와 동일)"""
    HIP_INDICES = (23, 24)
    USED_LANDMARK_INDICES = list(range(11, 33))

    coords = landmarks_sequence[..., :2]
    pelvis = (coords[:, HIP_INDICES[0], :] + coords[:, HIP_INDICES[1], :]) / 2.0
    coords = coords - pelvis[:, None, :]

    body_coords = coords[:, USED_LANDMARK_INDICES, :]
    max_range = np.max(np.linalg.norm(body_coords, axis=-1, ord=2))
    if max_range < 1e-6:
        max_range = 1.0
    body_coords = body_coords / max_range

    return body_coords.astype(np.float32)

# 모델 로드
print("\n" + "=" * 80)
print("🧪 서버 시뮬레이션 테스트")
print("=" * 80)

model_path = Path("app/brandnewTrain/checkpoints/brandnew_model_v1.pt")
checkpoint = torch.load(model_path, map_location="cpu", weights_only=False)
class_mapping = checkpoint.get("class_mapping", {})
id_to_label = {index: label for label, index in class_mapping.items()}

print(f"\n모델 정보: {id_to_label}")

# 모델 생성
args = checkpoint.get("args", {})
gcn_hidden_dims = args.get("gcn_hidden_dims", [64, 128])
temporal_channels = args.get("temporal_channels", [128, 256])
dropout = float(args.get("dropout", 0.3))
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
model.eval()

# 테스트: ELBOW seq001
test_dir = Path("app/brandnewTrain/extracted_data/JSY/ELBOW")
frames = []

for i in range(1, 9):
    img_path = test_dir / f"elbow_seq001_frame{i}.jpg"
    with open(img_path, "rb") as f:
        img_data = f.read()
        b64 = base64.b64encode(img_data).decode("utf-8")
        frames.append(b64)

print("\n" + "=" * 80)
print("📂 ELBOW seq001 테스트 (서버 시뮬레이션)")
print("=" * 80)

# 서버 처리 과정 완전 재현
raw_landmarks_list = []

for idx, encoded in enumerate(frames, 1):
    image_data = base64.b64decode(encoded)
    nparr = np.frombuffer(image_data, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

    results = pose_extractor.process(image_rgb)

    if not results.pose_landmarks:
        print(f"⚠️  Frame {idx}: Pose 실패!")
        continue

    landmarks = results.pose_landmarks.landmark
    all_coords = np.array([(lm.x, lm.y) for lm in landmarks], dtype=np.float32)
    raw_landmarks_list.append(all_coords)

# 정규화
raw_sequence = np.stack(raw_landmarks_list, axis=0)
normalized = normalize_sequence(raw_sequence)

# 모델 예측
input_tensor = torch.from_numpy(normalized).unsqueeze(0)

with torch.no_grad():
    logits = model(input_tensor)
    probabilities = torch.softmax(logits, dim=-1).cpu().numpy()[0]

# 결과
best_idx = int(np.argmax(probabilities))
predicted_label = id_to_label.get(best_idx, "UNKNOWN")
confidence = float(probabilities[best_idx])

print(f"\n예측 결과: {predicted_label} ({confidence*100:.1f}%)")
print(f"정답: ELBOW")
print(f"정확: {'✅' if predicted_label == 'ELBOW' else '❌'}")

print(f"\n전체 확률 분포:")
for idx, prob in sorted(enumerate(probabilities), key=lambda x: x[1], reverse=True):
    label = id_to_label.get(idx, "UNKNOWN")
    bar = "█" * int(prob * 50)
    print(f"  {label:10s}: {prob*100:6.2f}% {bar}")

print("=" * 80 + "\n")
