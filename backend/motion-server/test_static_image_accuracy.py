"""
정적 이미지를 각 동작별로 테스트하여 정확도 비교

목적: 가만히 있는 사진을 여러 동작으로 테스트해서 각 동작별 확률 차이 분석

사용법:
    # 이미지 파일 1개를 모든 동작으로 테스트
    python test_static_image_accuracy.py static_person.jpg

    # 동작 코드 범위 지정 (1-10번만 테스트)
    python test_static_image_accuracy.py static_person.jpg --start 1 --end 10

    # 특정 동작들만 테스트
    python test_static_image_accuracy.py static_person.jpg --actions 1 2 5 8
"""

import argparse
import base64
import io
import json
import sys
from pathlib import Path
from typing import Dict, Any, List
from PIL import Image
import requests


# 동작 이름 매핑
# - DB actionCode: 1, 2, 3, 4, 5, 6, 7, 8 (1-based)
# - Model class_index: 0, 1, 2, 3, 4 (0-based)
#
# ⚠️ 주의: actionCode - 1 ≠ class_index (1:1 매핑 아님!)
#
# Model에 학습된 동작 (5개):
#   class 0: CLAP (손 박수)
#   class 1: EXIT (비상구)
#   class 2: STRETCH (팔 뻗기)
#   class 3: TILT (기우뚱)
#   class 4: UNDERARM (겨드랑이박수)
#
# DB actionCode → Model class_index 매핑:
#   actionCode 1 → class 0 (손 박수)
#   actionCode 2 → None (팔 치기 - 모델에 없음)
#   actionCode 3 → None (엉덩이 박수 - 모델에 없음)
#   actionCode 4 → class 2 (팔 뻗기)
#   actionCode 5 → class 3 (기우뚱)
#   actionCode 6 → class 1 (비상구)
#   actionCode 7 → class 4 (겨드랑이박수)
#   actionCode 8 → None (팔 모으기 - 모델에 없음)
ACTION_NAMES = {
    1: "손 박수",           # class 0: CLAP
    2: "팔 치기",           # 모델에 없음
    3: "엉덩이 박수",        # 모델에 없음
    4: "팔 뻗기",           # class 2: STRETCH
    5: "기우뚱",            # class 3: TILT
    6: "비상구",            # class 1: EXIT
    7: "겨드랑이박수",       # class 4: UNDERARM
    8: "팔 모으기",         # 모델에 없음
}


