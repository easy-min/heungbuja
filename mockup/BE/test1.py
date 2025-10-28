import time
import whisper
import edge_tts
import asyncio
import os
import torch
import pygame

# GPU 사용 가능 여부 출력
print(torch.cuda.is_available())
print(torch.cuda.device_count())
print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else "No GPU")

# 음성 명령 키워드 리스트
PLAY_MUSIC_KEYWORDS = ["노래 틀", "노래를 틀", "음악 틀"]
START_EXERCISE_KEYWORDS = ["체조 시작", "운동할래"]
PAUSE_KEYWORDS = ["그만", "멈춰", "일시정지"]
RESUME_KEYWORDS = ["계속", "다시 틀어줘"]
NEXT_SONG_KEYWORDS = ["다음"]
STOP_ALL_KEYWORDS = ["종료", "끝"]
EMERGENCY_KEYWORDS = ["도와줘", "살려줘"]

# 명령 분기 함수
def command_match(text):
    text = text.lower()
    if any(k in text for k in PLAY_MUSIC_KEYWORDS):
        return "PLAY_MUSIC"
    elif any(k in text for k in START_EXERCISE_KEYWORDS):
        return "START_EXERCISE"
    elif any(k in text for k in PAUSE_KEYWORDS):
        return "PAUSE"
    elif any(k in text for k in RESUME_KEYWORDS):
        return "RESUME"
    elif any(k in text for k in NEXT_SONG_KEYWORDS):
        return "NEXT_SONG"
    elif any(k in text for k in STOP_ALL_KEYWORDS):
        return "STOP_ALL"
    elif any(k in text for k in EMERGENCY_KEYWORDS):
        return "EMERGENCY"
    else:
        return "UNKNOWN"

# 피드백 메시지 생성
def generate_feedback(command_type):
    feedbacks = {
        "PLAY_MUSIC": "네~ 노래 틀어드릴게요!",
        "START_EXERCISE": "좋아요! 체조 모드로 바꿀게요.",
        "PAUSE": "잠깐 멈출게요.",
        "RESUME": "계속 들려드릴게요~",
        "NEXT_SONG": "다음 곡으로 넘어갈게요!",
        "STOP_ALL": "알겠어요. 종료할게요.",
        "EMERGENCY": "괜찮으세요? 대답해주세요! 지금 도움을 요청할게요!",
        "UNKNOWN": "잘 못 들었어요. 다시 한번 말씀해주세요~"
    }
    return feedbacks.get(command_type, "명령을 실행했습니다.")

# pygame 초기화 및 재생 함수
def play_audio_with_pygame(file_path):
    pygame.mixer.init()
    pygame.mixer.music.load(file_path)
    pygame.mixer.music.play()
    while pygame.mixer.music.get_busy():
        pygame.time.Clock().tick(10)

# Edge TTS 음성 합성 및 재생 (블로킹)
async def tts_and_play(text, voice="ko-KR-JiMinNeural", filename="output.mp3"):
    tts = edge_tts.Communicate(
        text,
        voice=voice,
        rate="+10%",
        pitch="+5Hz"
    )
    await tts.save(filename)
    play_audio_with_pygame(filename)  # 블로킹 재생

# Whisper 모델 로드
print("\n🎤 Whisper 모델 로딩 중...")
model = whisper.load_model("medium")
print("✅ 모델 로드 완료!\n")

# 음성 인식 시작
audio_path = r"C:\Users\SSAFY\Documents\소리 녹음\test8_voice.m4a"
print("🔊 음성 인식 시작...")
start = time.time()
result = model.transcribe(audio_path)
end = time.time()

print(f"⏱️  처리 시간: {end - start:.2f}초")
print(f"📝 인식된 텍스트: {result['text']}")

command_type = command_match(result["text"])
print(f"🎯 명령 타입: {command_type}")

# 피드백 음성 재생
feedback_text = generate_feedback(command_type)
print(f"💬 응답: {feedback_text}")
print("\n🔊 음성 재생 중...")
asyncio.run(tts_and_play(feedback_text))

# 노래 재생 (명령어에 따라)
if command_type == "PLAY_MUSIC":
    music_file_path = r"C:\Users\SSAFY\흥부자\S13P31A103\mockup\BE\AI_나이가 어때서.mp3"
    print(f"🎵 {music_file_path} 재생 시작...")
    play_audio_with_pygame(music_file_path)  # 블로킹 재생

print("✅ 완료!")
