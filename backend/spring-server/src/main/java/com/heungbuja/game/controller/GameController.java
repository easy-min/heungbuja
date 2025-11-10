package com.heungbuja.game.controller;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import com.heungbuja.game.dto.GameStartRequest;
import com.heungbuja.game.dto.GameStartResponse;
import com.heungbuja.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game")
public class GameController {

    private final GameService gameService;

    /**
     * 게임 시작 API
     * JWT 인증 필요, userId는 토큰에서 자동 추출 (보안 강화)
     * @param request (songId만 전달)
     * @return 게임 세션 ID 및 노래 메타데이터
     */
    @PostMapping("/start")
    public ResponseEntity<GameStartResponse> startGame(@RequestBody GameStartRequest request) {
        // JWT 토큰에서 userId 추출 (보안 강화)
        Long authenticatedUserId = getCurrentUserId();

        // Request Body의 userId 검증 (있으면 일치 여부 확인)
        if (request.getUserId() != null && !request.getUserId().equals(authenticatedUserId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인의 게임만 시작할 수 있습니다");
        }

        // userId 강제 설정
        request.setUserId(authenticatedUserId);

        GameStartResponse response = gameService.startGame(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 게임 종료 API
     * @param sessionId 종료할 게임 세션 ID
     */
    @PostMapping("/end")
    public ResponseEntity<Void> endGame(@RequestParam String sessionId) {
        gameService.endGame(sessionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build(); // 내용 없이 성공 상태(204) 응답
    }

    /**
     * SecurityContext에서 현재 인증된 사용자 ID 추출
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }

        throw new CustomException(ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다");
    }
}