class StaticImageAccuracyTester:
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url.rstrip("/")
        self.api_endpoint = f"{self.base_url}/api/ai/analyze"

    def load_image_from_file(self, file_path: str) -> str:
        """
        파일에서 이미지 로드 후 Base64 인코딩

        Args:
            file_path: 이미지 파일 경로

        Returns:
            Base64 인코딩된 이미지 문자열
        """
        path = Path(file_path)
        if not path.exists():
            raise FileNotFoundError(f"이미지 파일을 찾을 수 없습니다: {file_path}")

        with Image.open(file_path) as image:
            return self._image_to_base64(image)

    def _image_to_base64(self, image: Image.Image) -> str:
        """PIL Image를 Base64 문자열로 변환"""
        buffer = io.BytesIO()
        image.save(buffer, format="JPEG")
        buffer.seek(0)
        return base64.b64encode(buffer.read()).decode("utf-8")

    def test_single_action(
        self,
        frames: List[str],
        action_code: int,
        action_name: str
    ) -> Dict[str, Any]:
        """
        단일 동작에 대해 AI 추론 수행

        Args:
            frames: Base64 인코딩된 프레임 리스트
            action_code: 목표 동작 코드
            action_name: 목표 동작 이름

        Returns:
            테스트 결과 딕셔너리
        """
        payload = {
            "actionCode": action_code,
            "actionName": action_name,
            "frameCount": len(frames),
            "frames": frames
        }

        try:
            response = requests.post(
                self.api_endpoint,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=30
            )

            if response.status_code == 200:
                result = response.json()
                return {
                    "actionCode": action_code,
                    "actionName": action_name,
                    "status": "success",
                    "judgment": result.get("judgment", 0),
                    "predictedLabel": result.get("predictedLabel", "N/A"),
                    "confidence": result.get("confidence", 0),
                    "targetProbability": result.get("targetProbability"),
                    "decodeTimeMs": result.get("decodeTimeMs", 0),
                    "poseTimeMs": result.get("poseTimeMs", 0),
                    "inferenceTimeMs": result.get("inferenceTimeMs", 0),
                }
            else:
                error_detail = response.json().get("detail", "Unknown error")
                return {
                    "actionCode": action_code,
                    "actionName": action_name,
                    "status": "error",
                    "error": error_detail,
                    "status_code": response.status_code
                }

        except requests.exceptions.Timeout:
            return {
                "actionCode": action_code,
                "actionName": action_name,
                "status": "timeout",
                "error": "Request timeout (30s)"
            }
        except requests.exceptions.ConnectionError:
            return {
                "actionCode": action_code,
                "actionName": action_name,
                "status": "connection_error",
                "error": f"Cannot connect to {self.base_url}"
            }
        except Exception as e:
            return {
                "actionCode": action_code,
                "actionName": action_name,
                "status": "exception",
                "error": str(e)
            }

    def test_all_actions(
        self,
        image_path: str,
        action_codes: List[int],
        frame_repeat: int = 10
    ) -> List[Dict[str, Any]]:
        """
        하나의 정적 이미지를 여러 동작으로 테스트

        Args:
            image_path: 테스트할 이미지 경로
            action_codes: 테스트할 동작 코드 리스트
            frame_repeat: 동일 프레임 반복 횟수

        Returns:
            모든 동작의 테스트 결과 리스트
        """
        print("="*80)
        print(f"📸 정적 이미지 정확도 테스트")
        print("="*80)
        print(f"이미지: {image_path}")
        print(f"프레임 반복: {frame_repeat}회")
        print(f"테스트 동작: {len(action_codes)}개")
        print("="*80)

        # 이미지 로드 및 Base64 인코딩
        try:
            base64_frame = self.load_image_from_file(image_path)
            frames = [base64_frame] * frame_repeat
        except FileNotFoundError as e:
            print(f"\n❌ {e}")
            sys.exit(1)

        results = []

        # 각 동작별로 테스트
        for i, action_code in enumerate(action_codes, 1):
            action_name = ACTION_NAMES.get(action_code, f"동작{action_code}")

            print(f"\n[{i}/{len(action_codes)}] 테스트 중: {action_name} (코드: {action_code})...", end=" ")

            result = self.test_single_action(frames, action_code, action_name)
            results.append(result)

            if result["status"] == "success":
                target_prob = result.get("targetProbability")
                target_prob_str = f"{target_prob * 100:.1f}%" if target_prob is not None else "N/A"
                print(f"✅ 점수: {result['judgment']}점, 확률: {target_prob_str}")
            else:
                print(f"❌ {result['status']}")

        return results

    def print_comparison_table(self, results: List[Dict[str, Any]]):
        """
        테스트 결과를 비교 표로 출력

        Args:
            results: 테스트 결과 리스트
        """
        print("\n" + "="*80)
        print("📊 동작별 정확도 비교 결과")
        print("="*80)

        # 성공한 결과만 필터링
        success_results = [r for r in results if r["status"] == "success"]

        if not success_results:
            print("❌ 성공한 테스트 결과가 없습니다.")
            return

        # 헤더 출력
        header = f"{'동작 코드':^10} | {'동작 이름':^15} | {'점수':^6} | {'목표 확률':^12} | {'예측 동작':^15} | {'예측 신뢰도':^12}"
        print(header)
        print("-" * len(header))

        # 각 결과 출력 (목표 확률 높은 순으로 정렬)
        sorted_results = sorted(
            success_results,
            key=lambda x: x.get("targetProbability") or 0,
            reverse=True
        )

        for result in sorted_results:
            action_code = result["actionCode"]
            action_name = result["actionName"]
            judgment = result["judgment"]
            target_prob = result.get("targetProbability")
            predicted_label = result["predictedLabel"]
            confidence = result["confidence"]

            target_prob_str = f"{target_prob * 100:>5.1f}%" if target_prob is not None else "N/A"
            confidence_str = f"{confidence * 100:>5.1f}%"

            # 점수별 이모지
            score_emoji = ["❌", "⚠️ ", "✅", "🎯"]
            score_display = f"{judgment}점 {score_emoji[judgment]}"

            row = f"{action_code:^10} | {action_name:^15} | {score_display:^6} | {target_prob_str:^12} | {predicted_label:^15} | {confidence_str:^12}"
            print(row)

        print("-" * len(header))

        # 통계 출력
        print("\n📈 통계:")
        total = len(success_results)
        score_counts = {0: 0, 1: 0, 2: 0, 3: 0}
        for r in success_results:
            score_counts[r["judgment"]] += 1

        print(f"   - 총 테스트: {total}개")
        print(f"   - 0점 (< 60%): {score_counts[0]}개 ({score_counts[0]/total*100:.1f}%)")
        print(f"   - 1점 (60-75%): {score_counts[1]}개 ({score_counts[1]/total*100:.1f}%)")
        print(f"   - 2점 (75-90%): {score_counts[2]}개 ({score_counts[2]/total*100:.1f}%)")
        print(f"   - 3점 (90%+): {score_counts[3]}개 ({score_counts[3]/total*100:.1f}%)")

        # 가장 높은 확률
        if sorted_results:
            highest = sorted_results[0]
            target_prob = highest.get("targetProbability")
            if target_prob is not None:
                print(f"\n🏆 가장 높은 확률:")
                print(f"   {highest['actionName']} (코드: {highest['actionCode']}): {target_prob * 100:.1f}%")

        # 에러 통계
        error_results = [r for r in results if r["status"] != "success"]
        if error_results:
            print(f"\n⚠️ 에러 발생: {len(error_results)}개")
            for r in error_results:
                print(f"   - {r['actionName']} (코드: {r['actionCode']}): {r['status']}")

        print("="*80)

        # 분석 의견
        print("\n💡 분석:")
        if score_counts[0] == total:
            print("   ✅ 완벽! 모든 동작에서 0점 (가만히 있을 때 점수 안 나옴)")
        elif score_counts[0] / total >= 0.8:
            print("   ✅ 양호: 대부분 동작에서 0점 (80% 이상)")
        elif score_counts[0] / total >= 0.5:
            print("   ⚠️ 주의: 절반 정도만 0점 (50-80%)")
        else:
            print("   ❌ 문제: 가만히 있는데도 많은 동작에서 점수 나옴 (< 50%)")
            print("   → 임계값(threshold) 조정이 필요할 수 있습니다.")


