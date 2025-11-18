"""
Motion AI 정확도 테스트 스크립트

목적: 가만히 있을 때 낮은 점수가 나오는지 검증

테스트 시나리오:
1. 빈 프레임 (사람 없음) → 예상: 400 에러 (유효 프레임 < 5)
2. 동일 프레임 반복 (가만히 서 있음) → 예상: 0점 또는 낮은 점수
3. (선택) 실제 동작 프레임 → 예상: 높은 점수

사용법:
    python test_motion_accuracy.py

옵션:
    --url: Motion 서버 URL (기본값: http://localhost:8000)
    --action-code: 테스트할 동작 코드 (기본값: 1, 손 박수)
    --action-name: 테스트할 동작 이름 (기본값: 손 박수)
"""

import argparse
import base64
import io
import json
import requests
from typing import Dict, Any, List
from PIL import Image, ImageDraw, ImageFont
import numpy as np


class MotionAccuracyTester:
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url.rstrip("/")
        self.api_endpoint = f"{self.base_url}/api/ai/analyze"

    def create_blank_frame(self, width: int = 640, height: int = 480, color: tuple = (0, 0, 0)) -> str:
        """
        빈 프레임 생성 (사람 없음)

        Args:
            width: 이미지 너비
            height: 이미지 높이
            color: RGB 색상 (기본값: 검은색)

        Returns:
            Base64 인코딩된 이미지 문자열
        """
        image = Image.new("RGB", (width, height), color)
        return self._image_to_base64(image)

    def create_static_person_frame(self, width: int = 640, height: int = 480) -> str:
        """
        사람이 서 있는 정적 프레임 생성 (테스트용 더미)

        실제 테스트에서는 실제 사람 이미지를 사용해야 합니다.
        이 함수는 간단한 스틱맨을 그려 Mediapipe가 감지할 수 있도록 합니다.

        Returns:
            Base64 인코딩된 이미지 문자열
        """
        image = Image.new("RGB", (width, height), (255, 255, 255))
        draw = ImageDraw.Draw(image)

        # 간단한 스틱맨 그리기 (Mediapipe가 감지하기 어려울 수 있음)
        # 주의: 실제 테스트에는 실제 사람 사진을 사용하세요!
        center_x, center_y = width // 2, height // 2

        # 머리
        draw.ellipse([center_x - 30, center_y - 150, center_x + 30, center_y - 90], fill=(255, 200, 180))

        # 몸통
        draw.line([center_x, center_y - 90, center_x, center_y + 50], fill=(100, 100, 100), width=20)

        # 팔
        draw.line([center_x, center_y - 60, center_x - 80, center_y], fill=(100, 100, 100), width=15)
        draw.line([center_x, center_y - 60, center_x + 80, center_y], fill=(100, 100, 100), width=15)

        # 다리
        draw.line([center_x, center_y + 50, center_x - 40, center_y + 150], fill=(100, 100, 100), width=15)
        draw.line([center_x, center_y + 50, center_x + 40, center_y + 150], fill=(100, 100, 100), width=15)

        return self._image_to_base64(image)

    def load_image_from_file(self, file_path: str) -> str:
        """
        파일에서 이미지 로드 후 Base64 인코딩

        Args:
            file_path: 이미지 파일 경로

        Returns:
            Base64 인코딩된 이미지 문자열
        """
        with Image.open(file_path) as image:
            return self._image_to_base64(image)

    def _image_to_base64(self, image: Image.Image) -> str:
        """
        PIL Image를 Base64 문자열로 변환
        """
        buffer = io.BytesIO()
        image.save(buffer, format="JPEG")
        buffer.seek(0)
        return base64.b64encode(buffer.read()).decode("utf-8")

    def test_motion_inference(
        self,
        frames: List[str],
        action_code: int = 1,
        action_name: str = "손 박수",
        scenario_name: str = "Unknown"
    ) -> Dict[str, Any]:
        """
        Motion AI 추론 API 호출 및 결과 분석

        Args:
            frames: Base64 인코딩된 프레임 리스트
            action_code: 목표 동작 코드
            action_name: 목표 동작 이름
            scenario_name: 테스트 시나리오 이름 (로깅용)

        Returns:
            테스트 결과 딕셔너리
        """
        payload = {
            "actionCode": action_code,
            "actionName": action_name,
            "frameCount": len(frames),
            "frames": frames
        }

        print(f"\n{'='*80}")
        print(f"🧪 테스트 시나리오: {scenario_name}")
        print(f"{'='*80}")
        print(f"📤 요청 정보:")
        print(f"   - 동작: {action_name} (코드: {action_code})")
        print(f"   - 프레임 수: {len(frames)}개")
        print(f"   - API: {self.api_endpoint}")

        try:
            response = requests.post(
                self.api_endpoint,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=30
            )

            print(f"\n📥 응답 상태: {response.status_code}")

            if response.status_code == 200:
                result = response.json()
                self._print_success_result(result)
                return {
                    "scenario": scenario_name,
                    "status": "success",
                    "status_code": 200,
                    "result": result
                }
            else:
                error_detail = response.json().get("detail", "Unknown error")
                self._print_error_result(response.status_code, error_detail)
                return {
                    "scenario": scenario_name,
                    "status": "error",
                    "status_code": response.status_code,
                    "error": error_detail
                }

        except requests.exceptions.Timeout:
            print(f"\n❌ 타임아웃: 서버 응답이 30초를 초과했습니다.")
            return {
                "scenario": scenario_name,
                "status": "timeout",
                "error": "Request timeout (30s)"
            }
        except requests.exceptions.ConnectionError:
            print(f"\n❌ 연결 실패: Motion 서버에 연결할 수 없습니다.")
            print(f"   서버가 실행 중인지 확인하세요: {self.base_url}")
            return {
                "scenario": scenario_name,
                "status": "connection_error",
                "error": f"Cannot connect to {self.base_url}"
            }
        except Exception as e:
            print(f"\n❌ 예외 발생: {str(e)}")
            return {
                "scenario": scenario_name,
                "status": "exception",
                "error": str(e)
            }

    def _print_success_result(self, result: Dict[str, Any]):
        """성공 응답 결과 출력"""
        judgment = result.get("judgment", 0)
        predicted_label = result.get("predictedLabel", "N/A")
        confidence = result.get("confidence", 0) * 100
        target_prob = result.get("targetProbability")
        target_prob_str = f"{target_prob * 100:.1f}%" if target_prob is not None else "N/A"

        # 점수별 색상 구분 (터미널에서는 보이지 않을 수 있음)
        score_emoji = ["❌", "⚠️", "✅", "🎯"]

        print(f"\n✅ 추론 성공!")
        print(f"\n📊 AI 판정 결과:")
        print(f"   - 최종 점수: {judgment}점 {score_emoji[judgment]}")
        print(f"   - 예측 동작: {predicted_label}")
        print(f"   - 예측 신뢰도: {confidence:.1f}%")
        print(f"   - 목표 동작 확률: {target_prob_str}")

        print(f"\n⏱️ 처리 시간:")
        print(f"   - 디코딩: {result.get('decodeTimeMs', 0):.1f}ms")
        print(f"   - Pose 추출: {result.get('poseTimeMs', 0):.1f}ms")
        print(f"   - AI 추론: {result.get('inferenceTimeMs', 0):.1f}ms")
        print(f"   - 총 처리 시간: {result.get('decodeTimeMs', 0) + result.get('poseTimeMs', 0) + result.get('inferenceTimeMs', 0):.1f}ms")

        # 점수 기준 안내
        print(f"\n📏 점수 기준:")
        print(f"   - 3점 (100점): 목표 확률 90% 이상")
        print(f"   - 2점 (67점): 목표 확률 75% 이상")
        print(f"   - 1점 (33점): 목표 확률 60% 이상")
        print(f"   - 0점 (0점): 목표 확률 60% 미만")

    def _print_error_result(self, status_code: int, error_detail: str):
        """에러 응답 결과 출력"""
        print(f"\n❌ 추론 실패 (HTTP {status_code})")
        print(f"   에러 메시지: {error_detail}")

        if status_code == 400:
            print(f"\n💡 분석:")
            print(f"   - 유효한 프레임이 5개 미만일 가능성")
            print(f"   - Mediapipe가 사람을 감지하지 못했거나")
            print(f"   - 모든 프레임이 zero vector로 필터링됨")
            print(f"   → 이는 정상적인 동작입니다 (가만히 있거나 사람이 없으면 400 에러)")


