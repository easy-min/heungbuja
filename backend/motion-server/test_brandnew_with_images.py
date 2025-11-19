"""
Brandnew 모델 테스트 - extracted_data 이미지 사용

사용법:
    python test_brandnew_with_images.py
    python test_brandnew_with_images.py --person JSY --action CLAP
    python test_brandnew_with_images.py --all  # 모든 시퀀스 테스트
"""

import argparse
import base64
import glob
import os
from pathlib import Path

import torch
from app.services.brandnew_inference import get_brandnew_inference_service


ACTION_CODE_MAP = {
    "CLAP": 1,
    "ELBOW": 2,
    "STRETCH": 4,
    "TILT": 5,
    "EXIT": 6,
    "UNDERARM": 7,
    "STAY": 9,
}


def load_image_sequence(sequence_path):
    """시퀀스 폴더에서 8개 프레임 로드 및 Base64 인코딩"""
    image_files = sorted(glob.glob(os.path.join(sequence_path, "*.jpg")))

    # _backup 파일 제외
    image_files = [f for f in image_files if "_backup" not in f]

    if len(image_files) < 8:
        print(f"   ⚠️  프레임 부족: {len(image_files)}개 (필요: 8개)")
        return None

    # 정확히 8개만 사용 (frame1~frame8)
    frames = []
    for i in range(1, 9):
        frame_file = [f for f in image_files if f"frame{i}.jpg" in f]
        if not frame_file:
            print(f"   ⚠️  frame{i}.jpg 찾을 수 없음")
            return None

        with open(frame_file[0], "rb") as f:
            img_data = f.read()
            b64 = base64.b64encode(img_data).decode("utf-8")
            frames.append(b64)

    return frames


def test_single_sequence(service, sequence_path, action_name):
    """단일 시퀀스 테스트"""
    sequence_name = os.path.basename(sequence_path)

    # 프레임 로드
    frames = load_image_sequence(sequence_path)
    if not frames:
        return None

    # 추론
    action_code = ACTION_CODE_MAP.get(action_name)

    try:
        result = service.predict(
            frames=frames,
            target_action_name=action_name,
            target_action_code=action_code,
        )

        return {
            "sequence": sequence_name,
            "action": action_name,
            "predicted": result.predicted_label,
            "confidence": result.confidence,
            "judgment": result.judgment,
            "target_prob": result.target_probability,
            "correct": result.predicted_label == action_name,
        }

    except Exception as e:
        print(f"   ❌ 추론 실패: {e}")
        return None


def test_person_action(service, person, action):
    """특정 사람의 특정 동작 모든 시퀀스 테스트"""
    base_path = Path("app/brandnewTrain/extracted_data")
    action_path = base_path / person / action

    if not action_path.exists():
        print(f"❌ 경로를 찾을 수 없습니다: {action_path}")
        return

    # 시퀀스 폴더 찾기 (예: clap_seq001, clap_seq002, ...)
    sequence_dirs = sorted([d for d in action_path.iterdir() if d.is_dir()])

    if not sequence_dirs:
        print(f"❌ 시퀀스 폴더가 없습니다: {action_path}")
        return

    print("\n" + "=" * 80)
    print(f"🧪 테스트: {person} - {action} (총 {len(sequence_dirs)}개 시퀀스)")
    print("=" * 80)

    results = []

    for seq_dir in sequence_dirs:
        seq_name = seq_dir.name
        print(f"\n📂 {seq_name}:")

        result = test_single_sequence(service, str(seq_dir), action)
        if result:
            results.append(result)

            emoji = "✅" if result["correct"] else "❌"
            print(f"   {emoji} 예측: {result['predicted']} (신뢰도: {result['confidence']*100:.1f}%)")
            print(f"   📊 목표 확률: {result['target_prob']*100:.1f}%")
            print(f"   ⭐ 점수: {result['judgment']}점")

    # 통계
    if results:
        print("\n" + "-" * 80)
        print(f"📊 통계 요약 ({person} - {action}):")
        print("-" * 80)

        correct = sum(1 for r in results if r["correct"])
        total = len(results)
        accuracy = correct / total * 100

        avg_confidence = sum(r["confidence"] for r in results) / total * 100
        avg_target_prob = sum(r["target_prob"] for r in results) / total * 100
        avg_judgment = sum(r["judgment"] for r in results) / total

        print(f"   정확도: {correct}/{total} ({accuracy:.1f}%)")
        print(f"   평균 신뢰도: {avg_confidence:.1f}%")
        print(f"   평균 목표 확률: {avg_target_prob:.1f}%")
        print(f"   평균 점수: {avg_judgment:.2f}점")

        # 점수 분포
        score_dist = {0: 0, 1: 0, 2: 0, 3: 0}
        for r in results:
            score_dist[r["judgment"]] += 1

        print(f"\n   점수 분포:")
        for score in [3, 2, 1, 0]:
            count = score_dist[score]
            if count > 0:
                bar = "█" * count
                pct = count / total * 100
                print(f"   {score}점: {bar} ({count}개, {pct:.1f}%)")

        print("=" * 80 + "\n")


