package com.heungbuja.game.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class FrameBatchRequest {
    // [필수]
    /** 현재 1절 또는 2절 내에서의 순서 (예: 1절의 6개 묶음 중 몇 번째인지) */
    private int segmentIndex;

    /** 게임 세션을 식별하는 고유 ID */
    private String sessionId;

    /** 현재 재생 중인 노래의 제목 */
    private String musicTitle;

    /** 현재 재생 중인 노래의 ID (Long 타입으로 받는 것이 더 좋습니다) */
    private Long songId;

    /** 초당 프레임 수 */
    private int fps;

    /** 이번 묶음에 포함된 프레임의 총 개수 */
    private int frameCount;

    /** 해당 묶음의 시작 시간 (노래 기준) */
    private String musicTimeStart;

    /** 해당 묶음의 종료 시간 (노래 기준) */
    private String musicTimeEnd;

    /** 현재 절 정보 (1 또는 2) */
    private int verse;

    /** 프레임 묶음 캡처가 완료된 시점의 타임스탬프 */
    private String captureTimestamp;

    /** (이전 설계의 isLastBatchOfVerse1을 대체) 1절의 마지막 묶음인지 여부 */
    private boolean isLastSegmentOfVerse1;

    // [옵션]
    /** 2절일 경우, 사용자가 수행 중인 안무의 난이도 (1, 2, 3) */
    private Integer difficulty; // Integer 타입으로 하여 null 값을 허용

    // 이미지 데이터는 이전과 동일
    private List<String> images;
}