def main():
    parser = argparse.ArgumentParser(description="정적 이미지 정확도 비교 테스트")
    parser.add_argument(
        "image",
        type=str,
        help="테스트할 이미지 파일 경로"
    )
    parser.add_argument(
        "--url",
        type=str,
        default="http://localhost:8000",
        help="Motion 서버 URL (기본값: http://localhost:8000)"
    )
    parser.add_argument(
        "--start",
        type=int,
        default=1,
        help="시작 동작 코드 (기본값: 1)"
    )
    parser.add_argument(
        "--end",
        type=int,
        default=8,
        help="끝 동작 코드 (기본값: 8)"
    )
    parser.add_argument(
        "--actions",
        type=int,
        nargs="+",
        help="특정 동작 코드만 테스트 (예: --actions 1 2 5 8)"
    )
    parser.add_argument(
        "--repeat",
        type=int,
        default=10,
        help="동일 프레임 반복 횟수 (기본값: 10)"
    )
    parser.add_argument(
        "--output",
        type=str,
        help="결과를 JSON 파일로 저장 (선택)"
    )

    args = parser.parse_args()

    # 테스트할 동작 코드 결정
    if args.actions:
        action_codes = args.actions
    else:
        action_codes = list(range(args.start, args.end + 1))

    # 테스트 실행
    tester = StaticImageAccuracyTester(base_url=args.url)
    results = tester.test_all_actions(
        image_path=args.image,
        action_codes=action_codes,
        frame_repeat=args.repeat
    )

    # 결과 출력
    tester.print_comparison_table(results)

    # JSON 파일로 저장 (선택)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        print(f"\n💾 결과가 {args.output}에 저장되었습니다.")


if __name__ == "__main__":
    main()
