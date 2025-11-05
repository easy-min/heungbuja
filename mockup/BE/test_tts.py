import time
import subprocess

def tts_generation():
    gms_key = input("GMS API 키를 입력하세요: ").strip()
    tts_text = "네! 아이유의 좋은 날 노래를 틀어드릴게요"
    curl_command = [
        "curl",
        "https://gms.ssafy.io/gmsapi/api.openai.com/v1/audio/speech",
        "-H", "Content-Type: application/json",
        "-H", f"Authorization: Bearer {gms_key}",
        "-d", f'{{"model": "gpt-4o-mini-tts", "input": "{tts_text}", "voice": "nova", "response_format": "mp3"}}',
        "--output", "greeting.mp3"
    ]

    start = time.time()
    result = subprocess.run(curl_command, capture_output=True, text=True)
    end = time.time()

    print(f"TTS 생성 소요 시간: {end - start:.2f}초")
    if result.returncode != 0:
        print("실행 중 오류 발생:")
        print(result.stderr)
    else:
        print("음성 파일 greeting.mp3 생성 완료")

if __name__ == "__main__":
    tts_generation()
