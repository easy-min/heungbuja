# Action Code 매핑 정보

## DB actionCode → 데이터 폴더명 매핑

### ✅ 학습할 동작 (7개)

| actionCode | DB 동작 이름 | 데이터 폴더명 | Model Class Index |
|------------|-------------|--------------|-------------------|
| 1          | 손 박수      | CLAP         | 0                 |
| 2          | 팔 치기      | ELBOW        | 1                 |
| 4          | 팔 뻗기      | STRETCH      | 2                 |
| 5          | 기우뚱      | TILT         | 3                 |
| 6          | 비상구      | EXIT         | 4                 |
| 7          | 겨드랑이박수  | UNDERARM     | 5                 |
| 9          | 가만히 있음  | STAY         | 6                 |

### ❌ 학습하지 않을 동작

| actionCode | DB 동작 이름 | 이유                    |
|------------|-------------|-------------------------|
| 3          | 엉덩이 박수  | 데이터 없음             |
| 8          | 팔 모으기   | 학습 안 함 (사용자 요청) |

---

## Class Mapping (학습 시 사용)

```python
CLASS_MAPPING = {
    "CLAP": 0,      # actionCode 1
    "ELBOW": 1,     # actionCode 2
    "STRETCH": 2,   # actionCode 4
    "TILT": 3,      # actionCode 5
    "EXIT": 4,      # actionCode 6
    "UNDERARM": 5,  # actionCode 7
    "STAY": 6,      # actionCode 9
}

# 역매핑: Class Index → actionCode
CLASS_TO_ACTION_CODE = {
    0: 1,   # CLAP → actionCode 1
    1: 2,   # ELBOW → actionCode 2
    2: 4,   # STRETCH → actionCode 4
    3: 5,   # TILT → actionCode 5
    4: 6,   # EXIT → actionCode 6
    5: 7,   # UNDERARM → actionCode 7
    6: 9,   # STAY → actionCode 9
}

# actionCode → Class Index
ACTION_CODE_TO_CLASS = {
    1: 0,   # 손 박수 → CLAP
    2: 1,   # 팔 치기 → ELBOW
    4: 2,   # 팔 뻗기 → STRETCH
    5: 3,   # 기우뚱 → TILT
    6: 4,   # 비상구 → EXIT
    7: 5,   # 겨드랑이박수 → UNDERARM
    9: 6,   # 가만히 있음 → STAY
}
```

---

## 주의사항

### 1. actionCode는 연속되지 않음!
- actionCode 3, 8은 건너뜀
- Model class_index는 0부터 연속 (0, 1, 2, 3, 4, 5, 6)

### 2. 학습 후 inference.py 수정 필요
- 기존 `ACTION_CODE_TO_CLASS_INDEX` 매핑을 위 매핑으로 교체
- 7개 클래스로 업데이트

### 3. STAY (가만히 있음) 동작
- actionCode 9
- 정지 상태를 학습하여 오인식 방지
- 이전 문제(가만히 있는데 손 박수 99.8%)를 해결하기 위한 동작

---

## 데이터 구조

```
backend/motion-server/app/forTrain/
├── data/                           # 원본 이미지
│   ├── JSY/
│   │   ├── CLAP/
│   │   ├── ELBOW/
│   │   ├── EXIT/
│   │   ├── STAY/
│   │   ├── STRETCH/
│   │   ├── TILT/
│   │   └── UNDERARM/
│   ├── KSM/
│   ├── LJM/
│   ├── LYW/
│   ├── PJE/
│   └── YHS/
│
└── pose_sequences/                 # 전처리된 .npz
    └── (전처리 후 같은 구조)
```

---

## 학습 명령어

```bash
# 전처리 (이미지 → .npz)
cd backend/motion-server/app/forTrain
python pose_sequence_extractor.py --data_dir ./data --output_dir ./pose_sequences

# 학습
python train_gcn_cnn.py \
    --data_dir ./pose_sequences \
    --epochs 100 \
    --batch_size 32 \
    --actions CLAP ELBOW STRETCH TILT EXIT UNDERARM STAY
```
