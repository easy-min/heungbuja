import time
import whisper
import edge_tts
import asyncio
import os
import torch
import pygame
import json
import librosa
import noisereduce as nr
import numpy as np
import soundfile as sf
from scipy import signal
from transformers import AutoTokenizer

# VoiceCommandModel은 반드시 별도 파일에서 import해야 함
from voice_command_model import VoiceCommandModel

# ============================================
# 음성 전처리 클래스
# ============================================
class AudioPreprocessor:
    """음성 전처리 클래스"""
    
    def __init__(self, target_sr=16000):
        self.target_sr = target_sr
    
    def load_audio(self, file_path):
        """오디오 파일 로드"""
        print(f"   📂 파일 로딩: {os.path.basename(file_path)}")
        try:
            # soundfile로 먼저 시도 (더 빠름)
            audio, sr = sf.read(file_path)
            if len(audio.shape) > 1:  # 스테레오 → 모노
                audio = audio.mean(axis=1)
        except:
            # 실패하면 librosa로 (더 많은 포맷 지원)
            audio, sr = librosa.load(file_path, sr=None, mono=True)
        
        print(f"      원본 SR: {sr}Hz, 길이: {len(audio)/sr:.2f}초")
        return audio, sr
    
    def resample(self, audio, orig_sr):
        """리샘플링"""
        if orig_sr != self.target_sr:
            print(f"   🔄 리샘플링: {orig_sr}Hz → {self.target_sr}Hz")
            audio = librosa.resample(audio, orig_sr=orig_sr, target_sr=self.target_sr)
        return audio
    
    def remove_noise(self, audio, sr, stationary=True):
        """잡음 제거"""
        print(f"   🔇 잡음 제거 중...")
        reduced_noise = nr.reduce_noise(
            y=audio, 
            sr=sr,
            stationary=stationary,
            prop_decrease=0.8
        )
        return reduced_noise
    
    def normalize_volume(self, audio, target_dBFS=-20.0):
        """음량 정규화"""
        print(f"   🔊 음량 정규화 중... (목표: {target_dBFS}dBFS)")
        rms = np.sqrt(np.mean(audio**2))
        current_dBFS = 20 * np.log10(rms) if rms > 0 else -np.inf
        
        target_rms = 10 ** (target_dBFS / 20)
        gain = target_rms / (rms + 1e-10)
        normalized = audio * gain
        
        # 클리핑 방지
        max_val = np.max(np.abs(normalized))
        if max_val > 1.0:
            normalized = normalized / max_val * 0.95
        
        print(f"      이전: {current_dBFS:.1f}dBFS → 이후: {target_dBFS:.1f}dBFS")
        return normalized
    
    def remove_silence(self, audio, sr, threshold_db=-40):
        """무음 구간 제거"""
        print(f"   ✂️  무음 구간 제거 중...")
        intervals = librosa.effects.split(
            audio,
            top_db=-threshold_db,
            frame_length=2048,
            hop_length=512
        )
        
        trimmed = np.concatenate([audio[start:end] for start, end in intervals])
        removed_duration = (len(audio) - len(trimmed)) / sr
        print(f"      제거: {removed_duration:.2f}초, 최종: {len(trimmed)/sr:.2f}초")
        return trimmed
    
    def apply_bandpass_filter(self, audio, sr, lowcut=80, highcut=7500):
        """대역통과 필터 (음성 주파수만 통과)"""
        print(f"   🎛️  대역통과 필터: {lowcut}Hz ~ {highcut}Hz")
        nyquist = sr / 2
        low = lowcut / nyquist
        high = min(highcut / nyquist, 0.99)  # Nyquist 주파수 미만으로 제한
        
        # 주파수 범위 유효성 검사
        if low >= high:
            print(f"      ⚠️  필터 범위 오류, 필터링 스킵")
            return audio
        
        try:
            b, a = signal.butter(5, [low, high], btype='band')
            filtered = signal.filtfilt(b, a, audio)
            return filtered
        except ValueError as e:
            print(f"      ⚠️  필터링 실패: {e}, 원본 반환")
            return audio
    
    def preprocess(self, file_path, output_path=None):
        """전체 전처리 파이프라인"""
        print("\n🎙️  음성 전처리 시작")
        preprocess_start = time.time()
        
        # 1. 로드
        audio, sr = self.load_audio(file_path)
        
        # 2. 리샘플링
        audio = self.resample(audio, sr)
        sr = self.target_sr
        
        # 3. 잡음 제거
        audio = self.remove_noise(audio, sr)
        
        # 4. 대역통과 필터
        audio = self.apply_bandpass_filter(audio, sr)
        
        # 5. 무음 구간 제거
        audio = self.remove_silence(audio, sr)
        
        # 6. 음량 정규화 (마지막에)
        audio = self.normalize_volume(audio)
        
        # 7. 저장
        if output_path:
            sf.write(output_path, audio, sr)
            print(f"   💾 저장: {os.path.basename(output_path)}")
        
        preprocess_time = time.time() - preprocess_start
        print(f"✓ 전처리 완료 ({preprocess_time:.2f}초)\n")
        
        return audio, sr, preprocess_time


