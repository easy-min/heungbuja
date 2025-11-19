"""
extracted_data의 이미지로부터 pose_sequences .npz 파일 생성

문제: 학습 데이터(.npz)와 추론 데이터(이미지)의 출처가 달라서
      모델이 학습 데이터에만 과적합됨

해결: extracted_data 이미지 → pose sequences → .npz 생성 → 재학습
"""

import glob
import os
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from tqdm import tqdm


# MediaPipe Pose 초기화
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(
    static_image_mode=True,
    model_complexity=1,
    enable_segmentation=False,
    min_detection_confidence=0.5,
)

ACTIONS = ["CLAP", "ELBOW", "STRETCH", "TILT", "EXIT", "UNDERARM", "STAY"]
EXTRACTED_DATA_DIR = Path("app/brandnewTrain/extracted_data")
OUTPUT_DIR = Path("app/brandnewTrain/pose_sequences_from_images")


def extract_pose_from_image(image_path):
    """이미지에서 pose landmarks 추출"""
    image = cv2.imread(str(image_path))
    if image is None:
        return None

    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    results = pose.process(image_rgb)

    if not results.pose_landmarks:
        return None

    # 33개 landmarks (x, y, z, visibility)
    landmarks = []
    for lm in results.pose_landmarks.landmark:
        landmarks.append([lm.x, lm.y, lm.z, lm.visibility])

    return np.array(landmarks, dtype=np.float32)  # (33, 4)


def process_sequence(sequence_images):
    """8개 프레임의 시퀀스를 처리"""
    landmarks_list = []

    for img_path in sequence_images:
        landmarks = extract_pose_from_image(img_path)
        if landmarks is None:
            return None
        landmarks_list.append(landmarks)

    # (8, 33, 4)
    return np.stack(landmarks_list, axis=0)


def get_sequence_dict(action_folder):
    """동작 폴더에서 시퀀스별로 이미지 그룹화"""
    all_images = glob.glob(os.path.join(action_folder, "*.jpg"))
    all_images = [f for f in all_images if "_backup" not in f]

    # 시퀀스별로 그룹화
    sequences = {}
    for img_path in all_images:
        filename = os.path.basename(img_path)
        # 예: clap_seq001_frame1.jpg -> clap_seq001
        parts = filename.split("_frame")
        if len(parts) == 2:
            seq_name = parts[0]
            if seq_name not in sequences:
                sequences[seq_name] = []
            sequences[seq_name].append(img_path)

    # 각 시퀀스를 frame 순서대로 정렬
    for seq_name in sequences:
        sequences[seq_name] = sorted(sequences[seq_name])

    return sequences


def main():
    print("\n" + "=" * 80)
    print("📸 extracted_data 이미지로부터 .npz 파일 생성")
    print("=" * 80 + "\n")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    total_sequences = 0
    failed_sequences = 0

    # 모든 사람 폴더 순회
    person_dirs = sorted([d for d in EXTRACTED_DATA_DIR.iterdir() if d.is_dir()])

    for person_dir in person_dirs:
        person = person_dir.name
        print(f"\n👤 {person}")

        person_output_dir = OUTPUT_DIR / person
        person_output_dir.mkdir(parents=True, exist_ok=True)

        # 모든 동작 폴더 순회
        for action in ACTIONS:
            action_dir = person_dir / action
            if not action_dir.exists():
                continue

            action_output_dir = person_output_dir / action
            action_output_dir.mkdir(parents=True, exist_ok=True)

            # 시퀀스별로 이미지 그룹화
            sequences = get_sequence_dict(str(action_dir))

            if not sequences:
                continue

            print(f"   {action}: {len(sequences)}개 시퀀스", end=" ")

            success = 0
            fail = 0

            for seq_name, image_paths in sequences.items():
                if len(image_paths) != 8:
                    fail += 1
                    continue

                # Pose 추출
                landmarks = process_sequence(image_paths)

                if landmarks is None:
                    fail += 1
                    continue

                # .npz 저장
                output_path = action_output_dir / f"{seq_name}.npz"
                np.savez_compressed(
                    output_path,
                    landmarks=landmarks,
                    person=person,
                    action=action,
                    source="extracted_images"
                )

                success += 1
                total_sequences += 1

            if fail > 0:
                print(f"(✅ {success}개, ❌ {fail}개 실패)")
            else:
                print(f"(✅ {success}개)")

            failed_sequences += fail

    print("\n" + "=" * 80)
    print("📊 생성 완료")
    print("=" * 80)
    print(f"   총 시퀀스: {total_sequences}개")
    print(f"   실패: {failed_sequences}개")
    print(f"   출력 디렉토리: {OUTPUT_DIR}")
    print("=" * 80 + "\n")

    print("✅ 다음 단계:")
    print("   1. 생성된 .npz 파일 확인:")
    print(f"      ls {OUTPUT_DIR}/JSY/CLAP")
    print("   2. 재학습:")
    print(f"      python app/brandnewTrain/train_gcn_cnn.py \\")
    print(f"        --data_dir ./{OUTPUT_DIR} \\")
    print(f"        --epochs 150 \\")
    print(f"        --save_name brandnew_from_images_v1.pt")
    print()


if __name__ == "__main__":
    main()
