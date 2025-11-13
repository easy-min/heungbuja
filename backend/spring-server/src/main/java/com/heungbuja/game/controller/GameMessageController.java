package com.heungbuja.game.controller;

import com.heungbuja.game.dto.WebSocketFrameRequest;
import com.heungbuja.game.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Slf4j
@Controller // STOMP 메시지 처리는 @RestController가 아닌 @Controller를 사용합니다.
@RequiredArgsConstructor
public class GameMessageController {

    private final GameService gameService;

    /**
     * 프론트엔드로부터 실시간 프레임 데이터를 받는 WebSocket 엔드포인트.
     * 클라이언트는 "/app/game/frame" 주소로 메시지를 보냅니다.
     * STOMP에서는 sessionId를 헤더나 메시지 본문에 담아 보내는 것이 일반적입니다.
     */
    @MessageMapping("/game/frame")
    public void processFrame(WebSocketFrameRequest request) {
        // 유효성 검증: sessionId가 있는지 확인
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            log.warn("sessionId가 없는 WebSocket 프레임 요청이 들어왔습니다.");
            return; // 조용히 무시하거나, 특정 사용자에게 에러 메시지를 보낼 수 있음
        }

        log.trace("WebSocket 프레임 수신: sessionId={}", request.getSessionId()); // 로그 레벨을 trace로 낮춰서 평소에는 안보이게 함

        // 실제 처리 로직은 GameService에 위임
        gameService.processFrame(request);
    }

    @MessageMapping("/game/ping") // <-- 파라미터 없는 간단한 주소
    public void handlePing() {
        log.info("!!!!!!!! PING 메시지 수신 성공 !!!!!!!!");
    }

    /**
     * 1절 종료 후, 레벨 결정을 요청하는 엔드포인트
     * 클라이언트는 "/app/game/decide-level" 주소로 sessionId만 담아 메시지를 보냅니다.
     */
//    @MessageMapping("/game/decide-level")
//    public void decideNextLevel(Map<String, String> payload) {
//        String sessionId = payload.get("sessionId");
//        if (sessionId != null && !sessionId.isBlank()) {
//            log.info("레벨 결정 요청 수신: sessionId={}", sessionId);
//            gameService.decideAndSendNextLevel(sessionId);
//        } else {
//            log.warn("sessionId가 없는 레벨 결정 요청이 들어왔습니다.");
//        }
//    }
}