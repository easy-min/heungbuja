package com.heungbuja.game.enums;

public enum GameStatus {
    // --- 성공 상태 ---
    /** 요청이 성공적으로 접수되어 비동기 처리 중*/
    ACCEPTED,

    /** 1절이 종료되었고, 다음 레벨이 결정됨 */
    LEVEL_DECIDED,

    // --- 게임 흐름 상태 ---
    /** 2절까지 모든 채점이 완료되어 게임이 종료됨 */
    GAME_OVER,

    // --- 오류 상태 ---
    /** 세션 ID가 유효하지 않음 */
    INVALID_SESSION,

    /** 요청 데이터가 논리적으로 잘못됨 (예: songId 불일치) */
    INVALID_REQUEST,

    /** AI 분석 서버가 처리에 실패함 */
    AI_PROCESSING_FAILED
}