package com.heungbuja.voice.controller;

import com.heungbuja.voice.dto.VoiceCommandRequest;
import com.heungbuja.voice.dto.VoiceCommandResponse;
import com.heungbuja.voice.service.VoiceCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceCommandService voiceCommandService;

    @PostMapping("/command")
    public ResponseEntity<VoiceCommandResponse> processVoiceCommand(
            @Valid @RequestBody VoiceCommandRequest request) {
        VoiceCommandResponse response = voiceCommandService.processCommand(request);
        return ResponseEntity.ok(response);
    }
}
