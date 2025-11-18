"""
오디오 분석 서비스
가사 분석기 - 복사본의 analyze_audio 로직을 FastAPI에 맞게 변환
"""
import numpy as np
import librosa
import soundfile as sf
from scipy.signal import butter, filtfilt, find_peaks
from typing import List, Tuple, Dict, Any


def butter_bandpass(lowcut, highcut, fs, order=4):
    ny = 0.5 * fs
    low = lowcut / ny
    high = highcut / ny
    b, a = butter(order, [low, high], btype="band")
    return b, a


def lowpass_env(x, fs, cutoff_hz):
    b, a = butter(2, cutoff_hz / (0.5 * fs), btype='low')
    return filtfilt(b, a, x)


def bpm_from_beats(beats):
    if len(beats) < 2:
        return 0.0
    ibi = np.median(np.diff(beats))
    return 60.0 / ibi if ibi > 0 else 0.0


def calculate_quality(beats):
    if len(beats) < 4:
        return 0.0
    intervals = np.diff(beats)
    cv = np.std(intervals) / (np.mean(intervals) + 1e-9)
    return max(0.0, 1.0 - cv)


def find_nearest_beat_index(time_sec, beats):
    if len(beats) == 0:
        return 1
    idx = np.argmin(np.abs(beats - time_sec))
    return int(idx + 1)


