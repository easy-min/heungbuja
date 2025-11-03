// ==============================
// main.js (전체 코드)
// ==============================

// ===== 전역 설정 =====
const START_OFFSET = 0.0; // 오디오-비디오 시작 오프셋(초)

// 🎬 섹션별 메타(파일, 영상 BPM, 루프 박자 수)
//  - 모든 영상이 100BPM이면 bpm: 100으로 통일
//  - 루프가 8박 기준이면 loopBeats: 8 (필요 시 섹션별로 수정)
const VIDEO_META = {
  intro: { src: 'break.mp4', bpm: 100, loopBeats: 8 },
  break: { src: 'break.mp4', bpm: 100, loopBeats: 8 },
  part1: { src: 'part1.mp4', bpm: 100, loopBeats: 8 },
  part2: { src: 'part2.mp4', bpm: 100, loopBeats: 8 },
};

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

// === 가사 DOM
const $lyPrev = document.getElementById('lyricPrev');
const $lyCurr = document.getElementById('lyricCurrent');
const $lyNext = document.getElementById('lyricNext');

// ===== 상태 =====
let audioCtx, mediaSrc;
let SONG_BPM = 131.9055;      // JSON에서 갱신
let beats = [];               // [{i,bar,beat,t}, ...] (t: 초)
let sections = [];            // [{label,startBeat,endBeat,startBar,endBar,lineRange}, ...]
let t0 = 0;                   // 오디오 기준 "영상 0초" 시각
let currentSection = null;    // 'intro' | 'break' | 'part1' | 'part2'

// 가사
let lyrics = [];              // [{line,start,end}]
let lyricIdx = -1;

// ===== 유틸 =====
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const getSelectedMp3 = () => musicSel.value.trim();
const baseName = (filename) => filename.replace(/\.[^/.]+$/, '');
const jsonUrlFromMp3 = (mp3) => encodeURI(`${baseName(mp3)}.json`);
const lyricsJsonUrlFromMp3 = (mp3) => encodeURI(`${baseName(mp3)}_가사.json`);

// 현재 섹션 메타 + 파생값
function getVideoMeta(section = currentSection) {
  return VIDEO_META[section] ?? VIDEO_META.part1;
}
function getLoopLenSec(section = currentSection) {
  const { bpm, loopBeats } = getVideoMeta(section);
  return (60 / bpm) * loopBeats;
}
function getBaseRate(section = currentSection) {
  const { bpm } = getVideoMeta(section);
  return SONG_BPM / bpm; // 노래 BPM / 해당 섹션 영상 BPM
}

// ===== 오디오 소스 교체 =====
function applySelectedAudio() {
  const mp3 = getSelectedMp3();
  audioEl.pause();
  audioEl.src = encodeURI(mp3);
  audioEl.load();
  audioEl.currentTime = 0;
  console.log(`[AUDIO] source -> ${mp3}`);
}

// ===== 비트/섹션 JSON 로드 =====
async function loadBeatGrid() {
  const jsonUrl = jsonUrlFromMp3(getSelectedMp3());
  try {
    const res = await fetch(jsonUrl, { cache: 'no-store' });
    if (!res.ok) throw new Error(`fetch ${jsonUrl} ${res.status}`);
    const data = await res.json();

    SONG_BPM = data?.tempoMap?.[0]?.bpm ?? SONG_BPM;
    beats    = Array.isArray(data?.beats) ? data.beats : [];
    sections = Array.isArray(data?.sections) ? data.sections : [];

    console.log(`[LOAD] ${jsonUrl} -> SONG_BPM=${SONG_BPM.toFixed(3)} beats=${beats.length} sections=${sections.length}`);
  } catch (e) {
    console.warn(`[LOAD] ${jsonUrl} 읽기 실패. 기본값 사용`, e);
    beats = [];
    sections = [];
  }
}

