"""
Brandnew 모델 배포 서버 테스트 - extracted_data 이미지 사용

사용법:
    python test_brandnew_server.py  # 각 동작별 1개씩 샘플 테스트
    python test_brandnew_server.py --samples 3  # 각 동작별 3개씩 테스트
    python test_brandnew_server.py --person JSY --action CLAP  # 특정 동작만
    python test_brandnew_server.py --url http://localhost:8000/api/ai/brandnew/analyze  # 로컬 서버
"""

import argparse
import base64
import glob
import os
import time
from pathlib import Path

import requests


ACTION_CODE_MAP = {
    "CLAP": 1,
    "ELBOW": 2,
    "STRETCH": 4,
    "TILT": 5,
    "EXIT": 6,
    "UNDERARM": 7,
    "STAY": 9,
}


def load_image_sequence(action_folder, sequence_name):
    """
    동작 폴더에서 특정 시퀀스의 8개 프레임 로드 및 Base64 인코딩

    예: action_folder = "app/brandnewTrain/extracted_data/JSY/CLAP"
        sequence_name = "clap_seq001"
    """
    frames = []

    for i in range(1, 9):
        # 파일명 패턴: clap_seq001_frame1.jpg
        frame_path = os.path.join(action_folder, f"{sequence_name}_frame{i}.jpg")

        if not os.path.exists(frame_path):
            return None

        with open(frame_path, "rb") as f:
            img_data = f.read()
            b64 = base64.b64encode(img_data).decode("utf-8")
            frames.append(b64)

    return frames


def send_to_server(frames, action_code, action_name, api_url):
    """서버로 프레임 전송 및 결과 수신"""
    payload = {
        "actionCode": action_code,
        "actionName": action_name,
        "frameCount": len(frames),
        "frames": frames,
    }

    try:
        response = requests.post(
            api_url,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=60,
            verify=True,
        )

        if response.status_code == 200:
            return response.json()
        else:
            error = response.json().get("detail", "Unknown error")
            print(f"   ❌ HTTP {response.status_code}: {error}")
            return None

    except requests.exceptions.Timeout:
        print(f"   ❌ 타임아웃!")
        return None
    except Exception as e:
        print(f"   ❌ 에러: {e}")
        return None


def test_single_sequence(action_folder, sequence_name, action_name, api_url):
    """단일 시퀀스 테스트"""
    # 프레임 로드
    frames = load_image_sequence(action_folder, sequence_name)
    if not frames:
        print(f"   ⚠️  프레임 로드 실패")
        return None

    # 서버로 전송
    action_code = ACTION_CODE_MAP.get(action_name)

    result = send_to_server(frames, action_code, action_name, api_url)
    if not result:
        return None

    predicted = result.get("predictedLabel", "N/A")
    confidence = result.get("confidence", 0)
    judgment = result.get("judgment", 0)
    target_prob = result.get("targetProbability", 0)

    return {
        "sequence": sequence_name,
        "action": action_name,
        "predicted": predicted,
        "confidence": confidence,
        "judgment": judgment,
        "target_prob": target_prob,
        "correct": predicted == action_name,
    }


def get_sequence_names(action_folder, action_name):
    """
    동작 폴더에서 시퀀스 이름 목록 추출

    예: clap_seq001_frame1.jpg -> clap_seq001
    """
    if not os.path.exists(action_folder):
        return []

    # 모든 jpg 파일 찾기
    all_files = glob.glob(os.path.join(action_folder, "*.jpg"))

    # _backup 제외
    all_files = [f for f in all_files if "_backup" not in f]

    # 시퀀스 이름 추출 (중복 제거)
    sequence_names = set()
    for file_path in all_files:
        filename = os.path.basename(file_path)
        # 예: clap_seq001_frame1.jpg -> clap_seq001
        parts = filename.split("_frame")
        if len(parts) == 2:
            sequence_names.add(parts[0])

    return sorted(list(sequence_names))


def test_person_action(person, action, api_url, max_samples=None):
    """특정 사람의 특정 동작 테스트 (샘플 개수 제한)"""
    base_path = Path("app/brandnewTrain/extracted_data")
    action_path = base_path / person / action

    if not action_path.exists():
        print(f"❌ 경로를 찾을 수 없습니다: {action_path}")
        return []

    # 시퀀스 이름 목록 가져오기
    sequence_names = get_sequence_names(str(action_path), action.lower())

    if not sequence_names:
        print(f"⚠️  {person}/{action}: 시퀀스 없음")
        return []

    # 샘플 개수 제한
    if max_samples:
        sequence_names = sequence_names[:max_samples]

    print(f"\n📂 {person}/{action} (테스트: {len(sequence_names)}개)")

    results = []

    for seq_name in sequence_names:
        print(f"   {seq_name}...", end=" ", flush=True)

        result = test_single_sequence(str(action_path), seq_name, action, api_url)
        if result:
            results.append(result)

            emoji = "✅" if result["correct"] else "❌"
            print(f"{emoji} {result['predicted']} "
                  f"({result['confidence']*100:.1f}%, {result['judgment']}점)")
        else:
            print("❌ 실패")

        # 서버 부하 방지
        time.sleep(0.3)

    return results


