"""
음악 NER 데이터 증강
어르신들의 다양한 발화 패턴을 고려한 데이터 생성
"""

import pandas as pd
import random
import re
from typing import List, Dict, Tuple

# ============================================
# 1. 데이터 로드
# ============================================
df = pd.read_excel('/mnt/user-data/uploads/노래목록.xlsx')
df = df.dropna(subset=['곡명', '가수'])  # 결측치 제거
print(f"원본 데이터: {len(df)}곡")

# ============================================
# 2. 다양한 발화 패턴 정의 (어르신 친화적)
# ============================================

# 기본 패턴
BASIC_PATTERNS = [
    "{song} {artist}",
    "{artist} {song}",
    "{artist}의 {song}",
    "{song} {artist}꺼",
]

# 명령형 패턴 (어르신들이 많이 쓰는 표현)
COMMAND_PATTERNS = [
    "{song} 틀어줘",
    "{song} 틀어주세요",
    "{song} 재생해줘",
    "{song} 재생해주세요",
    "{song} 들려줘",
    "{song} 들려주세요",
    "{song} 노래 틀어줘",
    "{song} 노래 재생해줘",
    "{song} 한 번 틀어줘",
    "{song} 한번 들려줘",
    "{song} 이거 틀어줘",
    "{song} 이거 재생해줘",
    "{song} 좀 틀어줘",
    "{song} 좀 재생해줘",
]

# 가수 중심 패턴
ARTIST_FIRST_PATTERNS = [
    "{artist} {song} 틀어줘",
    "{artist} {song} 재생해줘",
    "{artist} {song} 들려줘",
    "{artist}의 {song} 틀어줘",
    "{artist}의 {song} 재생해줘",
    "{artist}가 부른 {song}",
    "{artist}이 부른 {song}",
    "{artist} 노래 {song} 틀어줘",
    "{artist} 노래 중에 {song}",
    "{artist} {song} 이거 틀어줘",
    "{artist}꺼 {song} 틀어줘",
    "{artist}꺼 {song} 재생해줘",
]

# 곡명 뒤에 가수 언급
SONG_THEN_ARTIST_PATTERNS = [
    "{song} 틀어줘 {artist}",
    "{song} 재생해줘 {artist}",
    "{song} 들려줘 {artist}",
    "{song} {artist} 틀어줘",
    "{song} {artist} 재생해줘",
    "{song} 노래 틀어줘 {artist}",
    "{song} 이거 {artist} 거",
    "{song} 이거 {artist}꺼",
]

# 구어체/방언 패턴 (어르신들 특유의 표현)
COLLOQUIAL_PATTERNS = [
    "{song} 좀 틀어줘요",
    "{song} 한 번만 틀어줘",
    "{song} 이거 한 번 들어보자",
    "{artist} {song} 이거 좋아하는데",
    "{artist}가 부르는 {song}",
    "{song}라는 노래 틀어줘",
    "{song}란 노래 있잖아",
    "{song} 그거 틀어줘",
    "{artist} 그 노래 {song}",
    "{artist}이 부르는 그 {song}",
]

# 요청형 패턴 (정중한 표현)
POLITE_PATTERNS = [
    "{song} 좀 들려주시겠어요",
    "{song} 틀어주실래요",
    "{song} 재생 좀 해주세요",
    "{artist} {song} 들려주세요",
    "{artist}의 {song} 부탁합니다",
    "{song} 노래 부탁해요",
]

# 검색형 패턴
SEARCH_PATTERNS = [
    "{song} 찾아줘",
    "{song} 검색해줘",
    "{artist} {song} 찾아줘",
    "{artist} {song} 검색",
    "{song} 이거 어디 있어",
]

# 재생 관련 다양한 표현
PLAY_PATTERNS = [
    "{song} 나오게 해줘",
    "{song} 플레이",
    "{song} 플레이해줘",
    "{artist} {song} 플레이",
    "{song} 한번 나오게",
    "{song} 나오게",
]

# 음악 관련 자연스러운 표현
NATURAL_PATTERNS = [
    "{song} 듣고 싶어",
    "{song} 듣고 싶은데",
    "{artist} {song} 듣고 싶어",
    "{song} 좀 듣자",
    "{song} 한번 듣자",
    "{artist} 노래 {song} 듣고 싶어",
]

# 회상/추억 표현 (어르신들이 많이 쓰는)
NOSTALGIA_PATTERNS = [
    "{song} 그거 좋았는데",
    "{artist} {song} 그거 좋지",
    "{song} 옛날 노래",
    "{artist}이 부른 {song} 그거",
]

# 모든 패턴 통합
ALL_PATTERNS = (
    BASIC_PATTERNS +
    COMMAND_PATTERNS +
    ARTIST_FIRST_PATTERNS +
    SONG_THEN_ARTIST_PATTERNS +
    COLLOQUIAL_PATTERNS +
    POLITE_PATTERNS +
    SEARCH_PATTERNS +
    PLAY_PATTERNS +
    NATURAL_PATTERNS +
    NOSTALGIA_PATTERNS
)

print(f"\n총 패턴 수: {len(ALL_PATTERNS)}개")

