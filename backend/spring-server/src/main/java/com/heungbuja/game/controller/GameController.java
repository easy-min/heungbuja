package com.heungbuja.game.controller;

import org.springframework.http.HttpStatus;

import com.heungbuja.game.dto.FrameBatchRequest;
import com.heungbuja.game.dto.FrameBatchResponse;
import com.heungbuja.game.dto.GameStartRequest;
import com.heungbuja.game.dto.GameStartResponse;
import com.heungbuja.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game")
public class GameController {

    private final GameService gameService;

    /**
     * 게임 시작 API
     * @param request (userId, songId)
     * @return 게임 세션 ID 및 노래 메타데이터
     */
    @PostMapping("/start")
    public ResponseEntity<GameStartResponse> startGame(@RequestBody GameStartRequest request) {
        GameStartResponse response = gameService.startGame(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 프레임 묶음 분석 요청 API
     * @param request (sessionId, images, isLastBatchOfVerse1)
     * @return 분석 상태 및 (1절 종료 시) 다음 레벨 정보
     */
    @PostMapping("/frame")
    public ResponseEntity<FrameBatchResponse> analyzeFrames(@RequestBody FrameBatchRequest request) {
        FrameBatchResponse response = gameService.analyzeFrameBatch(request);
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
}