# ============================================
# 설정
# ============================================
save_path = r"C:\Users\SSAFY\흥부자\S13P31A103\mockup\BE\saved_voice_command_model"

# ============================================
# GPU 설정 확인
# ============================================
print("=" * 60)
print("🖥️  GPU 설정 확인")
print("=" * 60)
print(f"CUDA 사용 가능: {torch.cuda.is_available()}")
print(f"GPU 개수: {torch.cuda.device_count()}")
if torch.cuda.is_available():
    print(f"GPU 이름: {torch.cuda.get_device_name(0)}")
else:
    print("⚠️  GPU 없음 - CPU 모드로 실행")

# ============================================
# 모델 로딩
# ============================================
print("\n" + "=" * 60)
print("📦 모델 로딩 중...")
print("=" * 60)

with open(f"{save_path}/config.json", "r", encoding="utf-8") as f:
    config = json.load(f)

ner_tag2id = config["ner_tag2id"]
ner_id2tag = {v: k for k, v in ner_tag2id.items()}
intent2id = config["intent2id"]
id2intent = {v: k for k, v in intent2id.items()}
model_name = config["model_name"]

print(f"✓ Config 로드 완료")
print(f"  - NER Tags: {len(ner_tag2id)}개")
print(f"  - Intents: {len(intent2id)}개")
print(f"  - Base Model: {model_name}")

# 토크나이저 로드
tokenizer = AutoTokenizer.from_pretrained(save_path)
print(f"✓ Tokenizer 로드 완료")

# 모델 인스턴스 생성 후 가중치 로드
model_ner_intent = VoiceCommandModel(
    model_name=model_name,
    num_ner_tags=len(ner_tag2id),
    num_intents=len(intent2id)
)
model_ner_intent.load_state_dict(
    torch.load(f"{save_path}/pytorch_model.bin", map_location="cpu")
)
model_ner_intent.eval()
print(f"✓ NER/Intent 모델 로드 완료")

# Whisper 모델 로딩
print(f"✓ Whisper 모델 로딩 중...")
model_whisper = whisper.load_model("medium")
print(f"✓ Whisper 모델 로드 완료")

# 전처리기 초기화
preprocessor = AudioPreprocessor(target_sr=16000)
print(f"✓ 음성 전처리기 초기화 완료")

# ============================================
# 유틸리티 함수
# ============================================

def generate_feedback(command_type):
    """피드백 메시지 생성"""
    feedbacks = {
        "PLAY_MUSIC": "네~ 노래 틀어드릴게요!",
        "START_EXERCISE": "좋아요! 체조 모드로 바꿀게요.",
        "PAUSE": "잠깐 멈출게요.",
        "RESUME": "계속 들려드릴게요~",
        "NEXT_SONG": "다음 곡으로 넘어갈게요!",
        "STOP_ALL": "알겠어요. 종료할게요.",
        "START_LISTENING": "음악 감상 모드로 바꿀게요.",
        "SWITCH_TO_EXERCISE": "운동 모드로 전환할게요.",
        "SWITCH_TO_LISTENING": "음악 감상 모드로 전환할게요.",
        "EMERGENCY": "괜찮으세요? 대답해주세요! 지금 도움을 요청할게요!",
        "UNKNOWN": "잘 못 들었어요. 다시 한번 말씀해주세요~"
    }
    return feedbacks.get(command_type, "명령을 실행했습니다.")

def play_audio_with_pygame(file_path):
    """pygame으로 오디오 재생"""
    pygame.mixer.init()
    pygame.mixer.music.load(file_path)
    pygame.mixer.music.play()
    while pygame.mixer.music.get_busy():
        pygame.time.Clock().tick(10)

async def tts_and_play(text, voice="ko-KR-JiMinNeural", filename="output.mp3"):
    """TTS 음성 합성 및 재생"""
    tts = edge_tts.Communicate(
        text,
        voice=voice,
        rate="+10%",
        pitch="+5Hz"
    )
    await tts.save(filename)
    play_audio_with_pygame(filename)