def run_all_tests(tester: MotionAccuracyTester, action_code: int, action_name: str):
    """모든 테스트 시나리오 실행"""

    results = []

    # ========================================================================
    # 시나리오 1: 빈 프레임 10개 (사람 없음)
    # ========================================================================
    print("\n" + "="*80)
    print("🧪 시작: 전체 테스트 실행")
    print("="*80)

    blank_frames = [tester.create_blank_frame() for _ in range(10)]
    result1 = tester.test_motion_inference(
        frames=blank_frames,
        action_code=action_code,
        action_name=action_name,
        scenario_name="시나리오 1: 빈 프레임 (사람 없음)"
    )
    results.append(result1)

    # ========================================================================
    # 시나리오 2: 동일 프레임 10번 반복 (가만히 서 있음)
    # ========================================================================
    static_frame = tester.create_static_person_frame()
    static_frames = [static_frame] * 10
    result2 = tester.test_motion_inference(
        frames=static_frames,
        action_code=action_code,
        action_name=action_name,
        scenario_name="시나리오 2: 동일 프레임 반복 (가만히 서 있음)"
    )
    results.append(result2)

    # ========================================================================
    # 시나리오 3: 실제 이미지 파일이 있는 경우 (선택)
    # ========================================================================
    # 사용자가 실제 사진을 제공한 경우 테스트
    # example: test_images/static_person.jpg

    # ========================================================================
    # 최종 결과 요약
    # ========================================================================
    print("\n" + "="*80)
    print("📋 테스트 결과 요약")
    print("="*80)

    for i, result in enumerate(results, 1):
        scenario = result["scenario"]
        status = result["status"]

        print(f"\n[테스트 {i}] {scenario}")

        if status == "success":
            judgment = result["result"]["judgment"]
            target_prob = result["result"].get("targetProbability")
            target_prob_str = f"{target_prob * 100:.1f}%" if target_prob is not None else "N/A"
            print(f"   ✅ 성공: 점수={judgment}점, 목표확률={target_prob_str}")
        elif status == "error":
            status_code = result["status_code"]
            error = result["error"]
            print(f"   ❌ 에러: HTTP {status_code} - {error}")
        else:
            print(f"   ⚠️ {status}: {result.get('error', 'Unknown')}")

    print("\n" + "="*80)
    print("💡 기대 결과:")
    print("   - 시나리오 1 (빈 프레임): 400 에러 (유효 프레임 < 5)")
    print("   - 시나리오 2 (가만히): 0점 또는 낮은 점수 (< 60% 확률)")
    print("="*80)

    return results


