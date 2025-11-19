"""
학습 데이터와 추론 입력 전처리 비교 디버깅

학습 시 사용한 .npz 파일과 실제 이미지에서 추출한 pose를 비교합니다.
"""

import base64
import glob
import os
from pathlib import Path

import numpy as np
from PIL import Image

from app.services.inference import PoseExtractor


def load_training_npz_sample():
    """학습에 사용된 .npz 샘플 하나 로드"""
    npz_files = glob.glob("app/brandnewTrain/pose_sequences/JSY/CLAP/*.npz")

    if not npz_files:
        print("❌ .npz 파일을 찾을 수 없습니다")
        return None

    npz_path = npz_files[0]
    print(f"📂 학습 데이터 샘플: {npz_path}")

    data = np.load(npz_path)
    landmarks = data["landmarks"]  # (T, 33, 3 or 4)

    print(f"   Shape: {landmarks.shape}")
    print(f"   Frames: {len(landmarks)}")

    return landmarks


def load_inference_image_sample():
    """추론에 사용하는 이미지 샘플 로드 및 전처리"""
    image_files = sorted(glob.glob("app/brandnewTrain/extracted_data/JSY/CLAP/clap_seq001_frame*.jpg"))
    image_files = [f for f in image_files if "_backup" not in f]

    if len(image_files) < 8:
        print("❌ 이미지 파일이 부족합니다")
        return None

    print(f"📸 추론 이미지 샘플: clap_seq001 (8 frames)")

    pose_extractor = PoseExtractor()
    keypoints_list = []

    for img_path in image_files[:8]:
        # 이미지 로드
        image = Image.open(img_path)
        image_np = np.array(image)

        # Pose 추출 (전처리 포함)
        keypoints = pose_extractor.extract(image_np)  # (22, 2)
        keypoints_list.append(keypoints)

    keypoints_array = np.stack(keypoints_list, axis=0)  # (8, 22, 2)
    print(f"   Shape: {keypoints_array.shape}")
    print(f"   Frames: {len(keypoints_array)}")

    return keypoints_array


def compare_preprocessing():
    """학습 데이터와 추론 데이터의 전처리 결과 비교"""
    print("\n" + "=" * 80)
    print("🔍 전처리 비교 분석")
    print("=" * 80 + "\n")

    # 학습 데이터
    training_landmarks = load_training_npz_sample()
    if training_landmarks is None:
        return

    # 학습 데이터 전처리 시뮬레이션
    from app.brandnewTrain.train_gcn_cnn import normalize_landmarks, USED_LANDMARK_INDICES

    training_normalized = normalize_landmarks(training_landmarks)  # (T, 22, 2)
    print(f"\n📊 학습 데이터 전처리 결과:")
    print(f"   Shape: {training_normalized.shape}")
    print(f"   Mean: {training_normalized.mean():.6f}")
    print(f"   Std: {training_normalized.std():.6f}")
    print(f"   Min: {training_normalized.min():.6f}")
    print(f"   Max: {training_normalized.max():.6f}")
    print(f"   첫 프레임 첫 키포인트: {training_normalized[0, 0]}")

    # 추론 데이터
    print("\n")
    inference_keypoints = load_inference_image_sample()
    if inference_keypoints is None:
        return

    print(f"\n📊 추론 데이터 전처리 결과:")
    print(f"   Shape: {inference_keypoints.shape}")
    print(f"   Mean: {inference_keypoints.mean():.6f}")
    print(f"   Std: {inference_keypoints.std():.6f}")
    print(f"   Min: {inference_keypoints.min():.6f}")
    print(f"   Max: {inference_keypoints.max():.6f}")
    print(f"   첫 프레임 첫 키포인트: {inference_keypoints[0, 0]}")

    # 비교
    print("\n" + "=" * 80)
    print("📊 통계 비교:")
    print("=" * 80)

    print(f"\nMean 차이: {abs(training_normalized.mean() - inference_keypoints.mean()):.6f}")
    print(f"Std 차이: {abs(training_normalized.std() - inference_keypoints.std()):.6f}")

    # 값 범위 비교
    print(f"\n학습 데이터 범위: [{training_normalized.min():.4f}, {training_normalized.max():.4f}]")
    print(f"추론 데이터 범위: [{inference_keypoints.min():.4f}, {inference_keypoints.max():.4f}]")

    # Shape 비교
    print(f"\n학습 데이터 Shape: {training_normalized.shape}")
    print(f"추론 데이터 Shape: {inference_keypoints.shape}")

    if training_normalized.shape[1:] != inference_keypoints.shape[1:]:
        print("\n⚠️  경고: Shape가 다릅니다!")
        print(f"   학습: (frames, {training_normalized.shape[1]}, {training_normalized.shape[2]})")
        print(f"   추론: (frames, {inference_keypoints.shape[1]}, {inference_keypoints.shape[2]})")

    # 0 벡터 확인
    training_zeros = np.all(training_normalized == 0, axis=-1).sum()
    inference_zeros = np.all(inference_keypoints == 0, axis=-1).sum()

    print(f"\n0 벡터 개수:")
    print(f"   학습 데이터: {training_zeros} / {training_normalized.shape[0] * training_normalized.shape[1]}")
    print(f"   추론 데이터: {inference_zeros} / {inference_keypoints.shape[0] * inference_keypoints.shape[1]}")

    print("\n" + "=" * 80 + "\n")


if __name__ == "__main__":
    compare_preprocessing()
