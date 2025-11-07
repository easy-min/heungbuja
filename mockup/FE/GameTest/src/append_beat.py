import json

def find_closest_beat(target_time, beats):
    """
    주어진 시간(target_time)과 가장 가까운 비트를 찾아 그 비트의 인덱스('i')를 반환합니다.

    Args:
        target_time (float): 가사의 시작 또는 끝 시간 (초).
        beats (list): 노래의 전체 비트 정보가 담긴 리스트.

    Returns:
        int: 가장 가까운 비트의 'i' 값.
    """
    if not beats:
        return None

    # 가장 가까운 비트를 찾기 위해 초기값을 첫 번째 비트로 설정
    closest_beat = beats[0]
    min_diff = abs(target_time - closest_beat['t'])

    # 모든 비트를 순회하며 시간 차이가 가장 적은 비트를 찾음
    for beat in beats[1:]:
        diff = abs(target_time - beat['t'])
        if diff < min_diff:
            min_diff = diff
            closest_beat = beat
            
    return closest_beat['i']

# --- 파일 이름 설정 ---
# 이 부분의 변수 값만 수정하면 다른 곡에도 쉽게 적용할 수 있습니다.
file_prefix = "당돌한여자"

lyrics_filename = f"{file_prefix}_가사.json"
song_filename = f"{file_prefix}.json"
output_filename = f"{file_prefix}_가사_new.json"
# --------------------

# 1. JSON 파일 로드
try:
    with open(lyrics_filename, 'r', encoding='utf-8') as f:
        lyrics_data = json.load(f)

    with open(song_filename, 'r', encoding='utf-8') as f:
        song_data = json.load(f)
except FileNotFoundError as e:
    print(f"오류: {e.filename} 파일을 찾을 수 없습니다. 파일 이름과 경로를 확인해주세요.")
    exit()


# 노래 데이터에서 비트 정보 추출
beats = song_data.get('beats', [])
if not beats:
    print(f"오류: {song_filename} 파일에 'beats' 정보가 없습니다.")
    exit()

# 2. 새로운 가사 데이터를 저장할 리스트 생성
new_lyrics_lines = []

# 3. 각 가사 라인을 순회하며 sBeat와 eBeat 추가
for line in lyrics_data['lines']:
    start_time = line['start']
    end_time = line['end']
    
    # 시작 시간과 끝 시간에 가장 가까운 비트 찾기
    sBeat = find_closest_beat(start_time, beats)
    eBeat = find_closest_beat(end_time, beats)
    
    # 기존 라인 정보에 비트 정보 추가
    new_line = {
        "lineIndex": line['lineIndex'],
        "text": line['text'],
        "start": line['start'],
        "end": line['end'],
        "sBeat": sBeat,
        "eBeat": eBeat
    }
    new_lyrics_lines.append(new_line)

# 4. 최종 결과 JSON 구조 생성
new_lyrics_data = {"lines": new_lyrics_lines}

# 5. 새로운 JSON 파일로 저장
with open(output_filename, 'w', encoding='utf-8') as f:
    # ensure_ascii=False로 한글이 깨지지 않게 저장, indent=2로 가독성 좋게 저장
    json.dump(new_lyrics_data, f, ensure_ascii=False, indent=2)

print(f"성공적으로 '{output_filename}' 파일을 생성했습니다.")