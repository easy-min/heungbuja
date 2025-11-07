package com.heungbuja.command.service;

import com.heungbuja.command.dto.CommandRequest;
import com.heungbuja.command.dto.CommandResponse;

/**
 * 통합 명령 처리 서비스 인터페이스
 * 의도 분석 → 적절한 서비스 호출 → 응답 생성
 *
 * 주의: 프론트엔드가 음악 재생을 관리하므로, 백엔드는:
 * - 노래 정보만 전달 (audioUrl)
 * - 청취 이력만 기록
 * - 상태 관리 없음
 */
public interface CommandService {

    /**
     * 텍스트 명령어 처리 (통합 엔드포인트)
     */
    CommandResponse processTextCommand(CommandRequest request);
}
