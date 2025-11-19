"""
이미지 처리 방식에 따른 MediaPipe 추출 결과 비교

방법 1: cv2.imread (학습 데이터 생성 시)
방법 2: JPG → Base64 → PIL (추론 시)
"""
import base64
import cv2
import numpy as np
import mediapipe as mp
from PIL import Image
from io import BytesIO
from pathlib import Path

# MediaPipe 초기화 (동일한 설정)
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(
    static_image_mode=True,
    model_complexity=1,
    enable_segmentation=False,
    min_detection_confidence=0.5,
)

def method1_cv2(image_path):
    """방법 1: cv2.imread (학습 데이터 생성 시)"""
    image = cv2.imread(str(image_path))
    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    results = pose.process(image_rgb)

    if not results.pose_landmarks:
        return None

    landmarks = []
    for lm in results.pose_landmarks.landmark:
        landmarks.append([lm.x, lm.y])

    return np.array(landmarks, dtype=np.float32)

def method2_base64_pil(image_path):
    """방법 2: JPG → Base64 → PIL (추론 시)"""
    # JPG → Base64
    with open(image_path, "rb") as f:
        img_data = f.read()
        b64 = base64.b64encode(img_data).decode("utf-8")

    # Base64 → PIL
    image_data = base64.b64decode(b64)
    with Image.open(BytesIO(image_data)) as img:
        rgb_image = img.convert("RGB")
        image_np = np.array(rgb_image)

    # MediaPipe 추출
    results = pose.process(image_np)

    if not results.pose_landmarks:
        return None

    landmarks = []
    for lm in results.pose_landmarks.landmark:
        landmarks.append([lm.x, lm.y])

    return np.array(landmarks, dtype=np.float32)

# 테스트
test_images = [
    "app/brandnewTrain/extracted_data/JSY/ELBOW/elbow_seq001_frame1.jpg",
    "app/brandnewTrain/extracted_data/JSY/TILT/tilt_seq001_frame1.jpg",
    "app/brandnewTrain/extracted_data/JSY/STRETCH/stretch_seq001_frame1.jpg",
]

print("\n" + "=" * 80)
print("🔍 이미지 처리 방식 비교")
print("=" * 80)

for img_path in test_images:
    path = Path(img_path)
    if not path.exists():
        print(f"\n⚠️  {img_path}: 파일 없음")
        continue

    print(f"\n📂 {path.name}")

    # 방법 1: cv2
    landmarks1 = method1_cv2(path)

    # 방법 2: Base64 + PIL
    landmarks2 = method2_base64_pil(path)

    if landmarks1 is None or landmarks2 is None:
        print("   ❌ Pose 추출 실패")
        continue

    # 차이 계산
    diff = np.abs(landmarks1 - landmarks2)
    max_diff = np.max(diff)
    mean_diff = np.mean(diff)

    print(f"   Method 1 (cv2):      shape={landmarks1.shape}, sample={landmarks1[0]}")
    print(f"   Method 2 (base64):   shape={landmarks2.shape}, sample={landmarks2[0]}")
    print(f"   차이: max={max_diff:.6f}, mean={mean_diff:.6f}")

    if max_diff > 0.001:
        print(f"   ⚠️  큰 차이 발견! (max_diff={max_diff:.6f})")
    else:
        print(f"   ✅ 거의 동일")

print("\n" + "=" * 80)
print("💡 결과 분석")
print("=" * 80)
print("만약 차이가 크다면:")
print("  → 이미지 인코딩/디코딩 과정에서 손실 발생")
print("  → JPG 압축 설정이나 PIL vs cv2 차이")
print("\n만약 차이가 거의 없다면:")
print("  → 다른 원인 (프레임 샘플링? 시퀀스 길이?)")
print("=" * 80 + "\n")
