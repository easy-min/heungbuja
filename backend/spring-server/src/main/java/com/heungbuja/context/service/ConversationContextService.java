package com.heungbuja.context.service;

import com.heungbuja.context.entity.ConversationContext;
import com.heungbuja.song.enums.PlaybackMode;

import java.util.List;

/**
 * 대화 컨텍스트 관리 서비스
 *
 * Redis에 사용자 세션 정보를 저장/조회하여
 * GPT가 컨텍스트를 이해하도록 지원
 */
public interface ConversationContextService {

    /**
     * 컨텍스트 조회 또는 새로 생성
     */
    ConversationContext getOrCreate(Long userId);

    /**
     * 컨텍스트 저장
     */
    ConversationContext save(ConversationContext context);

    /**
     * 모드 변경
     */
    void changeMode(Long userId, PlaybackMode newMode);

    /**
     * 현재 재생 곡 설정
     */
    void setCurrentSong(Long userId, Long songId);

    /**
     * 대기열에 곡 추가
     */
    void addToQueue(Long userId, Long songId);

    /**
     * 대기열에 여러 곡 추가
     */
    void addAllToQueue(Long userId, List<Long> songIds);

    /**
     * 대기열에서 다음 곡 가져오기
     */
    Long pollNextSong(Long userId);

    /**
     * 대기열 초기화
     */
    void clearQueue(Long userId);

    /**
     * 컨텍스트 삭제 (세션 종료)
     */
    void delete(Long userId);

    /**
     * 컨텍스트를 읽기 쉬운 텍스트로 변환 (GPT 프롬프트용)
     */
    String formatContextForGpt(Long userId);
}
