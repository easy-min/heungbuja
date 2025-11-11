import type { GameStartResponse, LyricLine } from '@/types/song';

// 로딩 시연용 유틸
const delay = (ms: number) => new Promise(res => setTimeout(res, ms));

export async function mockGameStart(): Promise<GameStartResponse> {
  await delay(200);

  // 가사 정보
  const lyricsInfo: LyricLine[] = [
    { lineIndex: 1, text: '일부러 안 웃는거 맞죠', start: 33.0, end: 37.0, sbeat: 64, ebeat: 72 },
    { lineIndex: 2, text: '나에게만 차가운거 맞죠', start: 37.0, end: 41.0, sbeat: 72, ebeat: 81 },
    { lineIndex: 3, text: '알아요 그대 마음을', start: 41.0, end: 44.0, sbeat: 81, ebeat: 87 },
    { lineIndex: 4, text: '내게 빠질까봐 두려운거죠', start: 44.0, end: 48.0, sbeat: 87, ebeat: 96 },
    { lineIndex: 5, text: '그대는 그게 매력이에요', start: 48.0, end: 52.0, sbeat: 96, ebeat: 105 },
    { lineIndex: 6, text: '관심 없는 듯한 말투 눈빛', start: 52.0, end: 56.0, sbeat: 105, ebeat: 113 },
    { lineIndex: 7, text: '하지만 그대 시선은', start: 56.0, end: 59.0, sbeat: 113, ebeat: 120 },
    { lineIndex: 8, text: '나는 안보고도 느낄 수 있죠', start: 59.0, end: 62.0, sbeat: 120, ebeat: 126 },
    { lineIndex: 9, text: '집으로 들어가는 길인가요', start: 62.0, end: 66.0, sbeat: 126, ebeat: 135 },
    { lineIndex: 10, text: '그대의 어깨가 무거워 보여', start: 66.0, end: 70.0, sbeat: 135, ebeat: 144 },
    { lineIndex: 11, text: '이런 나 당돌한가요', start: 70.0, end: 73.0, sbeat: 144, ebeat: 150 },
    { lineIndex: 12, text: '술 한잔 사주실래요', start: 73.0, end: 77.0, sbeat: 150, ebeat: 159 },
    { lineIndex: 13, text: '야이야야야이 날 봐요', start: 77.0, end: 81.0, sbeat: 159, ebeat: 167 },
    { lineIndex: 14, text: '우리 마음 속이지는 말아요', start: 81.0, end: 85.0, sbeat: 167, ebeat: 176 },
    { lineIndex: 15, text: '날 기다렸다고', start: 85.0, end: 88.0, sbeat: 176, ebeat: 183 },
    { lineIndex: 16, text: '먼저 얘기하면 손해라도보나요', start: 88.0, end: 92.0, sbeat: 183, ebeat: 191 },
    { lineIndex: 17, text: '야이야이야이 말해요', start: 92.0, end: 96.0, sbeat: 191, ebeat: 200 },
    { lineIndex: 18, text: '그대 여자 되달라고 말해요', start: 96.0, end: 100.0, sbeat: 200, ebeat: 209 },
    { lineIndex: 19, text: '난 이미 오래전 그대 여자이고 싶었어요', start: 100.0, end: 108.0, sbeat: 209, ebeat: 226 },
    { lineIndex: 20, text: "애인이 없다는거 맞죠", "start": 138.0, "end": 142.0, "ebeat": 300, "sbeat": 291 }, 
    { lineIndex: 21, text: "혹시 숨겨둔거 아니겠죠", "start": 142.0, "end": 146.0, "ebeat": 308, "sbeat": 300 }, 
    { lineIndex: 22, text: "믿어요 그대의 말을", "start": 146.0, "end": 149.0, "ebeat": 315, "sbeat": 308 }, 
    { lineIndex: 23, text: "행여 있다 해도 양보는 싫어", "start": 149.0, "end": 153.0, "ebeat": 323, "sbeat": 315 }, 
    { lineIndex: 24, text: "그대는 그게 맘에 들어", "start": 153.0, "end": 157.0, "ebeat": 332, "sbeat": 323 }, 
    { lineIndex: 25, text: "여자 많은 듯한 겉모습에", "start": 157.0, "end": 161.0, "ebeat": 341, "sbeat": 332 }, 
    { lineIndex: 26, text: "사실은 아무에게나", "start": 161.0, "end": 164.0, "ebeat": 347, "sbeat": 341 }, 
    { lineIndex: 27, text: "마음주지 않는 그런 남자죠", "start": 164.0, "end": 168.0, "ebeat": 356, "sbeat": 347 },
    { lineIndex: 28, text: "집으로 들어가는 길인가요", "start": 168.0, "end": 171.0, "ebeat": 362, "sbeat": 356 }, 
    { lineIndex: 29, text: "그대의 어깨가 무거워 보여", "start": 171.0, "end": 175.0, "ebeat": 371, "sbeat": 362 }, 
    { lineIndex: 30, text: "이런 나 당돌한가요", "start": 175.0, "end": 178.0, "ebeat": 378, "sbeat": 371 }, 
    { lineIndex: 31, text: "술 한잔 사주실래요", "start": 178.0, "end": 183.0, "ebeat": 388, "sbeat": 378 }, 
    { lineIndex: 32, text: "야이야야야이 날 봐요", "start": 183.0, "end": 186.0, "ebeat": 395, "sbeat": 388 }, 
    { lineIndex: 33, text: "우리 마음 속이지는 말아요", "start": 186.0, "end": 190.0, "ebeat": 404, "sbeat": 395 }, 
    { lineIndex: 34, text: "날 기다렸다고", "start": 190.0, "end": 193.0, "ebeat": 410, "sbeat": 404 }, 
    { lineIndex: 35, text: "먼저 얘기하면 손해라도보나요", "start": 193.0, "end": 197.0, "ebeat": 419, "sbeat": 410 }, 
    { lineIndex: 36, text: "야이야이야이 말해요", "start": 197.0, "end": 201.0, "ebeat": 427, "sbeat": 419 }, 
    { lineIndex: 37, text: "그대 여자 되달라고 말해요", "start": 201.0, "end": 205.0, "ebeat": 436, "sbeat": 427 }, 
    { lineIndex: 38, text: "난 이미 오래전 그대 여자이고 싶었어요", "start": 205.0, "end": 213.0, "ebeat": 453, "sbeat": 436 }
  ];

  return {
    success: true,
    data: {
      sessionId: '19cb4d67-07f3-400e-9dc9-6d8bc2402446',
      songId: 1,
      songTitle: '당돌한 여자',
      songArtist: 'Seo Jookyung',
      audioUrl:
        '/당돌한여자.mp3',
      videoUrls: {
        intro:
          'https://heungbuja-bucket.s3.ap-northeast-2.amazonaws.com/video/break.mp4',
        verse1:
          'https://heungbuja-bucket.s3.ap-northeast-2.amazonaws.com/video/part1.mp4',
        verse2_level1:
          'https://heungbuja-bucket.s3.ap-northeast-2.amazonaws.com/video/part2_level1.mp4',
        verse2_level2:
          'https://heungbuja-bucket.s3.ap-northeast-2.amazonaws.com/video/part2_level2.mp4',
        verse2_level3: 'https://example.com/video_v2_level3.mp4',
      },
      bpm: 129.71510314941406,
      duration: 220.35736961451246,

      sectionInfo: {
        introStartTime: 4.163151927437642,
        verse1StartTime: 33.69315192743764,
        breakStartTime: 107.56315192743764,
        verse2StartTime: 138.95315192743763,
      },

      segmentInfo: {
        verse1cam: { startTime: 48.47315192743765, endTime: 92.78315192743764 },
        verse2cam: { startTime: 153.73315192743763, endTime: 198.04315192743763 },
      },

      verse1Timeline: [
        { time: 33.69, actionCode: 1, actionName: '손뼉 박수' },
        { time: 35.54, actionCode: 2, actionName: '팔 치기' },
        { time: 37.38, actionCode: 1, actionName: '손뼉 박수' },
        { time: 41.07, actionCode: 2, actionName: '팔 치기' },
      ],

      verse2Timeline: {
        level1: [
          { time: 146.33, actionCode: 1, actionName: '손뼉 박수' },
          { time: 153.73, actionCode: 2, actionName: '팔 치기' },
        ],
        level2: [
          { time: 157.42, actionCode: 1, actionName: '손뼉 박수' },
          { time: 167.57, actionCode: 2, actionName: '팔 치기' },
        ],
        level3: [
          { time: 177.73, actionCode: 1, actionName: '손뼉 박수' },
          { time: 187.89, actionCode: 2, actionName: '팔 치기' },
        ],
      },

      lyricsInfo,
    },
  };
}