def extract_entities(tokens, ner_tags):
    """토큰에서 엔티티 추출"""
    entities = []
    current_entity = None
    
    for token, tag in zip(tokens, ner_tags):
        # [CLS], [SEP], [PAD] 등 특수 토큰 제외
        if token in ['[CLS]', '[SEP]', '[PAD]']:
            continue
            
        if tag.startswith("B-"):
            if current_entity:
                entities.append(current_entity)
            current_entity = {
                "text": token.replace("##", ""), 
                "type": tag[2:]
            }
        elif tag.startswith("I-") and current_entity:
            current_entity["text"] += token.replace("##", "")
        else:
            if current_entity:
                entities.append(current_entity)
                current_entity = None
    
    if current_entity:
        entities.append(current_entity)
    
    return entities

# Intent 매핑
INTENT_TO_ACTION = {
    'SELECT_BY_ARTIST_TITLE': 'PLAY_MUSIC',
    'SELECT_BY_ARTIST': 'PLAY_MUSIC',
    'SELECT_BY_TITLE': 'PLAY_MUSIC',
    'NEXT_SONG': 'NEXT_SONG',
    'PAUSE': 'PAUSE',
    'RESUME': 'RESUME',
    'STOP': 'STOP_ALL',
    'START_LISTENING': 'START_LISTENING',
    'START_EXERCISE': 'START_EXERCISE',
    'SWITCH_TO_EXERCISE': 'SWITCH_TO_EXERCISE',
    'SWITCH_TO_LISTENING': 'SWITCH_TO_LISTENING',
    'EMERGENCY': 'EMERGENCY',
    'NONE': 'UNKNOWN'
}

# ============================================
# 메인 처리 파이프라인
# ============================================
print("\n" + "=" * 60)
print("🎤 음성 명령 처리 시작")
print("=" * 60)

# 전체 파이프라인 시작 시간
pipeline_start = time.time()

audio_path = r"C:\Users\SSAFY\Documents\소리 녹음\test5_voice.m4a"
processed_path = audio_path.replace(".m4a", "_processed.wav")

print(f"\n📂 오디오 파일: {os.path.basename(audio_path)}")

# --- 0-1. 전처리 전 음성 인식 (비교용) ---
print("\n" + "=" * 60)
print("📊 [비교] 전처리 전 음성 인식")
print("=" * 60)

stt_before_start = time.time()
result_before = model_whisper.transcribe(audio_path)
text_before = result_before['text']
stt_before_time = time.time() - stt_before_start

print(f"✓ 인식 완료 ({stt_before_time:.2f}초)")
print(f"📝 인식된 텍스트: '{text_before}'")

# --- 0-2. 음성 전처리 ---
print("\n" + "=" * 60)
print("🎙️  음성 전처리 적용")
print("=" * 60)

processed_audio, sr, preprocess_time = preprocessor.preprocess(
    file_path=audio_path,
    output_path=processed_path
)

# --- 1. 전처리 후 음성 인식 (STT) ---
print("\n" + "=" * 60)
print("📊 전처리 후 음성 인식")
print("=" * 60)

stt_start = time.time()
result = model_whisper.transcribe(processed_path)  # 전처리된 파일 사용
input_text = result['text']
stt_time = time.time() - stt_start

print(f"✓ 음성 인식 완료 ({stt_time:.2f}초)")
print(f"📝 인식된 텍스트: '{input_text}'")

# --- 전처리 효과 비교 ---
print("\n" + "=" * 60)
print("📈 전처리 효과 비교")
print("=" * 60)
print(f"\n🔴 전처리 전:")
print(f"   \"{text_before}\"")
print(f"\n🟢 전처리 후:")
print(f"   \"{input_text}\"")

# 텍스트 변화 분석
if text_before != input_text:
    print(f"\n✨ 변화 감지!")
    # 글자 수 비교
    print(f"   길이: {len(text_before)} → {len(input_text)} 글자")
    # 유사도 계산 (간단한 방법)
    common_chars = sum(1 for a, b in zip(text_before, input_text) if a == b)
    if len(text_before) > 0:
        similarity = common_chars / max(len(text_before), len(input_text)) * 100
        print(f"   유사도: {similarity:.1f}%")
else:
    print(f"\n   (동일한 결과)")

print("=" * 60)

# --- 2. NLU (Intent & NER) ---
print(f"\n🧠 의도 분석 중...")
nlu_start = time.time()

inputs = tokenizer(input_text, return_tensors="pt", padding=True, truncation=True)

with torch.no_grad():
    outputs = model_ner_intent(
        input_ids=inputs["input_ids"],
        attention_mask=inputs["attention_mask"]
    )

