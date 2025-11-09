import type { GameStartResponse, LyricLine, SongTimeline } from '@/types/song';

// 지연 유틸 (로딩 느낌)
const delay = (ms: number) => new Promise(res => setTimeout(res, ms));

const timeline: SongTimeline = {
  introStartTime: 4.16,
  verse1StartTime: 33.69,
  breakStartTime: 107.56,
  verse2StartTime: 138.95,
  verse1SegmentStartTimes: [33.69, 41.07, 48.47, 55.85, 63.24, 70.62],
  verse2SegmentStartTimes: [138.95, 146.33, 153.73, 161.11, 168.50, 175.88],
};

const lyrics: LyricLine[] = [
  { text: '일부러 안 웃는거 맞죠', startTime: 33, endTime: 37 },
  { text: '나에게만 차가운거 맞죠', startTime: 37, endTime: 41 },
  { text: '알아요 그대 마음을', startTime: 41, endTime: 44 },
  { text: '내게 빠질까봐 두려운거죠', startTime: 44, endTime: 48 },
  { text: '그대는 그게 매력이에요', startTime: 48, endTime: 52 },
  { text: '관심 없는 듯한 말투 눈빛', startTime: 52, endTime: 56 },
  { text: '하지만 그대 시선은', startTime: 56, endTime: 59 },
  { text: '나는 안보고도 느낄 수 있죠', startTime: 59, endTime: 62 },
  { text: '집으로 들어가는 길인가요', startTime: 62, endTime: 66 },
  { text: '그대의 어깨가 무거워 보여', startTime: 66, endTime: 70 },
  { text: '이런 나 당돌한가요', startTime: 70, endTime: 73 },
  { text: '술 한잔 사주실래요', startTime: 73, endTime: 77 },
  { text: '야이야야야이 날 봐요', startTime: 77, endTime: 81 },
  { text: '우리 마음 속이지는 말아요', startTime: 81, endTime: 85 },
  { text: '날 기다렸다고', startTime: 85, endTime: 88 },
  { text: '먼저 얘기하면 손해라도보나요', startTime: 88, endTime: 92 },
  { text: '야이야이야이 말해요', startTime: 92, endTime: 96 },
  { text: '그대 여자 되달라고 말해요', startTime: 96, endTime: 100 },
  { text: '난 이미 오래전 그대 여자이고 싶었어요', startTime: 100, endTime: 108 },

  { text: '애인이 없다는거 맞죠', startTime: 138, endTime: 142 },
  { text: '혹시 숨겨둔거 아니겠죠', startTime: 142, endTime: 146 },
  { text: '믿어요 그대의 말을', startTime: 146, endTime: 149 },
  { text: '행여 있다 해도 양보는 싫어', startTime: 149, endTime: 153 },
  { text: '그대는 그게 맘에 들어', startTime: 153, endTime: 157 },
  { text: '여자 많은 듯한 겉모습에', startTime: 157, endTime: 161 },
  { text: '사실은 아무에게나', startTime: 161, endTime: 164 },
  { text: '마음주지 않는 그런 남자죠', startTime: 164, endTime: 168 },
  { text: '집으로 들어가는 길인가요', startTime: 168, endTime: 171 },
  { text: '그대의 어깨가 무거워 보여', startTime: 171, endTime: 175 },
  { text: '이런 나 당돌한가요', startTime: 175, endTime: 178 },
  { text: '술 한잔 사주실래요', startTime: 178, endTime: 183 },
  { text: '야이야야야이 날 봐요', startTime: 183, endTime: 186 },
  { text: '우리 마음 속이지는 말아요', startTime: 186, endTime: 190 },
  { text: '날 기다렸다고', startTime: 190, endTime: 193 },
  { text: '먼저 얘기하면 손해라도보나요', startTime: 193, endTime: 197 },
  { text: '야이야이야이 말해요', startTime: 197, endTime: 201 },
  { text: '그대 여자 되달라고 말해요', startTime: 201, endTime: 205 },
  { text: '난 이미 오래전 그대 여자이고 싶었어요', startTime: 205, endTime: 213 },
];

export async function mockGameStart(songId: number): Promise<GameStartResponse> {
  // 실제 API 호출처럼 약간의 지연
  await delay(300);

  return {
    success: true,
    data: {
      sessionId: 'mock-a1b2c3d4-e5f6-7890',
      songInfo: {
        audioUrl: '/당돌한여자.mp3',
        title: '당돌한 여자',
        artist: '서주경',
        bpm: 129.71,
        duration: 220.35,
      },
      timeline,
      lyrics,
      videoUrls: {
        intro: 'https://example.com/mock/video_intro.mp4',
        verse1: 'https://example.com/mock/video_v1.mp4',
        verse2_level1: 'https://example.com/mock/video_v2_level1.mp4',
        verse2_level2: 'https://example.com/mock/video_v2_level2.mp4',
        verse2_level3: 'https://example.com/mock/video_v2_level3.mp4',
      },
    },
  };
}
