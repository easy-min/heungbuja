# 추론 정규화 문제 수정 필요

## 문제
- **학습**: 8프레임 전체의 max norm으로 정규화
- **추론**: 각 프레임별로 개별 정규화

## 해결 방법

`app/services/inference.py`의 `_frames_to_keypoints` 메서드를 수정해야 합니다.

### 현재 코드 (line 450-487):
```python
def _frames_to_keypoints(self, frames: Iterable[str]) -> Tuple[np.ndarray, float, float]:
    keypoints = []
    for encoded in frames:
        # ... 이미지 디코딩 ...
        coords = self.pose_extractor.extract(image)  # 여기서 개별 정규화됨!
        if np.any(coords):
            keypoints.append(coords)

    keypoint_array = np.stack(keypoints, axis=0).astype(np.float32)
    return keypoint_array, decode_elapsed, pose_elapsed
```

### 수정 필요:
1. `pose_extractor.extract`에서 정규화 **안 하도록** 수정
2. 모든 프레임 수집 후, **전체 시퀀스를 한 번에 정규화**

학습 코드의 `normalize_landmarks`와 동일하게 처리해야 함.