# ============================================
# 3. 조사 처리 함수
# ============================================
def add_josa(name: str, josa_type: str) -> str:
    """
    한국어 조사 자동 추가
    josa_type: '이/가', '을/를', '의'
    """
    if not name:
        return name
    
    last_char = name[-1]
    code = ord(last_char) - 0xAC00
    
    # 한글이 아닌 경우
    if code < 0 or code > 11171:
        # 영어나 숫자로 끝나는 경우
        if josa_type == '이/가':
            return name + '가'
        elif josa_type == '을/를':
            return name + '을'
        return name
    
    # 받침 확인
    has_jongseong = (code % 28) != 0
    
    if josa_type == '이/가':
        return name + ('이' if has_jongseong else '가')
    elif josa_type == '을/를':
        return name + ('을' if has_jongseong else '를')
    elif josa_type == '의':
        return name + '의'
    
    return name

# ============================================
# 4. 데이터 증강 함수
# ============================================
def augment_single_song(song: str, artist: str, num_samples: int = 15) -> List[Dict]:
    """
    하나의 곡에 대해 다양한 패턴으로 증강
    """
    augmented_samples = []
    
    # 패턴을 무작위로 섞어서 다양성 확보
    selected_patterns = random.sample(ALL_PATTERNS, min(num_samples, len(ALL_PATTERNS)))
    
    for pattern in selected_patterns:
        # 조사 처리가 필요한 경우
        if '{artist}가 부른' in pattern or '{artist}이 부른' in pattern:
            artist_with_josa = add_josa(artist, '이/가').replace('이', '').replace('가', '')
            if pattern.count('가 부른') > 0:
                artist_processed = add_josa(artist_with_josa, '이/가').replace('이', '가')
            else:
                artist_processed = add_josa(artist_with_josa, '이/가')
            text = pattern.replace('{artist}가 부른', f'{artist_processed} 부른')
            text = text.replace('{artist}이 부른', f'{artist_processed} 부른')
        elif '{artist}이 부르는' in pattern:
            artist_with_josa = add_josa(artist, '이/가')
            text = pattern.replace('{artist}이 부르는', f'{artist_with_josa} 부르는')
        else:
            text = pattern
        
        # 기본 치환
        text = text.format(song=song, artist=artist)
        
        # 곡명과 가수의 위치 찾기
        song_start = text.find(song)
        artist_start = text.find(artist)
        
        # 둘 다 있어야 유효한 샘플
        if song_start != -1 and artist_start != -1:
            augmented_samples.append({
                'text': text,
                'song': song,
                'artist': artist,
                'song_start': song_start,
                'song_end': song_start + len(song),
                'artist_start': artist_start,
                'artist_end': artist_start + len(artist)
            })
    
    return augmented_samples

# ============================================
# 5. 전체 데이터 증강 실행
# ============================================
def augment_all_data(df: pd.DataFrame, samples_per_song: int = 15) -> pd.DataFrame:
    """
    전체 곡 목록을 증강
    """
    all_augmented = []
    
    for idx, row in df.iterrows():
        song = str(row['곡명']).strip()
        artist = str(row['가수']).strip()
        
        # 괄호 제거 (예: "시절인연 (時節因緣)" -> "시절인연")
        song_clean = re.sub(r'\s*\([^)]*\)', '', song).strip()
        
        # 증강 데이터 생성
        augmented = augment_single_song(song_clean, artist, samples_per_song)
        all_augmented.extend(augmented)
        
        if (idx + 1) % 100 == 0:
            print(f"진행중... {idx + 1}/{len(df)}")
    
    return pd.DataFrame(all_augmented)

# 증강 실행
print("\n데이터 증강 시작...")
augmented_df = augment_all_data(df, samples_per_song=15)

print(f"\n증강 완료!")
print(f"원본 데이터: {len(df)}곡")
print(f"증강 후 데이터: {len(augmented_df)}개 샘플")

# ============================================
# 6. 샘플 확인
# ============================================
print("\n" + "="*60)
print("증강된 데이터 샘플 (같은 곡에 대한 다양한 표현)")
print("="*60)

# 첫 번째 곡의 다양한 패턴 보기
first_song = df.iloc[0]['곡명']
first_artist = df.iloc[0]['가수']
samples = augmented_df[(augmented_df['song'].str.contains(first_song.split()[0])) & 
                       (augmented_df['artist'] == first_artist)].head(20)

for idx, row in samples.iterrows():
    print(f"{idx+1}. {row['text']}")

# ============================================
# 7. 통계 정보
# ============================================
print("\n" + "="*60)
print("데이터 통계")
print("="*60)
print(f"총 샘플 수: {len(augmented_df):,}개")
print(f"고유 곡 수: {augmented_df['song'].nunique()}개")
print(f"고유 가수 수: {augmented_df['artist'].nunique()}명")
print(f"곡당 평균 샘플 수: {len(augmented_df) / df.shape[0]:.1f}개")

print("\n상위 10명 가수별 샘플 수:")
print(augmented_df['artist'].value_counts().head(10))

# ============================================
# 8. 저장
# ============================================
output_path = '/mnt/user-data/outputs/augmented_music_data.csv'
augmented_df.to_csv(output_path, index=False, encoding='utf-8-sig')
print(f"\n증강된 데이터 저장 완료: {output_path}")

# Excel로도 저장 (참고용)
excel_output_path = '/mnt/user-data/outputs/augmented_music_data.xlsx'
augmented_df.to_excel(excel_output_path, index=False, engine='openpyxl')
print(f"Excel 파일도 저장: {excel_output_path}")

print("\n✅ 모든 작업 완료!")