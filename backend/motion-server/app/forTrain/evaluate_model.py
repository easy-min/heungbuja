"""
학습된 모델 종합 평가 스크립트

전처리된 데이터(pose_sequences)를 사용하여:
1. Confusion Matrix
2. 클래스별 성능 지표 (Accuracy, Precision, Recall, F1)
3. 확률 분포 분석
4. 점수 변환 시뮬레이션
5. STAY vs CLAP 구분 능력 분석

사용법:
    python evaluate_model.py --model checkpoints/gcn_cnn_best.pt --data_dir ./pose_sequences
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import torch
import torch.nn.functional as F
from sklearn.metrics import confusion_matrix, classification_report, precision_recall_fscore_support

# train_gcn_cnn.py에서 정의된 것들 import
from train_gcn_cnn import (
    SUPPORTED_ACTIONS,
    USED_LANDMARK_INDICES,
    GCNTemporalModel,
    build_adjacency,
    normalize_landmarks,
)


def load_model(model_path: Path, num_classes: int, device: str = "cuda") -> GCNTemporalModel:
    """학습된 모델 로드"""
    checkpoint = torch.load(model_path, map_location=device, weights_only=False)

    # 모델 구조 파라미터
    gcn_hidden_dims = checkpoint.get("args", {}).get("gcn_hidden_dims", [96, 192])
    temporal_channels = checkpoint.get("args", {}).get("temporal_channels", [192, 384])
    dropout = checkpoint.get("args", {}).get("dropout", 0.4)

    # 모델 생성
    input_dim = 2  # x, y 좌표
    adjacency = build_adjacency(USED_LANDMARK_INDICES)

    model = GCNTemporalModel(
        input_dim=input_dim,
        num_classes=num_classes,
        adjacency=adjacency,
        gcn_hidden_dims=gcn_hidden_dims,
        temporal_channels=temporal_channels,
        dropout=dropout,
    )

    # 가중치 로드
    model.load_state_dict(checkpoint["model_state_dict"])
    model.to(device)
    model.eval()

    return model


def load_all_samples(data_dir: Path, action_to_label: Dict[str, int], frames_per_sample: int = 8):
    """전처리된 데이터 전체 로드"""
    samples = []

    for person_dir in sorted(p for p in data_dir.iterdir() if p.is_dir()):
        person_name = person_dir.name.upper()

        for action_dir in sorted(p for p in person_dir.iterdir() if p.is_dir()):
            action_name = action_dir.name.upper()

            if action_name not in action_to_label:
                continue

            for npz_file in sorted(action_dir.glob("*.npz")):
                try:
                    with np.load(npz_file, allow_pickle=True) as data:
                        landmarks = data["landmarks"]

                        if landmarks.shape[0] != frames_per_sample:
                            continue

                        # 정규화
                        landmarks = normalize_landmarks(landmarks)

                        samples.append({
                            "data": landmarks,
                            "label": action_to_label[action_name],
                            "action": action_name,
                            "person": person_name,
                            "file": npz_file,
                        })
                except Exception as e:
                    print(f"⚠️  파일 로드 실패: {npz_file}, 오류: {e}")
                    continue

    return samples


def evaluate_model_comprehensive(
    model: GCNTemporalModel,
    samples: List[Dict],
    label_to_action: Dict[int, str],
    device: str = "cuda"
):
    """전체 샘플 평가 및 상세 분석"""

    all_labels = []
    all_preds = []
    all_probs = []  # 모든 클래스에 대한 확률
    all_max_probs = []  # 최고 확률값
    all_target_probs = []  # 목표 클래스 확률
    misclassified = []

    # 클래스별 확률 저장 (STAY vs CLAP 분석용)
    class_probs = defaultdict(list)

    print("🔍 모델 평가 중...")

    with torch.no_grad():
        for sample in samples:
            data = torch.from_numpy(sample["data"]).unsqueeze(0).to(device)  # (1, T, N, C)
            label = sample["label"]
            action = sample["action"]

            # 모델 예측 (logits)
            logits = model(data)

            # Softmax로 확률 변환
            probs = F.softmax(logits, dim=1).cpu().numpy()[0]  # (num_classes,)

            pred = int(np.argmax(probs))
            max_prob = float(np.max(probs))
            target_prob = float(probs[label])

            all_labels.append(label)
            all_preds.append(pred)
            all_probs.append(probs)
            all_max_probs.append(max_prob)
            all_target_probs.append(target_prob)

            # 클래스별 확률 저장
            class_probs[action].append(probs)

            # 오분류 샘플 저장
            if pred != label:
                misclassified.append({
                    "true_label": label,
                    "true_action": action,
                    "pred_label": pred,
                    "pred_action": label_to_action[pred],
                    "true_prob": target_prob,
                    "pred_prob": max_prob,
                    "all_probs": probs,
                    "person": sample["person"],
                    "file": sample["file"],
                })

    return {
        "labels": np.array(all_labels),
        "preds": np.array(all_preds),
        "probs": np.array(all_probs),
        "max_probs": np.array(all_max_probs),
        "target_probs": np.array(all_target_probs),
        "misclassified": misclassified,
        "class_probs": class_probs,
    }


def print_evaluation_results(results: Dict, label_to_action: Dict[int, str]):
    """평가 결과 출력"""

    labels = results["labels"]
    preds = results["preds"]
    probs = results["probs"]
    max_probs = results["max_probs"]
    target_probs = results["target_probs"]
    misclassified = results["misclassified"]
    class_probs = results["class_probs"]

    num_classes = len(label_to_action)
    class_names = [label_to_action[i] for i in range(num_classes)]

    print("\n" + "="*80)
    print("모델 평가 결과")
    print("="*80)

    # 전체 정확도
    accuracy = np.mean(labels == preds) * 100
    print(f"\n▶ 전체 성능:")
    print(f"  - 총 샘플: {len(labels)}개")
    print(f"  - 정확도: {accuracy:.2f}% ({int(np.sum(labels == preds))}/{len(labels)})")
    print(f"  - 오분류: {len(misclassified)}개")

    # 클래스별 성능
    print(f"\n{'='*80}")
    print("클래스별 성능 지표")
    print("="*80)

    # Precision, Recall, F1-score
    precision, recall, f1, support = precision_recall_fscore_support(
        labels, preds, labels=list(range(num_classes)), zero_division=0
    )

    print(f"{'클래스':<12} {'정확도':<10} {'Precision':<12} {'Recall':<10} {'F1-score':<12} {'평균 확률':<10}")
    print("-" * 80)

    for i in range(num_classes):
        action = class_names[i]
        mask = labels == i
        class_acc = np.mean(preds[mask] == i) * 100 if mask.sum() > 0 else 0

        # 해당 클래스의 평균 확률
        action_upper = action.upper()
        if action_upper in class_probs and len(class_probs[action_upper]) > 0:
            avg_prob = np.mean([p[i] for p in class_probs[action_upper]]) * 100
        else:
            avg_prob = 0

        print(
            f"{action:<12} "
            f"{class_acc:>6.2f}%   "
            f"{precision[i]*100:>8.2f}%   "
            f"{recall[i]*100:>6.2f}%   "
            f"{f1[i]*100:>8.2f}%   "
            f"{avg_prob:>6.2f}%"
        )

    # Confusion Matrix
    print(f"\n{'='*80}")
    print("Confusion Matrix")
    print("="*80)

    cm = confusion_matrix(labels, preds, labels=list(range(num_classes)))

    # 헤더
    header = "실제\\예측".ljust(12)
    for name in class_names:
        header += f"{name[:8]:>10}"
    print(header)
    print("-" * 80)

    # 각 행
    for i, name in enumerate(class_names):
        row = f"{name[:12]:<12}"
        for j in range(num_classes):
            row += f"{cm[i, j]:>10}"
        print(row)

    # 확률 분포 분석
    print(f"\n{'='*80}")
    print("확률 분포 분석")
    print("="*80)

    # 점수 변환 (inference.py 로직)
    score_90_plus = np.sum(target_probs >= 0.90)
    score_75_to_90 = np.sum((target_probs >= 0.75) & (target_probs < 0.90))
    score_60_to_75 = np.sum((target_probs >= 0.60) & (target_probs < 0.75))
    score_below_60 = np.sum(target_probs < 0.60)

    print(f"목표 클래스 확률 분포:")
    print(f"  - 90% 이상 (3점):  {score_90_plus:>4}개 ({score_90_plus/len(labels)*100:>5.1f}%)")
    print(f"  - 75-90% (2점):    {score_75_to_90:>4}개 ({score_75_to_90/len(labels)*100:>5.1f}%)")
    print(f"  - 60-75% (1점):    {score_60_to_75:>4}개 ({score_60_to_75/len(labels)*100:>5.1f}%)")
    print(f"  - 60% 미만 (0점):  {score_below_60:>4}개 ({score_below_60/len(labels)*100:>5.1f}%)")

    # 점수 변환 시뮬레이션
    scores = np.zeros_like(target_probs, dtype=int)
    scores[target_probs >= 0.90] = 3
    scores[(target_probs >= 0.75) & (target_probs < 0.90)] = 2
    scores[(target_probs >= 0.60) & (target_probs < 0.75)] = 1

    avg_score = np.mean(scores)
    avg_game_score = avg_score / 3 * 100  # 0-100점 환산

    print(f"\n▶ 점수 변환 시뮬레이션 (inference.py 기준):")
    print(f"  - 평균 점수: {avg_score:.2f}점 (게임 점수: {avg_game_score:.1f}점)")
    print(f"  - 3점: {np.sum(scores == 3):>4}개 ({np.sum(scores == 3)/len(scores)*100:>5.1f}%)")
    print(f"  - 2점: {np.sum(scores == 2):>4}개 ({np.sum(scores == 2)/len(scores)*100:>5.1f}%)")
    print(f"  - 1점: {np.sum(scores == 1):>4}개 ({np.sum(scores == 1)/len(scores)*100:>5.1f}%)")
    print(f"  - 0점: {np.sum(scores == 0):>4}개 ({np.sum(scores == 0)/len(scores)*100:>5.1f}%)")

    # STAY vs CLAP 분석
    if "STAY" in class_probs and "CLAP" in class_probs:
        print(f"\n{'='*80}")
        print("STAY vs CLAP 구분 분석 (가만히 있기 vs 손 박수)")
        print("="*80)

        # CLAP, STAY의 인덱스 찾기
        clap_idx = None
        stay_idx = None
        for idx, action in label_to_action.items():
            if action == "CLAP":
                clap_idx = idx
            elif action == "STAY":
                stay_idx = idx

        if clap_idx is not None and stay_idx is not None:
            # STAY 샘플의 확률 분석
            stay_probs = np.array(class_probs["STAY"])
            stay_stay_prob = stay_probs[:, stay_idx] * 100
            stay_clap_prob = stay_probs[:, clap_idx] * 100

            print(f"\n▶ STAY 샘플 ({len(stay_probs)}개):")
            print(f"  - STAY 확률 평균: {np.mean(stay_stay_prob):.2f}%")
            print(f"  - CLAP 확률 평균: {np.mean(stay_clap_prob):.2f}%")
            print(f"  - 구분 성공률: {np.sum(stay_stay_prob > stay_clap_prob) / len(stay_probs) * 100:.2f}%")

            # CLAP 샘플의 확률 분석
            clap_probs = np.array(class_probs["CLAP"])
            clap_clap_prob = clap_probs[:, clap_idx] * 100
            clap_stay_prob = clap_probs[:, stay_idx] * 100

            print(f"\n▶ CLAP 샘플 ({len(clap_probs)}개):")
            print(f"  - CLAP 확률 평균: {np.mean(clap_clap_prob):.2f}%")
            print(f"  - STAY 확률 평균: {np.mean(clap_stay_prob):.2f}%")
            print(f"  - 구분 성공률: {np.sum(clap_clap_prob > clap_stay_prob) / len(clap_probs) * 100:.2f}%")

            if np.mean(stay_stay_prob) > 95 and np.mean(clap_clap_prob) > 95:
                print(f"\n  ✅ 완벽한 구분! STAY와 CLAP를 확실하게 구별합니다.")
            elif np.mean(stay_stay_prob) > 85 and np.mean(clap_clap_prob) > 85:
                print(f"\n  ✅ 우수한 구분! 대부분의 경우 정확하게 구별합니다.")
            else:
                print(f"\n  ⚠️  주의: STAY와 CLAP 구분이 완벽하지 않습니다.")

    # 오분류 샘플 상세 분석
    if misclassified:
        print(f"\n{'='*80}")
        print(f"오분류 샘플 상세 분석 (상위 {min(10, len(misclassified))}개)")
        print("="*80)

        # 확률 차이가 작은 순으로 정렬 (애매한 케이스)
        sorted_mis = sorted(misclassified, key=lambda x: abs(x["pred_prob"] - x["true_prob"]))

        for i, mis in enumerate(sorted_mis[:10], 1):
            prob_diff = mis["pred_prob"] - mis["true_prob"]

            print(f"\n{i}. 실제: {mis['true_action']}, 예측: {mis['pred_action']}")
            print(f"   - 실제 클래스 확률: {mis['true_prob']*100:.1f}%")
            print(f"   - 예측 클래스 확률: {mis['pred_prob']*100:.1f}%")
            print(f"   - 확률 차이: {prob_diff*100:+.1f}%p")
            print(f"   - 파일: {mis['person']}/{mis['true_action']}/{mis['file'].name}")

            # 점수 계산
            if mis["true_prob"] >= 0.90:
                score = 3
            elif mis["true_prob"] >= 0.75:
                score = 2
            elif mis["true_prob"] >= 0.60:
                score = 1
            else:
                score = 0
            print(f"   - 게임 점수: {score}점")

            if abs(prob_diff) < 0.1:
                print(f"   - 분석: 매우 애매한 확률 (거의 동점)")
            elif abs(prob_diff) < 0.2:
                print(f"   - 분석: 애매한 확률")
            else:
                print(f"   - 분석: 확신하고 틀림")

    print("\n" + "="*80)


def main():
    parser = argparse.ArgumentParser(description="학습된 모델 종합 평가")
    parser.add_argument("--model", type=str, default="checkpoints/gcn_cnn_best.pt", help="모델 파일 경로")
    parser.add_argument("--data_dir", type=str, default="./pose_sequences", help="전처리된 데이터 경로")
    parser.add_argument("--device", type=str, default="cuda", help="디바이스 (cuda/cpu)")

    args = parser.parse_args()

    model_path = Path(args.model)
    data_dir = Path(args.data_dir)

    if not model_path.exists():
        raise FileNotFoundError(f"모델 파일을 찾을 수 없습니다: {model_path}")

    if not data_dir.exists():
        raise FileNotFoundError(f"데이터 디렉토리를 찾을 수 없습니다: {data_dir}")

    # 디바이스 설정
    device = args.device
    if device == "cuda" and not torch.cuda.is_available():
        print("⚠️  CUDA를 사용할 수 없습니다. CPU로 전환합니다.")
        device = "cpu"

    print(f"▶ 사용 디바이스: {device}")
    print(f"▶ 모델: {model_path}")
    print(f"▶ 데이터: {data_dir}")

    # 클래스 매핑
    action_to_label = {action: idx for idx, action in enumerate(sorted(SUPPORTED_ACTIONS))}
    label_to_action = {idx: action for action, idx in action_to_label.items()}
    num_classes = len(action_to_label)

    print(f"▶ 클래스 ({num_classes}개): {', '.join(action_to_label.keys())}")

    # 모델 로드
    print("\n📦 모델 로딩 중...")
    model = load_model(model_path, num_classes, device)
    print("✅ 모델 로드 완료")

    # 데이터 로드
    print("\n📂 데이터 로딩 중...")
    samples = load_all_samples(data_dir, action_to_label)
    print(f"✅ 데이터 로드 완료: 총 {len(samples)}개 샘플")

    # 평가
    results = evaluate_model_comprehensive(model, samples, label_to_action, device)

    # 결과 출력
    print_evaluation_results(results, label_to_action)


if __name__ == "__main__":
    main()
