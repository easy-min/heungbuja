"""
실시간 웹캠 동작 인식 테스트 스크립트

사용법:
    python realtime_webcam_test.py

옵션:
    --camera: 카메라 인덱스 (기본값: 0)
    --action-code: 테스트할 동작 코드 (기본값: 1, 손 박수)
    --action-name: 테스트할 동작 이름 (기본값: 손 박수)
    --fps: 캡처 FPS (기본값: 10)

키보드 단축키:
    SPACE: 프레임 수집 시작/중지
    Q: 종료
    1-7: 동작 변경 (1=손박수, 2=팔치기, 4=팔뻗기, 5=기우뚱, 6=비상구, 7=겨드랑이, 9=가만히)
"""

import argparse
import base64
import io
import sys
import time
from collections import deque
from pathlib import Path
from typing import Deque, Optional

import cv2
import numpy as np
from PIL import Image

# Motion server 패키지 import
sys.path.insert(0, str(Path(__file__).parent))
from app.services.inference import get_inference_service, InferenceResult


class RealtimeMotionTester:
    """실시간 웹캠 동작 인식 테스터"""

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
        camera_index: int = 0,
        target_action_code: int = 1,
        capture_fps: int = 10,
        frames_per_sample: int = 8,
    ):
        """
        Args:
            camera_index: 웹캠 인덱스
            target_action_code: 테스트할 동작 코드
            capture_fps: 프레임 캡처 FPS
            frames_per_sample: AI 분석에 사용할 프레임 수
        """
        self.camera_index = camera_index
        self.target_action_code = target_action_code
        self.target_action_name = self.ACTION_NAMES.get(target_action_code, "알 수 없음")
        self.capture_fps = capture_fps
        self.frames_per_sample = frames_per_sample

        # 웹캠 초기화
        self.cap = cv2.VideoCapture(camera_index)
        if not self.cap.isOpened():
            raise RuntimeError(f"카메라 {camera_index}를 열 수 없습니다.")

        # 해상도 설정 (640x480)
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

        # AI 모델 로드
        print("🤖 AI 모델 로딩 중...")
        self.inference_service = get_inference_service()
        print("✅ AI 모델 로드 완료!")

        # 프레임 버퍼 (최근 N개 프레임 저장)
        self.frame_buffer: Deque[str] = deque(maxlen=frames_per_sample)

        # 상태 변수
        self.is_collecting = False  # 프레임 수집 중 여부
        self.last_result: Optional[InferenceResult] = None
        self.last_inference_time = 0

    def run(self):
        """메인 루프"""
        print("\n" + "=" * 80)
        print("🎥 실시간 웹캠 동작 인식 시작!")
        print("=" * 80)
        print(f"📹 카메라: {self.camera_index}")
        print(f"🎯 목표 동작: {self.target_action_name} (코드: {self.target_action_code})")
        print(f"⏱️ 캡처 FPS: {self.capture_fps}")
        print(f"📦 샘플 프레임 수: {self.frames_per_sample}")
        print("\n키보드 단축키:")
        print("  - SPACE: 프레임 수집 시작/중지")
        print("  - Q: 종료")
        print("  - 1-7: 동작 변경")
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

                # FPS 제어: 지정된 간격마다 프레임 수집
                if self.is_collecting and (current_time - last_frame_time) >= frame_interval:
                    self._collect_frame(frame)
                    last_frame_time = current_time

                    # 버퍼가 다 찼으면 AI 분석 실행
                    if len(self.frame_buffer) == self.frames_per_sample:
                        self._run_inference()
                        self.frame_buffer.clear()

                # UI 그리기
                self._draw_ui(frame)

                # 화면 표시
                cv2.imshow("실시간 동작 인식", frame)

                # 키보드 입력 처리
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

    def _collect_frame(self, frame: np.ndarray):
        """프레임을 Base64로 변환하여 버퍼에 추가"""
        # OpenCV BGR → PIL RGB
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        pil_image = Image.fromarray(rgb_frame)

        # JPEG로 인코딩 후 Base64 변환
        buffer = io.BytesIO()
        pil_image.save(buffer, format="JPEG", quality=85)
        buffer.seek(0)
        base64_str = base64.b64encode(buffer.read()).decode("utf-8")

        self.frame_buffer.append(base64_str)

    def _run_inference(self):
        """수집된 프레임으로 AI 추론 실행"""
        start_time = time.time()

        try:
            print(f"\n🔍 AI 분석 시작... (프레임: {len(self.frame_buffer)}개)")

            result = self.inference_service.predict(
                frames=list(self.frame_buffer),
                target_action_name=self.target_action_name,
                target_action_code=self.target_action_code,
            )

            self.last_result = result
            self.last_inference_time = (time.time() - start_time) * 1000

            # 결과 출력
            self._print_result(result)

        except ValueError as e:
            print(f"❌ 분석 실패: {e}")
            self.last_result = None
        except Exception as e:
            print(f"❌ 예외 발생: {e}")
            self.last_result = None

    def _print_result(self, result: InferenceResult):
        """추론 결과 콘솔 출력"""
        score_emoji = ["❌", "⚠️", "✅", "🎯"]

        print("\n" + "=" * 80)
        print("🎯 AI 판정 결과")
        print("=" * 80)
        print(f"  목표 동작: {self.target_action_name} (코드: {self.target_action_code})")
        print(f"  예측 동작: {result.predicted_label}")
        print(f"  예측 신뢰도: {result.confidence * 100:.1f}%")

        if result.target_probability is not None:
            print(f"  목표 확률: {result.target_probability * 100:.1f}%")

        print(f"\n  최종 점수: {result.judgment}점 {score_emoji[result.judgment]}")

        total_ms = result.decode_time_ms + result.pose_time_ms + result.inference_time_ms
        print(f"\n  처리 시간: {total_ms:.0f}ms (추론: {result.inference_time_ms:.0f}ms)")
        print("=" * 80 + "\n")

    def _draw_ui(self, frame: np.ndarray):
        """화면에 UI 그리기"""
        height, width = frame.shape[:2]

        # 반투명 배경
        overlay = frame.copy()
        cv2.rectangle(overlay, (10, 10), (width - 10, 200), (0, 0, 0), -1)
        cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)

        # 텍스트 정보
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

        # 마지막 결과 표시
        if self.last_result:
            # 판정 결과
            judgment_color = self._get_judgment_color(self.last_result.judgment)
            judgment_text = f"판정: {self.last_result.judgment}점"
            cv2.putText(frame, judgment_text, (20, y_offset), font, font_scale, judgment_color, thickness)
            y_offset += 30

            # 예측 동작
            predicted_text = f"예측: {self.last_result.predicted_label}"
            cv2.putText(frame, predicted_text, (20, y_offset), font, 0.6, (255, 255, 255), 1)
            y_offset += 25

            # 신뢰도
            confidence_text = f"신뢰도: {self.last_result.confidence * 100:.1f}%"
            cv2.putText(frame, confidence_text, (20, y_offset), font, 0.6, (255, 255, 255), 1)
            y_offset += 25

            # 목표 확률
            if self.last_result.target_probability is not None:
                target_prob_text = f"목표확률: {self.last_result.target_probability * 100:.1f}%"
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
        """판정 점수에 따른 색상 반환 (BGR)"""
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
    parser = argparse.ArgumentParser(description="실시간 웹캠 동작 인식 테스트")
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
        help="테스트할 동작 코드 (기본값: 1, 손 박수)",
    )
    parser.add_argument(
        "--fps",
        type=int,
        default=10,
        help="캡처 FPS (기본값: 10)",
    )

    args = parser.parse_args()

    try:
        tester = RealtimeMotionTester(
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