// ===== 가사 로드 =====
function normalizeLyricsPayload(data) {
  // 1) { lines: [{text,start,end}, ...] }
  if (Array.isArray(data?.lines)) {
    return data.lines.map(it => ({
      line: String(it.text ?? it.line ?? '').trim(),
      start: Number(it.start ?? 0),
      end: Number(it.end ?? (Number(it.start ?? 0) + 2))
    }));
  }
  // 2) { lyricsTimeline: { items: [{ line,start,end }, ...] } }
  if (Array.isArray(data?.lyricsTimeline?.items)) {
    return data.lyricsTimeline.items.map(it => ({
      line: String(it.line ?? it.text ?? '').trim(),
      start: Number(it.start ?? 0),
      end: Number(it.end ?? (Number(it.start ?? 0) + 2))
    }));
  }
  // 3) 최상위 배열
  if (Array.isArray(data)) {
    return data.map(it => ({
      line: String(it.line ?? it.text ?? '').trim(),
      start: Number(it.start ?? 0),
      end: Number(it.end ?? (Number(it.start ?? 0) + 2))
    }));
  }
  return [];
}

async function loadLyrics() {
  const jsonUrl = lyricsJsonUrlFromMp3(getSelectedMp3());
  try {
    const res = await fetch(jsonUrl, { cache: 'no-store' });
    if (!res.ok) throw new Error(`fetch ${jsonUrl} ${res.status}`);

    const data = await res.json();
    const items = normalizeLyricsPayload(data);
    lyrics = items
      .filter(it => it.line)
      .sort((a, b) => a.start - b.start);

    lyricIdx = -1;

    if (lyrics.length === 0) {
      $lyPrev.textContent = '';
      $lyCurr.textContent = '가사 정보가 없어요';
      $lyCurr.classList.add('lyrics-empty');
      $lyNext.textContent = '';
    } else {
      $lyCurr.classList.remove('lyrics-empty');
      renderLyricsAt(0);
    }
    console.log(`[LYRICS] loaded ${lyrics.length} lines from ${jsonUrl}`);
  } catch (e) {
    console.warn('[LYRICS] load failed:', e);
    lyrics = [];
    lyricIdx = -1;
    $lyPrev.textContent = '';
    $lyCurr.textContent = '가사 파일을 찾을 수 없어요';
    $lyCurr.classList.add('lyrics-empty');
    $lyNext.textContent = '';
  }
}

// ===== 가사 렌더링 =====
function findLyricIndex(t) {
  if (!lyrics.length) return -1;
  let i = lyricIdx >= 0 ? lyricIdx : 0;

  while (i > 0 && lyrics[i].start > t) i--;
  while (i + 1 < lyrics.length && lyrics[i + 1].start <= t) i++;

  if (lyrics[i].start <= t && t < (lyrics[i].end ?? (lyrics[i].start + 2))) return i;

  for (let k = 0; k < lyrics.length; k++) {
    const L = lyrics[k];
    if (L.start <= t && t < (L.end ?? (L.start + 2))) return k;
  }
  return -1;
}

function renderLyricsAt(t) {
  if (!lyrics.length) return;
  const idx = findLyricIndex(t);
  if (idx === lyricIdx) return;

  lyricIdx = idx;

  if (idx < 0) {
    const next = lyrics.find(l => l.start > t);
    $lyPrev.textContent = '';
    $lyCurr.textContent = next ? '(간주 중)' : '';
    $lyCurr.classList.add('lyrics-empty');
    $lyNext.textContent = next ? next.line : '';
    return;
  }

  $lyCurr.classList.remove('lyrics-empty');
  $lyCurr.textContent = lyrics[idx].line;
  $lyPrev.textContent = lyrics[idx - 1]?.line ?? '';
  $lyNext.textContent = lyrics[idx + 1]?.line ?? '';
}