def analyze_audio(audio_path: str, lyrics_lines: List[str], song_id: int = 1, song_title: str = "Unknown") -> Tuple[Dict[str, Any], Dict[str, Any], float]:
    """
    오디오 파일 분석 메인 함수

    Args:
        audio_path: 오디오 파일 경로
        lyrics_lines: 가사 라인 리스트
        song_id: 곡 ID
        song_title: 곡 제목

    Returns:
        (beats_json, lyrics_json, duration_sec)
    """
    # 파라미터
    TARGET_SR = 44100
    HPF_HZ, LPF_HZ = 200.0, 5000.0
    ENV_SMOOTH_SEC = 0.20
    VALLEY_PROMINENCE = 0.02
    VALLEY_SEARCH_WIN = 2.5
    BEAT_SNAP_AHEAD_MAX = 0.20
    TRIM_TOP_DB = 30
    METER = 4

    print("🎵 오디오 로드 중...")
    y, sr = librosa.load(audio_path, sr=TARGET_SR, mono=True)
    duration_sec = len(y) / sr

    print("🎛 전처리 중...")
    y_h, y_p = librosa.effects.hpss(y)
    y_h = librosa.util.normalize(y_h)
    y_h_trim, trim_idx = librosa.effects.trim(y_h, top_db=TRIM_TOP_DB)
    start_time = trim_idx[0] / sr

    print("🎼 비트 검출 중...")
    tempo, beats_librosa = librosa.beat.beat_track(y=y_h_trim, sr=sr, units='time')
    beats_librosa = beats_librosa + start_time

    chosen_beats = beats_librosa
    chosen_engine = "librosa"

    bpm_final = bpm_from_beats(chosen_beats)
    print(f"   엔진: {chosen_engine}, BPM: {bpm_final:.1f}, 비트 수: {len(chosen_beats)}")

    print("🎤 보컬 엔벨로프 생성 중...")
    b, a = butter_bandpass(HPF_HZ, LPF_HZ, sr, order=4)
    yh_bp = filtfilt(b, a, y_h)

    frame_len = int(0.046 * sr)
    hop_len = int(0.010 * sr)
    rms = librosa.feature.rms(y=yh_bp, frame_length=frame_len, hop_length=hop_len, center=True)[0]
    times_env = librosa.frames_to_time(np.arange(len(rms)), sr=sr, hop_length=hop_len)

    env = rms / (np.max(rms) + 1e-9)
    env = lowpass_env(env, 1.0 / (times_env[1] - times_env[0]), cutoff_hz=1.0 / ENV_SMOOTH_SEC)
    env = np.clip(env, 0, 1)

    print("📝 가사 경계 추정 중...")
    N = len(lyrics_lines)
    cum = np.cumsum(env)
    cum = cum / (cum[-1] + 1e-9)
    targets = np.linspace(0, 1, N + 1)
    boundary_times = []

    neg_env = 1.0 - env
    peaks, props = find_peaks(neg_env, prominence=VALLEY_PROMINENCE,
                               distance=int(0.25 / (times_env[1] - times_env[0])))

    for tgt in targets:
        idx = int(np.searchsorted(cum, tgt))
        idx = np.clip(idx, 0, len(times_env) - 1)
        t0 = times_env[idx]

        win_mask = (times_env >= t0 - VALLEY_SEARCH_WIN) & (times_env <= t0 + VALLEY_SEARCH_WIN)
        cand_idx = np.where(win_mask)[0]

        if len(cand_idx) == 0 or len(peaks) == 0:
            boundary_times.append(float(np.clip(t0, 0, duration_sec)))
            continue

        cand_peaks = [p for p in peaks if cand_idx[0] <= p <= cand_idx[-1]]
        if not cand_peaks:
            boundary_times.append(float(np.clip(t0, 0, duration_sec)))
            continue

        sub = np.array(cand_peaks)
        dist = np.abs(times_env[sub] - t0)
        depth = neg_env[sub]
        score = 0.6 * (1.0 - dist / (VALLEY_SEARCH_WIN + 1e-6)) + 0.4 * (depth / (np.max(depth) + 1e-9))
        best = sub[np.argmax(score)]
        boundary_times.append(float(np.clip(times_env[best], 0, duration_sec)))

    boundary_times[0] = 0.0 + start_time
    boundary_times[-1] = duration_sec
    for i in range(1, len(boundary_times)):
        boundary_times[i] = max(boundary_times[i], boundary_times[i - 1] + 0.05)

    print("🎯 비트 스냅 적용 중...")
    beats_all = np.array(chosen_beats, dtype=float)

    def snap_to_next_beat(t, beats, max_ahead=0.2):
        idx = np.searchsorted(beats, t)
        if idx < len(beats):
            nxt = beats[idx]
            if 0 <= (nxt - t) <= max_ahead:
                return float(nxt)
        return float(t)

    starts = []
    ends = []
    for i in range(N):
        s = boundary_times[i]
        e = boundary_times[i + 1]
        s2 = snap_to_next_beat(s, beats_all, BEAT_SNAP_AHEAD_MAX)
        e2 = max(s2 + 0.05, e)
        starts.append(s2)
        ends.append(e2)

    starts = [max(0.0, min(duration_sec, t)) for t in starts]
    ends = [max(0.0, min(duration_sec, t)) for t in ends]

    print("📊 JSON 생성 중...")
    # 박자 JSON
    beats_list = []
    for i, t in enumerate(chosen_beats, 1):
        bar = (i - 1) // METER + 1
        beat_in_bar = (i - 1) % METER + 1
        beats_list.append({
            "i": i,
            "bar": int(bar),
            "beat": int(beat_in_bar),
            "t": float(t)
        })

    beats_json = {
        "songId": song_id,
        "audio": {
            "title": song_title,
            "durationSec": float(duration_sec)
        },
        "tempoMap": [{"t": 0.0, "bpm": float(bpm_final)}],
        "beats": beats_list,
        "sections": []
    }

    # 가사 JSON
    lyrics_list = []
    for i, line in enumerate(lyrics_lines):
        start_sec = int(round(starts[i]))
        end_sec = int(round(ends[i]))
        start_beat = find_nearest_beat_index(starts[i], chosen_beats)
        end_beat = find_nearest_beat_index(ends[i], chosen_beats)

        lyrics_list.append({
            "lineIndex": i + 1,
            "text": line,
            "start": start_sec,
            "end": end_sec,
            "sBeat": start_beat,
            "eBeat": end_beat
        })

    lyrics_json = {
        "songId": song_id,
        "title": song_title,
        "lines": lyrics_list
    }

    return beats_json, lyrics_json, duration_sec