def test_samples(api_url, samples_per_action=1):
    """각 동작별 샘플 테스트"""
    base_path = Path("app/brandnewTrain/extracted_data")

    # 첫 번째 사람 선택 (JSY 우선)
    person_dirs = sorted([d for d in base_path.iterdir() if d.is_dir()])
    if "JSY" in [d.name for d in person_dirs]:
        test_person = "JSY"
    else:
        test_person = person_dirs[0].name

    print("\n" + "=" * 80)
    print(f"🆕 Brandnew 모델 배포 서버 테스트")
    print("=" * 80)
    print(f"🌐 서버: {api_url}")
    print(f"👤 테스트 대상: {test_person}")
    print(f"📊 각 동작별 {samples_per_action}개 샘플")
    print("=" * 80)

    all_results = []

    # 모든 동작 테스트
    for action in sorted(ACTION_CODE_MAP.keys()):
        results = test_person_action(test_person, action, api_url, samples_per_action)
        all_results.extend(results)

    # 통계
    if all_results:
        print("\n" + "=" * 80)
        print("📊 테스트 결과 요약")
        print("=" * 80)

        total = len(all_results)
        correct = sum(1 for r in all_results if r["correct"])
        accuracy = correct / total * 100 if total > 0 else 0

        avg_confidence = sum(r["confidence"] for r in all_results) / total * 100 if total > 0 else 0
        avg_judgment = sum(r["judgment"] for r in all_results) / total if total > 0 else 0

        print(f"   전체 시퀀스: {total}개")
        print(f"   정확도: {correct}/{total} ({accuracy:.1f}%)")
        print(f"   평균 신뢰도: {avg_confidence:.1f}%")
        print(f"   평균 점수: {avg_judgment:.2f}점")

        # 동작별 결과
        print(f"\n   동작별 정확도:")
        for action in sorted(ACTION_CODE_MAP.keys()):
            action_results = [r for r in all_results if r["action"] == action]
            if action_results:
                action_correct = sum(1 for r in action_results if r["correct"])
                action_total = len(action_results)
                action_acc = action_correct / action_total * 100 if action_total > 0 else 0
                action_avg_conf = sum(r["confidence"] for r in action_results) / action_total * 100
                action_avg_score = sum(r["judgment"] for r in action_results) / action_total

                emoji = "✅" if action_correct == action_total else "⚠️" if action_correct > 0 else "❌"
                print(f"   {emoji} {action:10s}: {action_correct}/{action_total} "
                      f"({action_acc:.0f}% 정확도, {action_avg_conf:.0f}% 신뢰도, "
                      f"{action_avg_score:.1f}점)")

        # 점수 분포
        score_dist = {0: 0, 1: 0, 2: 0, 3: 0}
        for r in all_results:
            score_dist[r["judgment"]] += 1

        print(f"\n   점수 분포:")
        for score in [3, 2, 1, 0]:
            count = score_dist[score]
            if count > 0:
                pct = count / total * 100
                bar = "█" * max(1, int(pct / 3))
                print(f"   {score}점: {bar} ({count}개, {pct:.1f}%)")

        print("=" * 80 + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--url",
        default="https://heungbuja.site/motion/api/ai/brandnew/analyze",
        help="API URL (기본값: 배포 서버)"
    )
    parser.add_argument("--person", type=str, help="테스트할 사람 (예: JSY)")
    parser.add_argument("--action", type=str, help="테스트할 동작 (예: CLAP)")
    parser.add_argument("--samples", type=int, default=1, help="각 동작별 테스트할 샘플 개수 (기본값: 1)")
    args = parser.parse_args()

    api_url = args.url
    server_type = "배포 서버" if "heungbuja.site" in api_url else "로컬 서버"

    if args.person and args.action:
        # 특정 동작만 테스트
        print("\n" + "=" * 80)
        print(f"🆕 Brandnew 모델 {server_type} 테스트")
        print("=" * 80)
        print(f"🌐 서버: {api_url}")
        print("=" * 80)

        results = test_person_action(args.person, args.action, api_url, args.samples)

        if results:
            correct = sum(1 for r in results if r["correct"])
            total = len(results)
            avg_conf = sum(r["confidence"] for r in results) / total * 100
            avg_score = sum(r["judgment"] for r in results) / total

            print(f"\n✅ 정확도: {correct}/{total} ({correct/total*100:.1f}%)")
            print(f"📊 평균 신뢰도: {avg_conf:.1f}%")
            print(f"⭐ 평균 점수: {avg_score:.2f}점\n")
    else:
        # 샘플 테스트
        test_samples(api_url, args.samples)


if __name__ == "__main__":
    main()
