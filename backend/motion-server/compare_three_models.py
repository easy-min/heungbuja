"""
3개 모델 성능 비교 스크립트

1. 팀원 모델 (거리 기반): /api/pose-sequences/classify
2. 기존 GCN 모델: /api/ai/analyze
3. 우리 모델 (개선): /api/ai/brandnew/analyze

동일한 테스트 데이터로 정확도, 신뢰도, 처리 시간 비교

사용법:
    python compare_three_models.py --samples 3
    python compare_three_models.py --samples 5 --url https://heungbuja.site/motion
    python compare_three_models.py --person JSY --action ELBOW --samples 3
"""

import argparse
import base64
import glob
import os
import time
from collections import defaultdict
from pathlib import Path
from typing import Optional

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
    """동작 폴더에서 특정 시퀀스의 8개 프레임 로드"""
    frames = []
    for i in range(1, 9):
        frame_path = os.path.join(action_folder, f"{sequence_name}_frame{i}.jpg")
        if not os.path.exists(frame_path):
            return None
        with open(frame_path, "rb") as f:
            img_data = f.read()
            b64 = base64.b64encode(img_data).decode("utf-8")
            frames.append(b64)
    return frames


def call_pose_sequence_model(api_url, frames, action_code, action_name):
    """팀원 모델 (거리 기반) API 호출"""
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
            result = response.json()
            # 거리 기반 모델은 judgment만 반환 (predictedLabel, confidence 없음)
            return {
                "judgment": result.get("judgment", 0),
                "actionCode": result.get("actionCode"),
            }
        else:
            error = response.json().get("detail", "Unknown error")
            return {"error": f"HTTP {response.status_code}: {error}"}

    except requests.exceptions.Timeout:
        return {"error": "Timeout"}
    except Exception as e:
        return {"error": str(e)}


def call_gcn_model(api_url, frames, action_code, action_name):
    """GCN 모델 API 호출 (기존 또는 우리 모델)"""
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
            return {"error": f"HTTP {response.status_code}: {error}"}

    except requests.exceptions.Timeout:
        return {"error": "Timeout"}
    except Exception as e:
        return {"error": str(e)}


def compare_single_sequence(base_url, action_folder, sequence_name, action_name):
    """단일 시퀀스에 대해 3개 모델 비교"""
    frames = load_image_sequence(action_folder, sequence_name)
    if not frames:
        return None

    action_code = ACTION_CODE_MAP.get(action_name)

    # 1. 팀원 모델 (거리 기반)
    distance_url = f"{base_url}/api/pose-sequences/classify"
    distance_result = call_pose_sequence_model(distance_url, frames, action_code, action_name)

    # 2. 기존 GCN 모델
    old_gcn_url = f"{base_url}/api/ai/analyze"
    old_gcn_result = call_gcn_model(old_gcn_url, frames, action_code, action_name)

    # 3. 우리 모델 (개선)
    new_gcn_url = f"{base_url}/api/ai/brandnew/analyze"
    new_gcn_result = call_gcn_model(new_gcn_url, frames, action_code, action_name)

    # 서버 부하 방지
    time.sleep(0.3)

    return {
        "sequence": sequence_name,
        "action": action_name,
        "distance_model": distance_result,
        "old_gcn_model": old_gcn_result,
        "new_gcn_model": new_gcn_result,
    }


def get_sequence_names(action_folder, action_name):
    """동작 폴더에서 시퀀스 이름 목록 추출"""
    if not os.path.exists(action_folder):
        return []

    all_files = glob.glob(os.path.join(action_folder, "*.jpg"))
    all_files = [f for f in all_files if "_backup" not in f]

    sequence_names = set()
    for file_path in all_files:
        filename = os.path.basename(file_path)
        parts = filename.split("_frame")
        if len(parts) == 2:
            sequence_names.add(parts[0])

    return sorted(list(sequence_names))


def print_comparison_header():
    """비교 결과 헤더 출력"""
    print("\n" + "=" * 160)
    print(f"{'Sequence':<20} {'거리 기반 (팀원)':<45} {'기존 GCN (버그)':<45} {'우리 GCN (개선)':<45}")
    print(f"{'':20} {'Score':<45} {'Pred / Conf / Score / Time':<45} {'Pred / Conf / Score / Time':<45}")
    print("=" * 160)


def format_model_result(result, action, is_distance_model=False):
    """모델 결과 포맷팅"""
    if "error" in result:
        return f"❌ {result['error']}", False

    if is_distance_model:
        # 거리 기반 모델은 judgment만 있음
        score = result.get("judgment", 0)
        emoji = "✅" if score >= 2 else "⚠️" if score == 1 else "❌"
        return f"{emoji} Score: {score}점", score >= 2
    else:
        # GCN 모델은 전체 정보 있음
        pred = result.get("predictedLabel", "N/A")
        conf = result.get("confidence", 0) * 100
        score = result.get("judgment", 0)
        inf_time = result.get("inferenceTimeMs", 0)
        emoji = "✅" if pred == action else "❌"
        correct = pred == action
        return f"{emoji} {pred:8s} / {conf:5.1f}% / {score}점 / {inf_time:5.1f}ms", correct


