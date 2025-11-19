"""
서버로 전송되는 입력 디버깅

실제로 서버에 어떤 데이터가 들어가는지 확인
"""
import base64
import numpy as np
from pathlib import Path

# 서버 코드와 동일하게
import cv2
import mediapipe as mp

# MediaPipe 초기화
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(
    static_image_mode=True,
    model_complexity=1,
    enable_segmentation=False,
    min_detection_confidence=0.5,
)

def normalize_landmarks(landmarks: np.ndarray) -> np.ndarray:
    """정규화 (train_gcn_cnn.py와 동일)"""
    HIP_INDICES = (23, 24)
    USED_LANDMARK_INDICES = list(range(11, 33))

    coords = landmarks[..., :2]
    pelvis = (coords[:, HIP_INDICES[0], :] + coords[:, HIP_INDICES[1], :]) / 2.0
    coords = coords - pelvis[:, None, :]

    body_coords = coords[:, USED_LANDMARK_INDICES, :]
    max_range = np.max(np.linalg.norm(body_coords, axis=-1, ord=2))
    if max_range < 1e-6:
        max_range = 1.0
    body_coords = body_coords / max_range

    return body_coords.astype(np.float32)

# 테스트 시퀀스
test_dir = Path("app/brandnewTrain/extracted_data/JSY/ELBOW")
frames = []

for i in range(1, 9):
    img_path = test_dir / f"elbow_seq001_frame{i}.jpg"

    # 테스트 스크립트처럼 Base64 인코딩
    with open(img_path, "rb") as f:
        img_data = f.read()
        b64 = base64.b64encode(img_data).decode("utf-8")
        frames.append(b64)

print("\n" + "=" * 80)
print("🧪 서버 입력 디버깅: ELBOW seq001")
print("=" * 80)

# 서버 코드와 동일하게 처리
raw_landmarks_list = []

for idx, encoded in enumerate(frames, 1):
    # Base64 디코딩
    image_data = base64.b64decode(encoded)

    # cv2로 디코딩 (새 코드)
    nparr = np.frombuffer(image_data, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

    # MediaPipe 추출
    results = pose.process(image_rgb)

    if not results.pose_landmarks:
        print(f"⚠️  Frame {idx}: Pose 추출 실패!")
        continue

    # Landmarks 추출
    landmarks = results.pose_landmarks.landmark
    all_coords = np.array(
        [(lm.x, lm.y) for lm in landmarks],
        dtype=np.float32
    )

    raw_landmarks_list.append(all_coords)
    print(f"✅ Frame {idx}: Pose 추출 성공, shape={all_coords.shape}")

print(f"\n총 유효 프레임: {len(raw_landmarks_list)}개 / 8개")

if len(raw_landmarks_list) < 5:
    print("❌ 유효 프레임 부족!")
else:
    # 정규화
    raw_sequence = np.stack(raw_landmarks_list, axis=0)
    print(f"Raw sequence shape: {raw_sequence.shape}")

    normalized = normalize_landmarks(raw_sequence)
    print(f"Normalized shape: {normalized.shape}")

    # .npz와 비교
    npz_path = Path("app/brandnewTrain/pose_sequences_from_images/JSY/ELBOW/elbow_seq001.npz")
    npz_data = np.load(npz_path)
    npz_landmarks = npz_data["landmarks"]
    npz_normalized = normalize_landmarks(npz_landmarks)

    print(f"\nNPZ normalized shape: {npz_normalized.shape}")

    # 차이 계산
    diff = np.abs(normalized - npz_normalized).mean()
    max_diff = np.abs(normalized - npz_normalized).max()

    print(f"\n서버 입력 vs NPZ 차이:")
    print(f"  평균 차이: {diff:.6f}")
    print(f"  최대 차이: {max_diff:.6f}")

    if max_diff > 0.01:
        print("  ⚠️  큰 차이 발견! 입력이 다릅니다!")
    else:
        print("  ✅ 거의 동일합니다")

print("=" * 80 + "\n")