// ===== 박/섹션 유틸 =====
// 현재 오디오 시간 → 전체 박 번호(1..)
function getCurrentBeatNumber(nowSec = audioEl.currentTime) {
  if (!beats.length) return 1;
  // 이진 탐색
  let lo = 0, hi = beats.length - 1, ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (beats[mid].t <= nowSec) { ans = mid; lo = mid + 1; }
    else hi = mid - 1;
  }
  return (ans >= 0 ? ans + 1 : 1); // 1-based index
}

// 박 번호 → 섹션 라벨
function sectionByBeat(beatNum) {
  if (!sections.length) return null;
  return sections.find(s => beatNum >= s.startBeat && beatNum <= s.endBeat)?.label ?? null;
}

// 오디오 경과 → 이상적 루프 위상(초), 섹션별 루프 길이 사용
function computeIdealPhase(section = currentSection) {
  if (!audioCtx) return 0;
  const { bpm } = getVideoMeta(section);
  const loopLen = getLoopLenSec(section);
  const audioElapsed = Math.max(0, audioCtx.currentTime - t0);
  // 오디오 시간 * (영상BPM/노래BPM) = 영상 시간 스케일
  const videoPhase = audioElapsed * (bpm / SONG_BPM);
  return videoPhase % loopLen;
}

// ===== 섹션 전환: 영상 소스 교체 + 루프 보강 =====
let _switching = false;
async function ensureVideoForSection(label) {
  if (!label) return;
  if (currentSection === label && video.dataset.section === label) return;
  if (_switching) return;
  _switching = true;

  const { src } = getVideoMeta(label);
  const targetPhase = computeIdealPhase(label);

  const onLoaded = new Promise(resolve => {
    const handler = () => { video.removeEventListener('loadedmetadata', handler); resolve(); };
    video.addEventListener('loadedmetadata', handler, { once: true });
  });

  // 일부 브라우저에서 loop 플래그 유실/이벤트 꼬임 방지
  video.pause();
  video.removeAttribute('src');
  video.load();

  video.src = encodeURI(src);
  video.dataset.section = label;

  // 🔁 루프 보장
  video.loop = true;
  video.onended = () => {
    try {
      const loopLen = getLoopLenSec(label);
      const phase = computeIdealPhase(label);
      video.currentTime = phase % (video.duration || loopLen);
      video.play();
    } catch {}
  };

  video.load();
  await onLoaded;

  try { video.currentTime = targetPhase; } catch {}
  try {
    video.playbackRate = getBaseRate(label);
    await video.play();
  } catch {}

  currentSection = label;
  _switching = false;
  console.log(`[VIDEO] section=${label} src=${src} phase=${targetPhase.toFixed(3)} rate=${getBaseRate(label).toFixed(3)}`);
}

// ===== 싱크 루프 (오디오=마스터, 비디오=추종) =====
function startSyncLoop() {
  const KP = 0.35;
  const MICRO = 0.03;

  const shortestSignedDelta = (a, b, period) => {
    let d = a - b;
    if (d >  period / 2) d -= period;
    if (d < -period / 2) d += period;
    return d;
  };

  const loop = () => {
    const loopLen = getLoopLenSec();               // 현재 섹션 기준
    const idealPhase   = computeIdealPhase();      // 0~loopLen
    const actualPhase  = (video.currentTime % loopLen); // 0~loopLen
    const drift        = shortestSignedDelta(idealPhase, actualPhase, loopLen);
    const microAdjust  = clamp(drift * KP, -MICRO, MICRO);

    // 섹션별 기준 재생속도에 미세 보정
    video.playbackRate = getBaseRate() + microAdjust * 0.8;

    // 섹션 감시(저비용)
    const beatNum = getCurrentBeatNumber(audioEl.currentTime);
    const targetSection = sectionByBeat(beatNum);
    if (targetSection && targetSection !== currentSection) {
      ensureVideoForSection(targetSection);
    }

    // 드물게 loop 끊김 시 수동 래핑(세이프가드)
    if (video.duration && video.currentTime >= video.duration - 0.02) {
      try {
        const phase = computeIdealPhase();
        video.currentTime = phase % (video.duration || loopLen);
      } catch {}
    }

    video.requestVideoFrameCallback(loop);
  };
  video.requestVideoFrameCallback(loop);
}

