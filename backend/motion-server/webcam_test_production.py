"""
배포 서버 테스트용 웹캠 동작 인식

사용법:
    python webcam_test_production.py

배포 서버: https://heungbuja.site/motion/api/ai/analyze

키보드:
    SPACE: 테스트 시작 (8개 프레임 수집 → API 전송)
    1-9: 동작 변경
    Q: 종료
"""

import argparse
import base64
import io
import time

import cv2
import requests
from PIL import Image


ACTION_NAMES = {
    1: "손 박수",
    2: "팔 치기",
    4: "팔 뻗기",
    5: "기우뚱",
    6: "비상구",
    7: "겨드랑이박수",
    9: "가만히 있음",
}


def capture_frames(cap, count=8):
    """빠르게 N개 프레임 캡처 (초경량)"""
    frames = []
    print(f"\n📸 프레임 캡처 중...", end="", flush=True)

    for i in range(count):
        ret, frame = cap.read()
        if not ret:
            print(f"\n❌ 프레임 읽기 실패")
            return None

        # 해상도 축소 (160x120)
        small_frame = cv2.resize(frame, (160, 120))

        # BGR → RGB
        rgb = cv2.cvtColor(small_frame, cv2.COLOR_BGR2RGB)

        # PIL → JPEG → Base64
        pil_img = Image.fromarray(rgb)
        buffer = io.BytesIO()
        pil_img.save(buffer, format="JPEG", quality=50)
        buffer.seek(0)
        b64 = base64.b64encode(buffer.read()).decode("utf-8")

        frames.append(b64)
        print(".", end="", flush=True)

        time.sleep(0.08)  # 80ms 간격

    print(" ✅")
    print(f"📦 프레임 크기 예상: ~{len(frames[0]) * count / 1024:.1f}KB")
    return frames


def send_to_api(frames, action_code, action_name, api_url):
    """API 호출 (HTTPS)"""
    print(f"\n🔍 AI 분석 중 (배포 서버)...", end="", flush=True)

    payload = {
        "actionCode": action_code,
        "actionName": action_name,
        "frameCount": len(frames),
        "frames": frames,
    }

    start_time = time.time()

    try:
        response = requests.post(
            api_url,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=60,
            verify=True,  # HTTPS 인증서 검증
        )

        elapsed = time.time() - start_time

        if response.status_code == 200:
            result = response.json()
            print(f" ✅ ({elapsed:.1f}초)\n")
            print_result(result, action_code, action_name)
            return result
        else:
            error = response.json().get("detail", "Unknown error")
            print(f" ❌ ({elapsed:.1f}초)\n")
            print(f"HTTP {response.status_code}: {error}\n")
            return None

    except requests.exceptions.Timeout:
        elapsed = time.time() - start_time
        print(f" ❌\n타임아웃! (60초 초과)\n")
        return None
    except requests.exceptions.SSLError as e:
        print(f" ❌\nSSL 에러: {e}\n")
        return None
    except requests.exceptions.ConnectionError as e:
        print(f" ❌\n연결 실패: {e}\n")
        return None
    except Exception as e:
        print(f" ❌\n예외: {e}\n")
        import traceback
        traceback.print_exc()
        return None


def print_result(result, action_code, action_name):
    """결과 출력"""
    judgment = result.get("judgment", 0)
    predicted = result.get("predictedLabel", "N/A")
    confidence = result.get("confidence", 0) * 100
    target_prob = result.get("targetProbability")

    score_emoji = ["❌", "⚠️", "✅", "🎯"]

    print("=" * 60)
    print(f"🎯 목표 동작: {action_name} (코드: {action_code})")
    print(f"🤖 예측 동작: {predicted}")
    print(f"📊 예측 신뢰도: {confidence:.1f}%")

    if target_prob is not None:
        print(f"🎲 목표 확률: {target_prob * 100:.1f}%")

    print(f"\n⭐ 최종 점수: {judgment}점 {score_emoji[judgment]}")

    decode_ms = result.get("decodeTimeMs", 0)
    pose_ms = result.get("poseTimeMs", 0)
    inference_ms = result.get("inferenceTimeMs", 0)

    print(f"\n⏱️ 서버 처리 시간:")
    print(f"   디코딩: {decode_ms:.0f}ms")
    print(f"   Pose: {pose_ms:.0f}ms")
    print(f"   추론: {inference_ms:.0f}ms")
    print(f"   총: {decode_ms + pose_ms + inference_ms:.0f}ms")
    print("=" * 60 + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--url",
        default="https://heungbuja.site/motion/api/ai/analyze",
        help="배포 서버 Motion API URL"
    )
    parser.add_argument("--camera", type=int, default=0, help="카메라 인덱스")
    parser.add_argument("--action-code", type=int, default=1, help="동작 코드")
    args = parser.parse_args()

    api_url = args.url
    action_code = args.action_code
    action_name = ACTION_NAMES.get(action_code, "알 수 없음")

    # 웹캠 열기
    cap = cv2.VideoCapture(args.camera)
    if not cap.isOpened():
        print(f"❌ 카메라 {args.camera}를 열 수 없습니다.")
        return

    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    print("\n" + "=" * 60)
    print("🎥 배포 서버 웹캠 동작 테스트")
    print("=" * 60)
    print(f"🌐 배포 API: {api_url}")
    print(f"📹 카메라: {args.camera}")
    print(f"🎯 현재 동작: {action_name} (코드: {action_code})")
    print("\n키보드:")
    print("  - SPACE: 테스트 시작")
    print("  - 1-9: 동작 변경")
    print("  - Q: 종료")
    print("=" * 60 + "\n")

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            # 좌우 반전
            frame = cv2.flip(frame, 1)

            # 간단한 UI
            cv2.putText(
                frame,
                f"목표: {action_name} ({action_code}) [배포서버]",
                (10, 30),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.7,
                (0, 255, 0),
                2,
            )
            cv2.putText(
                frame,
                "SPACE: 테스트 시작 | Q: 종료",
                (10, frame.shape[0] - 10),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (255, 255, 255),
                1,
            )

            cv2.imshow("웹캠 테스트 (배포서버)", frame)

            key = cv2.waitKey(1) & 0xFF

            if key == ord("q"):
                print("\n👋 종료합니다...")
                break

            elif key == ord(" "):
                # 프레임 캡처 + API 호출
                frames = capture_frames(cap, count=8)
                if frames:
                    send_to_api(frames, action_code, action_name, api_url)

            elif key in [ord("1"), ord("2"), ord("4"), ord("5"), ord("6"), ord("7"), ord("9")]:
                code = int(chr(key))
                if code in ACTION_NAMES:
                    action_code = code
                    action_name = ACTION_NAMES[code]
                    print(f"\n🎯 동작 변경: {action_name} (코드: {action_code})\n")

    finally:
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