def test_all(service):
    """모든 사람, 모든 동작 테스트"""
    base_path = Path("app/brandnewTrain/extracted_data")

    all_results = []

    # 모든 사람 폴더
    person_dirs = sorted([d for d in base_path.iterdir() if d.is_dir()])

    for person_dir in person_dirs:
        person = person_dir.name

        # 모든 동작 폴더
        action_dirs = sorted([d for d in person_dir.iterdir() if d.is_dir()])

        for action_dir in action_dirs:
            action = action_dir.name

            # 시퀀스 폴더들
            sequence_dirs = sorted([d for d in action_dir.iterdir() if d.is_dir()])

            for seq_dir in sequence_dirs:
                result = test_single_sequence(service, str(seq_dir), action)
                if result:
                    result["person"] = person
                    all_results.append(result)

                    emoji = "✅" if result["correct"] else "❌"
                    print(f"{emoji} {person}/{action}/{result['sequence']}: "
                          f"{result['predicted']} ({result['confidence']*100:.1f}%, "
                          f"{result['judgment']}점)")

    # 전체 통계
    if all_results:
        print("\n" + "=" * 80)
        print("📊 전체 통계:")
        print("=" * 80)

        total = len(all_results)
        correct = sum(1 for r in all_results if r["correct"])
        accuracy = correct / total * 100

        avg_confidence = sum(r["confidence"] for r in all_results) / total * 100
        avg_judgment = sum(r["judgment"] for r in all_results) / total

        print(f"   전체 시퀀스: {total}개")
        print(f"   정확도: {correct}/{total} ({accuracy:.1f}%)")
        print(f"   평균 신뢰도: {avg_confidence:.1f}%")
        print(f"   평균 점수: {avg_judgment:.2f}점")

        # 동작별 정확도
        print(f"\n   동작별 정확도:")
        for action in sorted(ACTION_CODE_MAP.keys()):
            action_results = [r for r in all_results if r["action"] == action]
            if action_results:
                action_correct = sum(1 for r in action_results if r["correct"])
                action_total = len(action_results)
                action_acc = action_correct / action_total * 100
                print(f"   {action:10s}: {action_correct}/{action_total} ({action_acc:.1f}%)")

        # 점수 분포
        score_dist = {0: 0, 1: 0, 2: 0, 3: 0}
        for r in all_results:
            score_dist[r["judgment"]] += 1

        print(f"\n   점수 분포:")
        for score in [3, 2, 1, 0]:
            count = score_dist[score]
            pct = count / total * 100
            bar = "█" * int(pct / 2)
            print(f"   {score}점: {bar} ({count}개, {pct:.1f}%)")

        print("=" * 80 + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--person", type=str, help="테스트할 사람 (예: JSY, KSM)")
    parser.add_argument("--action", type=str, help="테스트할 동작 (예: CLAP, STAY)")
    parser.add_argument("--all", action="store_true", help="모든 시퀀스 테스트")
    args = parser.parse_args()

    print("\n🆕 Brandnew 모델 로딩...")
    service = get_brandnew_inference_service()
    print("✅ 모델 로드 완료\n")

    if args.all:
        test_all(service)
    elif args.person and args.action:
        test_person_action(service, args.person, args.action)
    else:
        # 기본값: JSY의 CLAP 테스트
        print("💡 사용법: --person <이름> --action <동작> 또는 --all")
        print("   예시: python test_brandnew_with_images.py --person JSY --action CLAP")
        print("   예시: python test_brandnew_with_images.py --all\n")
        print("기본 테스트 실행: JSY - CLAP\n")
        test_person_action(service, "JSY", "CLAP")


if __name__ == "__main__":
    main()
