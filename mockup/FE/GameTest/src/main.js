// ===== 설정 =====
const VIDEO_BPM = 112;                         // 영상(실측) BPM
const LOOP_BEATS = 8;
const LOOP_LEN  = (60 / VIDEO_BPM) * LOOP_BEATS; // ≈ 4.2333s
const START_OFFSET = 0.0; // 영상 시작 지연(초) - 박 시점 이후 0.15s

// ===== 요소 =====
const video     = document.getElementById('motion');
const audioEl   = document.getElementById('music');
const musicSel  = document.getElementById('musicSelect');

// 버튼
const btnBar1Beat1 = document.getElementById('btnBar1Beat1');
const btnBeat2     = document.getElementById('btnBeat2');
const btnBeat3     = document.getElementById('btnBeat3');
const btnBeat4     = document.getElementById('btnBeat4');
const btnBar2Beat1 = document.getElementById('btnBar2Beat1');

// ===== 상태 =====
let audioCtx, mediaSrc;
let SONG_BPM = 131.9055;      // JSON에서 갱신
let baseRate = 1.0;           // = SONG_BPM / VIDEO_BPM
let beats = [];               // [{i,bar,beat,t}, ...]
let t0 = 0;                   // 오디오 시계 기준 "영상 시작 시각"

// --- 유틸 ---
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const getSelectedMp3 = () => musicSel.value.trim();
const baseName = (filename) => filename.replace(/\.[^/.]+$/, '');
const jsonUrlFromMp3 = (mp3) => encodeURI(`${baseName(mp3)}.json`); // 공백/한글 안전

// 선택된 음원으로 오디오 src 교체
function applySelectedAudio() {
  const mp3 = getSelectedMp3();
  audioEl.pause();
  // audio <source>를 직접 바꾸지 않아도 audio.src로 교체 가능
  audioEl.src = encodeURI(mp3);
  audioEl.load();                      // 소스 교체 반영
  // 시작 위치 초기화
  audioEl.currentTime = 0;
  console.log(`[AUDIO] source -> ${mp3}`);
}

// JSON 로드 (선택된 mp3와 동일한 이름의 json)
async function loadBeatGrid() {
  const jsonUrl = jsonUrlFromMp3(getSelectedMp3());
  try {
    const res = await fetch(jsonUrl, { cache: 'no-store' });
    if (!res.ok) throw new Error(`fetch ${jsonUrl} ${res.status}`);
    const data = await res.json();

    SONG_BPM = data?.tempoMap?.[0]?.bpm ?? SONG_BPM;
    beats    = Array.isArray(data?.beats) ? data.beats : [];
    baseRate = SONG_BPM / VIDEO_BPM;

    console.log(`[LOAD] ${jsonUrl} -> SONG_BPM=${SONG_BPM.toFixed(3)} baseRate=${baseRate.toFixed(4)} beats=${beats.length}`);
  } catch (e) {
    console.warn(`[LOAD] ${jsonUrl} 읽기 실패. 기본값 사용`, e);
    // 최소 동작을 위해 기본값 유지
    beats = [];
    baseRate = SONG_BPM / VIDEO_BPM;
  }
}

// 싱크 루프 (오디오=마스터, 비디오=추종)
function startSyncLoop() {
  const KP = 0.35;        // 미세 추종 강도
  const MICRO = 0.03;     // 프레임당 보정 상한(초)

  const shortestSignedDelta = (a, b, period) => {
    let d = a - b;
    if (d >  period / 2) d -= period;
    if (d < -period / 2) d += period;
    return d;
  };

  const loop = () => {
    const audioElapsed = Math.max(0, audioCtx.currentTime - t0);
    const idealPhase   = (audioElapsed * (VIDEO_BPM / SONG_BPM)) % LOOP_LEN; // 0~LOOP_LEN
    const actualPhase  = video.currentTime % LOOP_LEN;
    const drift        = shortestSignedDelta(idealPhase, actualPhase, LOOP_LEN);

    const microAdjust  = clamp(drift * KP, -MICRO, MICRO);
    video.playbackRate = baseRate + microAdjust * 0.8;

    video.requestVideoFrameCallback(loop);
  };
  video.requestVideoFrameCallback(loop);
}

