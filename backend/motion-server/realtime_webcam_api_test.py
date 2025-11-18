"""
실시간 웹캠 동작 인식 테스트 (API 버전)

motion-server API를 호출하여 동작 인식 테스트
Python 3.13에서도 작동! (opencv-python만 필요)

사용법:
    pip install opencv-python requests pillow
    python realtime_webcam_api_test.py

옵션:
    --url: Motion 서버 URL (기본값: http://localhost:8000)
    --camera: 카메라 인덱스 (기본값: 0)
    --action-code: 테스트할 동작 코드 (기본값: 1)
    --fps: 캡처 FPS (기본값: 10)

키보드 단축키:
    SPACE: 프레임 수집 시작/중지
    Q: 종료
    1-7: 동작 변경
"""

import argparse
import base64
import io
import sys
import time
from collections import deque
from typing import Deque, Optional, Dict, Any

import cv2
import requests
from PIL import Image


class RealtimeMotionAPITester:
    """실시간 웹캠 동작 인식 테스터 (API 버전)"""

    # 동작 코드 → 이름 매핑
    ACTION_NAMES = {
        1: "손 박수",
        2: "팔 치기",
        4: "팔 뻗기",
        5: "기우뚱",
        6: "비상구",
        7: "겨드랑이박수",
        9: "가만히 있음",
    }

    def __init__(
        self,
        api_url: str,
        camera_index: int = 0,
        target_action_code: int = 1,
        capture_fps: int = 10,
        frames_per_sample: int = 8,
    ):
        """
        Args:
            api_url: Motion 서버 API URL
            camera_index: 웹캠 인덱스
            target_action_code: 테스트할 동작 코드
            capture_fps: 프레임 캡처 FPS
            frames_per_sample: AI 분석에 사용할 프레임 수
        """
        self.api_url = api_url.rstrip("/") + "/api/ai/analyze"
        self.camera_index = camera_index
        self.target_action_code = target_action_code
        self.target_action_name = self.ACTION_NAMES.get(target_action_code, "알 수 없음")
        self.capture_fps = capture_fps
        self.frames_per_sample = frames_per_sample

        # 웹캠 초기화
        self.cap = cv2.VideoCapture(camera_index)
        if not self.cap.isOpened():
            raise RuntimeError(f"카메라 {camera_index}를 열 수 없습니다.")

        # 해상도 설정
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

        # API 연결 테스트
        print(f"🔌 API 연결 테스트 중: {self.api_url}")
        self._test_api_connection()

        # 프레임 버퍼
        self.frame_buffer: Deque[str] = deque(maxlen=frames_per_sample)

        # 상태 변수
        self.is_collecting = False
        self.last_result: Optional[Dict[str, Any]] = None
        self.last_inference_time = 0

    def _test_api_connection(self):
        """API 서버 연결 테스트"""
        try:
            # 간단한 더미 요청으로 연결 확인
            test_frame = self._create_test_frame()
            payload = {
                "actionCode": 1,
                "actionName": "손 박수",
                "frameCount": 1,
                "frames": [test_frame] * 8,
            }
            response = requests.post(
                self.api_url,
                json=payload,
                timeout=5,
            )
            if response.status_code in [200, 400]:  # 400도 OK (프레임이 더미라서)
                print("✅ API 서버 연결 성공!")
            else:
                print(f"⚠️ API 응답 코드: {response.status_code}")
        except requests.exceptions.ConnectionError:
            print(f"❌ API 서버에 연결할 수 없습니다: {self.api_url}")
            print("motion-server가 실행 중인지 확인하세요.")
            sys.exit(1)
        except Exception as e:
            print(f"⚠️ API 연결 테스트 중 에러: {e}")

    def _create_test_frame(self) -> str:
        """테스트용 더미 프레임 생성"""
        img = Image.new("RGB", (100, 100), color=(0, 0, 0))
        buffer = io.BytesIO()
        img.save(buffer, format="JPEG")
        buffer.seek(0)
        return base64.b64encode(buffer.read()).decode("utf-8")

    def run(self):
        """메인 루프"""
        print("\n" + "=" * 80)
        print("🎥 실시간 웹캠 동작 인식 시작! (API 버전)")
        print("=" * 80)
        print(f"🔌 API URL: {self.api_url}")
        print(f"📹 카메라: {self.camera_index}")
        print(f"🎯 목표 동작: {self.target_action_name} (코드: {self.target_action_code})")
        print(f"⏱️ 캡처 FPS: {self.capture_fps}")
        print(f"📦 샘플 프레임 수: {self.frames_per_sample}")
        print("\n키보드 단축키:")
        print("  - SPACE: 프레임 수집 시작/중지")
        print("  - Q: 종료")
        print("  - 1-9: 동작 변경")
        print("=" * 80 + "\n")

        frame_interval = 1.0 / self.capture_fps
        last_frame_time = 0

        try:
            while True:
                ret, frame = self.cap.read()
                if not ret:
                    print("❌ 프레임 읽기 실패")
                    break

                # 좌우 반전 (셀카 모드)
                frame = cv2.flip(frame, 1)

                current_time = time.time()

                # FPS 제어
                if self.is_collecting and (current_time - last_frame_time) >= frame_interval:
                    self._collect_frame(frame)
                    last_frame_time = current_time

                    # 버퍼가 다 찼으면 API 호출
                    if len(self.frame_buffer) == self.frames_per_sample:
                        self._run_inference_api()
                        self.frame_buffer.clear()

                # UI 그리기
                self._draw_ui(frame)

                # 화면 표시
                cv2.imshow("실시간 동작 인식 (API)", frame)

                # 키보드 입력
                key = cv2.waitKey(1) & 0xFF
                if key == ord("q"):
                    print("\n👋 종료합니다...")
                    break
                elif key == ord(" "):
                    self.is_collecting = not self.is_collecting
                    if self.is_collecting:
                        print(f"\n▶️ 프레임 수집 시작! ({self.frames_per_sample}개)")
                        self.frame_buffer.clear()
                        self.last_result = None
                    else:
                        print("\n⏸️ 프레임 수집 중지")
                elif key in [ord("1"), ord("2"), ord("4"), ord("5"), ord("6"), ord("7"), ord("9")]:
                    code = int(chr(key))
                    if code in self.ACTION_NAMES:
                        self._change_target_action(code)

        finally:
            self.cap.release()
            cv2.destroyAllWindows()

    def _collect_frame(self, frame):
        """프레임을 Base64로 변환하여 버퍼에 추가"""
        # OpenCV BGR → RGB
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        # PIL Image로 변환
        pil_image = Image.fromarray(rgb_frame)

        # JPEG 인코딩 후 Base64 변환
        buffer = io.BytesIO()
        pil_image.save(buffer, format="JPEG", quality=85)
        buffer.seek(0)
        base64_str = base64.b64encode(buffer.read()).decode("utf-8")

        self.frame_buffer.append(base64_str)

    def _run_inference_api(self):
        """API 호출하여 AI 추론"""
        start_time = time.time()

        try:
            print(f"\n🔍 AI 분석 시작... (프레임: {len(self.frame_buffer)}개)")

            payload = {
                "actionCode": self.target_action_code,
                "actionName": self.target_action_name,
                "frameCount": len(self.frame_buffer),
                "frames": list(self.frame_buffer),
            }

            response = requests.post(
                self.api_url,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=30,
            )

            self.last_inference_time = (time.time() - start_time) * 1000

            if response.status_code == 200:
                result = response.json()
                self.last_result = result
                self._print_result(result)
            else:
                error_detail = response.json().get("detail", "Unknown error")
                print(f"❌ API 에러 (HTTP {response.status_code}): {error_detail}")
                self.last_result = None

        except requests.exceptions.Timeout:
            print(f"❌ 타임아웃: API 응답이 30초를 초과했습니다.")
            self.last_result = None
        except requests.exceptions.ConnectionError:
            print(f"❌ 연결 실패: API 서버에 연결할 수 없습니다.")
            self.last_result = None
        except Exception as e:
            print(f"❌ 예외 발생: {e}")
            self.last_result = None

    def _print_result(self, result: Dict[str, Any]):
        """추론 결과 콘솔 출력"""
        score_emoji = ["❌", "⚠️", "✅", "🎯"]

        judgment = result.get("judgment", 0)
        predicted_label = result.get("predictedLabel", "N/A")
        confidence = result.get("confidence", 0) * 100
        target_prob = result.get("targetProbability")

        print("\n" + "=" * 80)
        print("🎯 AI 판정 결과")
        print("=" * 80)
        print(f"  목표 동작: {self.target_action_name} (코드: {self.target_action_code})")
        print(f"  예측 동작: {predicted_label}")
        print(f"  예측 신뢰도: {confidence:.1f}%")

        if target_prob is not None:
            print(f"  목표 확률: {target_prob * 100:.1f}%")

        print(f"\n  최종 점수: {judgment}점 {score_emoji[judgment]}")

        decode_ms = result.get("decodeTimeMs", 0)
        pose_ms = result.get("poseTimeMs", 0)
        inference_ms = result.get("inferenceTimeMs", 0)
        total_ms = decode_ms + pose_ms + inference_ms

        print(f"\n  처리 시간: {total_ms:.0f}ms")
        print(f"    - 디코딩: {decode_ms:.0f}ms")
        print(f"    - Pose 추출: {pose_ms:.0f}ms")
        print(f"    - AI 추론: {inference_ms:.0f}ms")
        print(f"  네트워크 왕복: {self.last_inference_time:.0f}ms")
        print("=" * 80 + "\n")

    def _draw_ui(self, frame):
        """화면에 UI 그리기"""
        height, width = frame.shape[:2]

        # 반투명 배경
        overlay = frame.copy()
        cv2.rectangle(overlay, (10, 10), (width - 10, 220), (0, 0, 0), -1)
        cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)

        y_offset = 40
        font = cv2.FONT_HERSHEY_SIMPLEX
        font_scale = 0.7
        thickness = 2

        # 수집 상태
        status_text = "수집 중..." if self.is_collecting else "대기 중 (SPACE로 시작)"
        status_color = (0, 255, 0) if self.is_collecting else (100, 100, 100)
        cv2.putText(frame, status_text, (20, y_offset), font, font_scale, status_color, thickness)
        y_offset += 35

        # 목표 동작
        cv2.putText(
            frame,
            f"목표: {self.target_action_name} (코드: {self.target_action_code})",
            (20, y_offset),
            font,
            0.6,
            (255, 255, 255),
            1,
        )
        y_offset += 30

        # 버퍼 상태
        buffer_text = f"버퍼: {len(self.frame_buffer)}/{self.frames_per_sample}"
        cv2.putText(frame, buffer_text, (20, y_offset), font, 0.6, (255, 255, 255), 1)
        y_offset += 35

        # 마지막 결과
        if self.last_result:
            judgment = self.last_result.get("judgment", 0)
            predicted_label = self.last_result.get("predictedLabel", "N/A")
            confidence = self.last_result.get("confidence", 0) * 100
            target_prob = self.last_result.get("targetProbability")

            # 판정
            judgment_color = self._get_judgment_color(judgment)
            judgment_text = f"판정: {judgment}점"
            cv2.putText(frame, judgment_text, (20, y_offset), font, font_scale, judgment_color, thickness)
            y_offset += 30

            # 예측 동작
            predicted_text = f"예측: {predicted_label}"
            cv2.putText(frame, predicted_text, (20, y_offset), font, 0.6, (255, 255, 255), 1)
            y_offset += 25

            # 신뢰도
            confidence_text = f"신뢰도: {confidence:.1f}%"
            cv2.putText(frame, confidence_text, (20, y_offset), font, 0.6, (255, 255, 255), 1)
            y_offset += 25

            # 목표 확률
            if target_prob is not None:
                target_prob_text = f"목표확률: {target_prob * 100:.1f}%"
                cv2.putText(frame, target_prob_text, (20, y_offset), font, 0.6, (255, 255, 255), 1)

        # 하단 도움말
        help_text = "Q: 종료 | SPACE: 시작/중지 | 1-9: 동작변경"
        cv2.putText(
            frame,
            help_text,
            (20, height - 20),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (200, 200, 200),
            1,
        )

    def _get_judgment_color(self, judgment: int) -> tuple:
        """판정 점수에 따른 색상 (BGR)"""
        colors = {
            0: (0, 0, 255),  # 빨강
            1: (0, 165, 255),  # 주황
            2: (0, 255, 255),  # 노랑
            3: (0, 255, 0),  # 초록
        }
        return colors.get(judgment, (255, 255, 255))

    def _change_target_action(self, action_code: int):
        """목표 동작 변경"""
        self.target_action_code = action_code
        self.target_action_name = self.ACTION_NAMES[action_code]
        self.last_result = None
        print(f"\n🎯 목표 동작 변경: {self.target_action_name} (코드: {action_code})")


def main():
    parser = argparse.ArgumentParser(description="실시간 웹캠 동작 인식 (API 버전)")
    parser.add_argument(
        "--url",
        type=str,
        default="http://localhost:8000",
        help="Motion 서버 URL (기본값: http://localhost:8000)",
    )
    parser.add_argument(
        "--camera",
        type=int,
        default=0,
        help="카메라 인덱스 (기본값: 0)",
    )
    parser.add_argument(
        "--action-code",
        type=int,
        default=1,
        help="테스트할 동작 코드 (기본값: 1)",
    )
    parser.add_argument(
        "--fps",
        type=int,
        default=10,
        help="캡처 FPS (기본값: 10)",
    )

    args = parser.parse_args()

    try:
        tester = RealtimeMotionAPITester(
            api_url=args.url,
            camera_index=args.camera,
            target_action_code=args.action_code,
            capture_fps=args.fps,
        )
        tester.run()
    except KeyboardInterrupt:
        print("\n\n⚠️ 사용자에 의해 중단되었습니다.")
    except Exception as e:
        print(f"\n❌ 오류 발생: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