// ===== 오디오/비디오 무장 + 특정 시각에 맞춰 시작 =====
async function armStartAt(targetTimeSec) {
  await loadBeatGrid();

  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    await audioCtx.resume();
  }
  if (!mediaSrc) {
    mediaSrc = audioCtx.createMediaElementSource(audioEl);
    mediaSrc.connect(audioCtx.destination);
  }

  if (audioEl.paused) await audioEl.play();

  // 시작 시점 섹션 미리 로드
  let startBeat = 1;
  if (beats.length) {
    const idx = (typeof beats.findLastIndex === 'function')
      ? beats.findLastIndex(b => b.t <= targetTimeSec)
      : (() => { let i=-1; for (let k=0;k<beats.length;k++) if (beats[k].t<=targetTimeSec) i=k; return i; })();
    startBeat = idx >= 0 ? idx + 1 : 1;
  }
  const startSection = sectionByBeat(startBeat) || 'part1';
  await ensureVideoForSection(startSection);

  // 오디오 기준 예약
  const targetWithOffset = targetTimeSec + START_OFFSET;
  const nowEl = audioEl.currentTime;
  const delaySec = Math.max(0, targetWithOffset - nowEl);
  const startAtAudioCtxTime = audioCtx.currentTime + delaySec;
  t0 = startAtAudioCtxTime;

  const startVideo = () => {
    const phase = computeIdealPhase();
    try { video.currentTime = phase; } catch {}
    video.playbackRate = getBaseRate();
    video.play().then(() => startSyncLoop());
  };

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

  console.log(`[ARMED] now=${nowEl.toFixed(3)}s → start@${targetTimeSec.toFixed(3)}s, section=${startSection}, rate=${getBaseRate(startSection).toFixed(3)}`);
}

// ===== 찾기 함수들 =====
function findBarBeatTime(bar, beat) {
  const bb = beats.find(b => b.bar === bar && b.beat === beat);
  return bb ? bb.t : null;
}
function findNextBeatNumberTime(beatNum) {
  const now = audioEl.currentTime;
  let next = beats.find(b => b.t >= now && b.beat === beatNum);
  if (!next) next = beats.find(b => b.t >= now);
  return next ? next.t : null;
}

// ===== 이벤트 바인딩 =====
musicSel.addEventListener('change', async () => {
  applySelectedAudio();
  await loadBeatGrid();
  await loadLyrics();

  const initialSection = sectionByBeat(1) || 'part1';
  await ensureVideoForSection(initialSection);

  // 가사 패널 초기 메시지
  $lyPrev.textContent = '';
  $lyCurr.textContent = '(가사를 불러오는 중…)';
  $lyCurr.classList.add('lyrics-empty');
  $lyNext.textContent = '';
});

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

// 오디오 시간 변화 → 가사/섹션 재확인
audioEl.addEventListener('timeupdate', () => {
  renderLyricsAt(audioEl.currentTime);
  const sec = sectionByBeat(getCurrentBeatNumber(audioEl.currentTime));
  if (sec && sec !== currentSection) ensureVideoForSection(sec);
});
audioEl.addEventListener('seeked', () => {
  renderLyricsAt(audioEl.currentTime);
  const sec = sectionByBeat(getCurrentBeatNumber(audioEl.currentTime));
  if (sec && sec !== currentSection) ensureVideoForSection(sec);
});

// ===== 초기화 =====
applySelectedAudio();
Promise.all([loadBeatGrid(), loadLyrics()]).then(async () => {
  const initialSection = sectionByBeat(1) || 'part1';
  await ensureVideoForSection(initialSection);
});