def print_comparison_row(result):
    """비교 결과 한 줄 출력"""
    seq = result["sequence"]
    action = result["action"]

    # 거리 기반 모델
    d_str, d_correct = format_model_result(result["distance_model"], action, is_distance_model=True)

    # 기존 GCN
    o_str, o_correct = format_model_result(result["old_gcn_model"], action)

    # 우리 GCN
    n_str, n_correct = format_model_result(result["new_gcn_model"], action)

    print(f"{seq:<20} {d_str:<45} {o_str:<45} {n_str:<45}")

    return d_correct, o_correct, n_correct


def print_summary(all_results):
    """전체 통계 출력"""
    print("\n" + "=" * 160)
    print("📊 전체 비교 결과")
    print("=" * 160)

    distance_stats = defaultdict(lambda: {"correct": 0, "total": 0, "score_sum": 0})
    old_gcn_stats = defaultdict(lambda: {"correct": 0, "total": 0, "conf_sum": 0, "score_sum": 0, "time_sum": 0})
    new_gcn_stats = defaultdict(lambda: {"correct": 0, "total": 0, "conf_sum": 0, "score_sum": 0, "time_sum": 0})

    distance_total_correct = 0
    old_gcn_total_correct = 0
    new_gcn_total_correct = 0
    total_samples = 0

    for result in all_results:
        action = result["action"]

        # 거리 기반 모델
        d = result["distance_model"]
        if "error" not in d:
            score = d.get("judgment", 0)
            is_correct = score >= 2  # 2점 이상이면 성공으로 간주
            distance_stats[action]["total"] += 1
            distance_stats[action]["score_sum"] += score
            if is_correct:
                distance_stats[action]["correct"] += 1
                distance_total_correct += 1

        # 기존 GCN
        o = result["old_gcn_model"]
        if "error" not in o:
            is_correct = o.get("predictedLabel") == action
            old_gcn_stats[action]["total"] += 1
            old_gcn_stats[action]["conf_sum"] += o.get("confidence", 0) * 100
            old_gcn_stats[action]["score_sum"] += o.get("judgment", 0)
            old_gcn_stats[action]["time_sum"] += o.get("inferenceTimeMs", 0)
            if is_correct:
                old_gcn_stats[action]["correct"] += 1
                old_gcn_total_correct += 1

        # 우리 GCN
        n = result["new_gcn_model"]
        if "error" not in n:
            is_correct = n.get("predictedLabel") == action
            new_gcn_stats[action]["total"] += 1
            new_gcn_stats[action]["conf_sum"] += n.get("confidence", 0) * 100
            new_gcn_stats[action]["score_sum"] += n.get("judgment", 0)
            new_gcn_stats[action]["time_sum"] += n.get("inferenceTimeMs", 0)
            if is_correct:
                new_gcn_stats[action]["correct"] += 1
                new_gcn_total_correct += 1

        total_samples += 1

    # 전체 정확도
    print(f"\n📌 전체 정확도:")
    distance_acc = distance_total_correct / total_samples * 100 if total_samples > 0 else 0
    old_gcn_acc = old_gcn_total_correct / total_samples * 100 if total_samples > 0 else 0
    new_gcn_acc = new_gcn_total_correct / total_samples * 100 if total_samples > 0 else 0

    print(f"   거리 기반 (팀원):  {distance_total_correct}/{total_samples} ({distance_acc:.1f}%)")
    print(f"   기존 GCN (버그):   {old_gcn_total_correct}/{total_samples} ({old_gcn_acc:.1f}%)")
    print(f"   우리 GCN (개선):   {new_gcn_total_correct}/{total_samples} ({new_gcn_acc:.1f}%)")

    best_acc = max(distance_acc, old_gcn_acc, new_gcn_acc)
    if best_acc == new_gcn_acc:
        winner = "우리 GCN (개선)"
    elif best_acc == old_gcn_acc:
        winner = "기존 GCN"
    else:
        winner = "거리 기반 (팀원)"

    print(f"   🏆 정확도 승자: {winner} ({best_acc:.1f}%)")

    # 동작별 상세 비교
    print(f"\n📌 동작별 성능 비교:")
    print(f"{'Action':<12} {'거리 Acc':>15} {'기존 Acc':>15} {'우리 Acc':>15} {'거리 Score':>15} {'기존 Conf':>15} {'우리 Conf':>15}")
    print("-" * 160)

    for action in sorted(ACTION_CODE_MAP.keys()):
        d = distance_stats[action]
        o = old_gcn_stats[action]
        n = new_gcn_stats[action]

        if d["total"] == 0 and o["total"] == 0 and n["total"] == 0:
            continue

        d_acc = d["correct"] / d["total"] * 100 if d["total"] > 0 else 0
        o_acc = o["correct"] / o["total"] * 100 if o["total"] > 0 else 0
        n_acc = n["correct"] / n["total"] * 100 if n["total"] > 0 else 0

        d_score = d["score_sum"] / d["total"] if d["total"] > 0 else 0
        o_conf = o["conf_sum"] / o["total"] if o["total"] > 0 else 0
        n_conf = n["conf_sum"] / n["total"] if n["total"] > 0 else 0

        d_acc_str = f"{d['correct']}/{d['total']} ({d_acc:.0f}%)"
        o_acc_str = f"{o['correct']}/{o['total']} ({o_acc:.0f}%)"
        n_acc_str = f"{n['correct']}/{n['total']} ({n_acc:.0f}%)"

        print(f"{action:<12} {d_acc_str:>15} {o_acc_str:>15} {n_acc_str:>15} {d_score:>14.1f}점 {o_conf:>14.1f}% {n_conf:>14.1f}%")

    # 처리 시간 비교 (GCN 모델만)
    print(f"\n📌 평균 추론 시간 (GCN 모델만):")
    old_time_total = sum(s["time_sum"] for s in old_gcn_stats.values())
    new_time_total = sum(s["time_sum"] for s in new_gcn_stats.values())
    old_count = sum(s["total"] for s in old_gcn_stats.values())
    new_count = sum(s["total"] for s in new_gcn_stats.values())

    old_avg_time = old_time_total / old_count if old_count > 0 else 0
    new_avg_time = new_time_total / new_count if new_count > 0 else 0

    print(f"   기존 GCN:  {old_avg_time:.1f}ms")
    print(f"   우리 GCN:  {new_avg_time:.1f}ms")

    if new_avg_time < old_avg_time:
        speedup = ((old_avg_time - new_avg_time) / old_avg_time * 100) if old_avg_time > 0 else 0
        print(f"   ⚡ 우리 모델이 {speedup:.1f}% 더 빠름!")

    print("=" * 160 + "\n")