def main():
    parser = argparse.ArgumentParser(description="Motion AI 정확도 테스트")
    parser.add_argument(
        "--url",
        type=str,
        default="http://localhost:8000",
        help="Motion 서버 URL (기본값: http://localhost:8000)"
    )
    parser.add_argument(
        "--action-code",
        type=int,
        default=1,
        help="테스트할 동작 코드 (기본값: 1)"
    )
    parser.add_argument(
        "--action-name",
        type=str,
        default="손 박수",
        help="테스트할 동작 이름 (기본값: 손 박수)"
    )
    parser.add_argument(
        "--scenario",
        type=str,
        choices=["blank", "static", "all"],
        default="all",
        help="실행할 시나리오 (기본값: all)"
    )

    args = parser.parse_args()

    tester = MotionAccuracyTester(base_url=args.url)

    if args.scenario == "all":
        run_all_tests(tester, args.action_code, args.action_name)
    elif args.scenario == "blank":
        blank_frames = [tester.create_blank_frame() for _ in range(10)]
        tester.test_motion_inference(
            frames=blank_frames,
            action_code=args.action_code,
            action_name=args.action_name,
            scenario_name="빈 프레임 테스트"
        )
    elif args.scenario == "static":
        static_frame = tester.create_static_person_frame()
        static_frames = [static_frame] * 10
        tester.test_motion_inference(
            frames=static_frames,
            action_code=args.action_code,
            action_name=args.action_name,
            scenario_name="정적 프레임 테스트"
        )


if __name__ == "__main__":
    main()