// 공통: 오디오/비디오 준비 + 특정 시각(targetTimeSec)에 영상 0초 시작
async function armStartAt(targetTimeSec) {
  await loadBeatGrid();

  // 오디오 라우팅
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    await audioCtx.resume();
  }
  if (!mediaSrc) {
    mediaSrc = audioCtx.createMediaElementSource(audioEl);
    mediaSrc.connect(audioCtx.destination);
  }

  // 오디오 재생 (현재 위치 유지)
  if (audioEl.paused) await audioEl.play();

  // 비디오 준비
  video.pause();
  video.currentTime = 0;
  video.playbackRate = baseRate;

  // 오디오 엘리먼트 시간 기준 딜레이 계산
  const targetWithOffset = targetTimeSec + START_OFFSET;
  const nowEl = audioEl.currentTime;
  const delaySec = Math.max(0, targetWithOffset - nowEl);
  const startAtAudioCtxTime = audioCtx.currentTime + delaySec;

  // 기준 시각 저장: "이때 영상이 0초에서 시작했다"로 간주
  t0 = startAtAudioCtxTime;

  const startVideo = () => {
    video.play().then(() => startSyncLoop());
  };

  // 느슨한 예약 + 근접 폴링
  if (delaySec > 0.03) {
    setTimeout(() => {
      const guard = () => {
        const remain = startAtAudioCtxTime - audioCtx.currentTime;
        if (remain <= 0.005) startVideo();
        else requestAnimationFrame(guard);
      };
      requestAnimationFrame(guard);
    }, (delaySec - 0.03) * 1000);
  } else {
    startVideo();
  }

  console.log(`[ARMED] now=${nowEl.toFixed(3)}s → start@${targetTimeSec.toFixed(3)}s, baseRate=${baseRate.toFixed(4)}`);
}

// ---- 찾기 함수들 ----

// 1) 특정 마디/박(t) 찾기 (정확히 그 bar/beat)
function findBarBeatTime(bar, beat) {
  const bb = beats.find(b => b.bar === bar && b.beat === beat);
  return bb ? bb.t : null;
}

// 2) "현재 이후"의 특정 박(2/3/4박) 찾기 (마디 무관, 다음으로 다가오는 해당 박)
function findNextBeatNumberTime(beatNum) {
  const now = audioEl.currentTime;
  let next = beats.find(b => b.t >= now && b.beat === beatNum);
  if (!next) next = beats.find(b => b.t >= now); // 폴백
  return next ? next.t : null;
}

// ---- 이벤트: 음원 선택 시 소스/JSON 동기 교체 ----
musicSel.addEventListener('change', async () => {
  applySelectedAudio();
  await loadBeatGrid();
  // 선택만 바꾸면 자동 재생하지 않고 대기
});

// ---- 버튼 핸들러 ----
btnBar1Beat1.addEventListener('click', async () => {
  await loadBeatGrid();
  const t = findBarBeatTime(1, 1) ?? (beats[0]?.t ?? 0);
  armStartAt(t);
});

btnBeat2.addEventListener('click', async () => {
  await loadBeatGrid();
  const t = findNextBeatNumberTime(2) ?? (beats[0]?.t ?? 0);
  armStartAt(t);
});

btnBeat3.addEventListener('click', async () => {
  await loadBeatGrid();
  const t = findNextBeatNumberTime(3) ?? (beats[0]?.t ?? 0);
  armStartAt(t);
});

btnBeat4.addEventListener('click', async () => {
  await loadBeatGrid();
  const t = findNextBeatNumberTime(4) ?? (beats[0]?.t ?? 0);
  armStartAt(t);
});

btnBar2Beat1.addEventListener('click', async () => {
  await loadBeatGrid();
  const t = findBarBeatTime(2, 1);
  armStartAt(t ?? (beats.find(b => b.beat===1)?.t ?? 0));
});

// 초기 상태: select의 기본값 반영
applySelectedAudio();
loadBeatGrid();