# Intent 예측
intent_logits = outputs["intent_logits"][0]
intent_probs = torch.softmax(intent_logits, dim=-1)
intent_pred = torch.argmax(intent_logits).item()
intent_label = id2intent[intent_pred]
intent_confidence = intent_probs[intent_pred].item()

# NER 예측
ner_preds = torch.argmax(outputs["ner_logits"], dim=-1)[0].tolist()
tokens = tokenizer.convert_ids_to_tokens(inputs["input_ids"][0])
ner_tags = [ner_id2tag.get(p, "O") for p in ner_preds]

# 엔티티 추출
entities = extract_entities(tokens, ner_tags)

nlu_time = time.time() - nlu_start

print(f"✓ 의도 분석 완료 ({nlu_time:.4f}초)")

# --- 3. 결과 출력 (디버깅 정보) ---
print("\n" + "=" * 60)
print("📊 분석 결과")
print("=" * 60)

print(f"\n🎯 Intent 예측:")
print(f"   ▶ {intent_label} (확신도: {intent_confidence:.1%})")

# Top-3 후보 표시
top3 = torch.topk(intent_probs, k=min(3, len(intent_probs)))
print(f"\n   Top-3 후보:")
for idx, prob in zip(top3.indices, top3.values):
    intent_name = id2intent[idx.item()]
    is_predicted = "✓" if idx.item() == intent_pred else " "
    print(f"   {is_predicted} {intent_name:25} {prob.item():.1%}")

# 엔티티 출력
print(f"\n🏷️  추출된 엔티티 ({len(entities)}개):")
if entities:
    for ent in entities:
        print(f"   [{ent['type']:6}] {ent['text']}")
else:
    print(f"   (없음)")

# 토큰별 NER 태그 (상세)
print(f"\n📝 토큰별 NER 태깅:")
for token, tag in zip(tokens, ner_tags):
    if token not in ['[CLS]', '[SEP]', '[PAD]']:
        print(f"   {token:15} → {tag}")

# --- 4. 응답 생성 및 TTS ---
command_type = INTENT_TO_ACTION.get(intent_label, 'UNKNOWN')
feedback_text = generate_feedback(command_type)

print(f"\n💬 시스템 응답: '{feedback_text}'")
print(f"🔊 음성 합성 및 재생 중...")

tts_start = time.time()
asyncio.run(tts_and_play(feedback_text))
tts_time = time.time() - tts_start

print(f"✓ 응답 완료 ({tts_time:.2f}초)")

# --- 5. 음악 재생 (PLAY_MUSIC인 경우) ---
if command_type == 'PLAY_MUSIC':
    music_file_path = r"C:\Users\SSAFY\흥부자\S13P31A103\mockup\BE\AI_나이가 어때서.mp3"
    
    artist = next((e['text'] for e in entities if e['type'] == 'ARTIST'), None)
    song = next((e['text'] for e in entities if e['type'] == 'SONG'), None)
    
    print(f"\n🎵 음악 재생:")
    if artist or song:
        print(f"   요청: {artist or '(미지정)'} - {song or '(미지정)'}")
    print(f"   파일: {os.path.basename(music_file_path)}")
    
    play_audio_with_pygame(music_file_path)

# ============================================
# 성능 측정 결과
# ============================================
total_time = time.time() - pipeline_start

print("\n" + "=" * 60)
print("⏱️  성능 측정 결과")
print("=" * 60)
print(f"   STT (전처리 전):    {stt_before_time:6.2f}초")
print(f"   음성 전처리:        {preprocess_time:6.2f}초  ({preprocess_time/total_time*100:5.1f}%)")
print(f"   STT (전처리 후):    {stt_time:6.2f}초  ({stt_time/total_time*100:5.1f}%)")
print(f"   NLU (Intent+NER):   {nlu_time:6.4f}초  ({nlu_time/total_time*100:5.1f}%)")
print(f"   TTS (응답 생성):    {tts_time:6.2f}초  ({tts_time/total_time*100:5.1f}%)")
print("   " + "-" * 56)
print(f"   총 소요 시간:       {total_time:6.2f}초")

# 전처리 효과 요약
print("\n" + "=" * 60)
print("📊 전처리 효과 요약")
print("=" * 60)
print(f"   인식 텍스트 변화: {'있음 ✓' if text_before != input_text else '없음'}")
if text_before != input_text:
    print(f"   전: \"{text_before[:50]}{'...' if len(text_before) > 50 else ''}\"")
    print(f"   후: \"{input_text[:50]}{'...' if len(input_text) > 50 else ''}\"")
print(f"   전처리 시간 비용: {preprocess_time:.2f}초")
print("=" * 60)

print("\n✅ 모든 처리 완료!")