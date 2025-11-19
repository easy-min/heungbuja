"""
모델을 직접 테스트하여 학습/평가 시 정규화와 추론 시 정규화가 동일한지 확인
"""
import numpy as np
import torch
import torch.nn.functional as F
from pathlib import Path

# Import from train_gcn_cnn
from train_gcn_cnn import (
    SUPPORTED_ACTIONS,
    USED_LANDMARK_INDICES,
    HIP_INDICES,
    normalize_landmarks,
    GCNTemporalModel,
    build_adjacency,
)


def test_sample(model, sample_path, device="cuda"):
    """단일 샘플 테스트"""
    # Load sample
    with np.load(sample_path, allow_pickle=True) as data:
        landmarks = data["landmarks"]  # (T, 33, 2)

    print(f"\n{'='*80}")
    print(f"Testing: {sample_path}")
    print(f"{'='*80}")

    # Original shape
    print(f"Original landmarks shape: {landmarks.shape}")

    # Apply SAME normalization as training
    landmarks_normalized = normalize_landmarks(landmarks)  # (T, 22, 2)

    print(f"Normalized landmarks shape: {landmarks_normalized.shape}")
    print(f"Normalized stats - mean: {landmarks_normalized.mean():.4f}, "
          f"std: {landmarks_normalized.std():.4f}, "
          f"min: {landmarks_normalized.min():.4f}, "
          f"max: {landmarks_normalized.max():.4f}")

    # Convert to tensor
    input_tensor = torch.from_numpy(landmarks_normalized).unsqueeze(0).to(device)  # (1, T, N, 2)

    # Model inference
    with torch.no_grad():
        logits = model(input_tensor)
        probs = F.softmax(logits, dim=1).cpu().numpy()[0]

    print(f"\nLogits: {logits.cpu().numpy()[0]}")
    print(f"Probabilities: {probs}")

    # Get prediction
    pred_idx = int(np.argmax(probs))
    pred_label = SUPPORTED_ACTIONS[pred_idx]
    confidence = probs[pred_idx]

    print(f"\nPredicted: {pred_label} (class {pred_idx}) with {confidence*100:.2f}% confidence")

    # Expected label from path
    for action in SUPPORTED_ACTIONS:
        if action.lower() in str(sample_path).lower():
            expected = action
            expected_idx = SUPPORTED_ACTIONS.index(action)
            expected_prob = probs[expected_idx]
            print(f"Expected: {expected} (class {expected_idx})")
            print(f"Expected probability: {expected_prob*100:.2f}%")

            if pred_label == expected:
                print("✅ CORRECT")
            else:
                print("❌ WRONG")
            break


def main():
    # Load model
    model_path = Path("../trained_model/gcn_cnn_best.pt")
    device = "cuda" if torch.cuda.is_available() else "cpu"

    print(f"Device: {device}")
    print(f"Loading model from: {model_path}")

    checkpoint = torch.load(model_path, map_location=device, weights_only=False)

    # Model parameters
    gcn_hidden_dims = checkpoint.get("args", {}).get("gcn_hidden_dims", [96, 192])
    temporal_channels = checkpoint.get("args", {}).get("temporal_channels", [192, 384])
    dropout = checkpoint.get("args", {}).get("dropout", 0.4)
    num_classes = len(SUPPORTED_ACTIONS)

    print(f"Class mapping from checkpoint: {checkpoint.get('class_mapping', {})}")

    # Build model
    input_dim = 2
    adjacency = build_adjacency(USED_LANDMARK_INDICES)

    model = GCNTemporalModel(
        input_dim=input_dim,
        num_classes=num_classes,
        adjacency=adjacency,
        gcn_hidden_dims=gcn_hidden_dims,
        temporal_channels=temporal_channels,
        dropout=dropout,
    )

    model.load_state_dict(checkpoint["model_state_dict"])
    model.to(device)
    model.eval()

    print("✅ Model loaded successfully")

    # Test samples from each class
    data_dir = Path("pose_sequences")

    # Find one sample per action
    test_samples = []
    for action in SUPPORTED_ACTIONS:
        # Find first available sample for this action
        for person_dir in sorted(data_dir.iterdir()):
            if not person_dir.is_dir():
                continue
            action_dir = person_dir / action
            if not action_dir.exists():
                continue

            samples = list(action_dir.glob("*.npz"))
            if samples:
                test_samples.append(samples[0])
                break

    print(f"\nTesting {len(test_samples)} samples (one per action)")

    for sample_path in test_samples:
        test_sample(model, sample_path, device)


if __name__ == "__main__":
    main()
