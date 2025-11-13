package com.heungbuja.game.controller;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.game.state.GameSession;
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
     * 게임 시작 API (디버깅용 - 인증 없음)
     * Request Body에 포함된 userId와 songId를 그대로 사용하여 게임을 시작합니다.
     * @param request (userId, songId)
     * @return 게임 세션 ID 및 노래 메타데이터
     */
    @PostMapping("/start")
    public ResponseEntity<GameStartResponse> startGame(@RequestBody GameStartRequest request) {
        // --- ▼ (핵심 수정) 보안 관련 로직을 모두 제거하고, Service 호출만 남깁니다 ▼ ---

        // 1. 요청 Body의 유효성 검증 (선택사항이지만 좋은 습관)
        if (request.getUserId() == null || request.getSongId() == null) {
            // ErrorCode에 INVALID_INPUT_VALUE가 있다고 가정
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "userId와 songId는 필수입니다.");
        }

        // 2. 받은 요청(request)을 그대로 GameService에 전달
        GameStartResponse response = gameService.startGame(request);

        // 3. 성공 응답 반환
        return ResponseEntity.ok(response);
        // --- ▲ ------------------------------------------------------------- ▲ ---
    }

    /**
     * 게임 종료 API
     * @param sessionId 종료할 게임 세션 ID
     */
    @PostMapping("/end")
    public ResponseEntity<Void> endGame(@RequestParam String sessionId) {
        // 1. gameService를 이용해 sessionId에 해당하는 GameSession 객체를 먼저 조회합니다.
        GameSession gameSession = gameService.getGameSession(sessionId);

        // 2. 조회한 객체를 endGame 메소드에 전달하여 게임 종료 로직을 수행합니다.
        gameService.endGame(gameSession); // <-- 컴파일 에러 해결!

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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