def test_all_actions(base_url, samples_per_action=1):
    """모든 동작에 대해 테스트"""
    base_path = Path("app/brandnewTrain/extracted_data")

    # 첫 번째 사람 선택
    person_dirs = sorted([d for d in base_path.iterdir() if d.is_dir()])
    if "JSY" in [d.name for d in person_dirs]:
        test_person = "JSY"
    else:
        test_person = person_dirs[0].name

    print("\n" + "=" * 160)
    print(f"🔬 3개 모델 성능 비교 테스트")
    print("=" * 160)
    print(f"🌐 서버: {base_url}")
    print(f"👤 테스트 대상: {test_person}")
    print(f"📊 각 동작별 {samples_per_action}개 샘플")
    print("\n모델:")
    print("   1️⃣  거리 기반 (팀원):  /api/pose-sequences/classify")
    print("   2️⃣  기존 GCN (버그):   /api/ai/analyze")
    print("   3️⃣  우리 GCN (개선):   /api/ai/brandnew/analyze")
    print("=" * 160)

    all_results = []

    print_comparison_header()

    for action in sorted(ACTION_CODE_MAP.keys()):
        action_path = base_path / test_person / action

        if not action_path.exists():
            continue

        sequence_names = get_sequence_names(str(action_path), action.lower())
        if not sequence_names:
            continue

        sequence_names = sequence_names[:samples_per_action]

        for seq_name in sequence_names:
            result = compare_single_sequence(base_url, str(action_path), seq_name, action)
            if result:
                all_results.append(result)
                print_comparison_row(result)

    if all_results:
        print_summary(all_results)


def main():
    parser = argparse.ArgumentParser(description="3개 모델 성능 비교")
    parser.add_argument(
        "--url",
        default="https://heungbuja.site/motion",
        help="서버 base URL (기본값: 배포 서버)"
    )
    parser.add_argument("--person", type=str, help="테스트할 사람 (예: JSY)")
    parser.add_argument("--action", type=str, help="테스트할 동작 (예: CLAP)")
    parser.add_argument("--samples", type=int, default=1, help="각 동작별 테스트할 샘플 개수 (기본값: 1)")
    args = parser.parse_args()

    base_url = args.url.rstrip("/")

    if args.person and args.action:
        # 특정 동작만 테스트
        base_path = Path("app/brandnewTrain/extracted_data")
        action_path = base_path / args.person / args.action

        print("\n" + "=" * 160)
        print(f"🔬 3개 모델 성능 비교 테스트 - {args.person}/{args.action}")
        print("=" * 160)
        print(f"🌐 서버: {base_url}")

        sequence_names = get_sequence_names(str(action_path), args.action.lower())
        sequence_names = sequence_names[:args.samples]

        all_results = []
        print_comparison_header()

        for seq_name in sequence_names:
            result = compare_single_sequence(base_url, str(action_path), seq_name, args.action)
            if result:
                all_results.append(result)
                print_comparison_row(result)

        if all_results:
            print_summary(all_results)
    else:
        # 모든 동작 테스트
        test_all_actions(base_url, args.samples)


if __name__ == "__main__":
    main()
