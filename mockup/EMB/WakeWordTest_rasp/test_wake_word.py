import pvporcupine
from pvrecorder import PvRecorder

# !!! 중요 !!! 아래 두 값을 자신의 것으로 수정하세요.
ACCESS_KEY = "*********"
KEYWORD_PATH = "흥부야_ko_raspberry-pi_v3_0_0.ppn"
MODEL_PATH = "porcupine_params_ko.pv"     # 다운로드한 한국어 모델 파일명

try:
    # Porcupine 엔진 초기화
    porcupine = pvporcupine.create(
        access_key=ACCESS_KEY,
        keyword_paths=[KEYWORD_PATH],
        model_path=MODEL_PATH
    )

    # 마이크 녹음기 초기화
    recorder = PvRecorder(device_index=-1, frame_length=porcupine.frame_length)
    recorder.start()

    print("Picovoice Porcupine 엔진이 시작되었습니다. '흥부야'라고 말해보세요...")
    print("종료하려면 Ctrl+C 를 누르세요.")

    while True:
        # 마이크에서 오디오 프레임 읽기
        pcm = recorder.read()
        # Porcupine 엔진으로 Wake Word 감지
        result = porcupine.process(pcm)

        if result >= 0:
            print("'흥부야'가 감지되었습니다!")

except KeyboardInterrupt:
    print("프로그램을 종료합니다.")
finally:
    if 'recorder' in locals() and recorder is not None:
        recorder.delete()
    if 'porcupine' in locals() and porcupine is not None:
        porcupine.delete()