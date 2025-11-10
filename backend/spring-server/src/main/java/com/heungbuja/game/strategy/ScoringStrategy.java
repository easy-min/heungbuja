package com.heungbuja.game.strategy;

import java.util.List;

/**
 * 채점 전략에 대한 인터페이스 (규칙)
 */
public interface ScoringStrategy {
    /**
     * 사용자의 동작과 정답을 비교하여 점수를 계산합니다.
     * @param userActionCodes AI가 분석한 사용자 동작 번호 리스트
     * @param correctActionCodes DB에서 조회한 정답 동작 번호 리스트
     * @return 0~100 사이의 점수
     */
    double grade(List<Integer> userActionCodes, List<Integer> correctActionCodes);
}