package com.heungbuja.voice.service.impl;

import com.heungbuja.song.dto.SongResponse;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.service.SongService;
import com.heungbuja.user.entity.User;
import com.heungbuja.user.service.UserService;
import com.heungbuja.voice.dto.VoiceCommandRequest;
import com.heungbuja.voice.dto.VoiceCommandResponse;
import com.heungbuja.voice.entity.VoiceCommand;
import com.heungbuja.voice.repository.VoiceCommandRepository;
import com.heungbuja.voice.service.VoiceCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 음성 명령 처리 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceCommandServiceImpl implements VoiceCommandService {

    private final VoiceCommandRepository voiceCommandRepository;
    private final UserService userService;
    private final SongService songService;

    // 재생 제어 키워드
    private static final List<String> PAUSE_KEYWORDS = Arrays.asList("잠깐", "멈춰", "정지", "일시정지");
    private static final List<String> RESUME_KEYWORDS = Arrays.asList("다시", "계속", "재생");
    private static final List<String> NEXT_KEYWORDS = Arrays.asList("다음", "건너뛰기", "스킵");
    private static final List<String> STOP_KEYWORDS = Arrays.asList("그만", "종료", "끝");

    @Override
    @Transactional
    public VoiceCommandResponse processCommand(VoiceCommandRequest request) {
        User user = userService.findById(request.getUserId());
        String text = request.getText().trim();

        // 의도 파악
        String intent = detectIntent(text);

        // 음성 명령 저장
        VoiceCommand command = VoiceCommand.builder()
                .user(user)
                .rawText(text)
                .intent(intent)
                .build();

        VoiceCommand savedCommand = voiceCommandRepository.save(command);

        // 의도별 처리
        return switch (intent) {
            case "PLAY_SONG" -> handlePlaySong(savedCommand.getId(), text);
            case "PAUSE" -> VoiceCommandResponse.simpleIntent(savedCommand.getId(), "PAUSE");
            case "RESUME" -> VoiceCommandResponse.simpleIntent(savedCommand.getId(), "RESUME");
            case "NEXT" -> VoiceCommandResponse.simpleIntent(savedCommand.getId(), "NEXT");
            case "STOP" -> VoiceCommandResponse.simpleIntent(savedCommand.getId(), "STOP");
            default -> VoiceCommandResponse.withMessage(
                    savedCommand.getId(),
                    "UNKNOWN",
                    "죄송합니다. 이해하지 못했습니다");
        };
    }

    /**
     * 의도 감지
     */
    private String detectIntent(String text) {
        String lower = text.toLowerCase();

        // 재생 제어 명령 감지
        if (containsAny(lower, PAUSE_KEYWORDS)) return "PAUSE";
        if (containsAny(lower, RESUME_KEYWORDS)) return "RESUME";
        if (containsAny(lower, NEXT_KEYWORDS)) return "NEXT";
        if (containsAny(lower, STOP_KEYWORDS)) return "STOP";

        // 노래 재생 명령 ("틀어줘", "들려줘" 등)
        if (lower.contains("틀어") || lower.contains("듣") || lower.contains("재생")) {
            return "PLAY_SONG";
        }

        // 그 외 일반 검색 (가수명이나 곡명만 말한 경우)
        return "PLAY_SONG";
    }

    /**
     * 노래 재생 처리
     */
    private VoiceCommandResponse handlePlaySong(Long commandId, String text) {
        // 텍스트에서 "틀어줘", "들려줘" 등 제거
        String cleanQuery = text
                .replace("틀어줘", "")
                .replace("틀어", "")
                .replace("들려줘", "")
                .replace("들려", "")
                .replace("재생", "")
                .replace("해줘", "")
                .replace("해", "")
                .trim();

        // 곡 검색 (CustomException 발생 시 그대로 던짐)
        Song song = songService.searchSong(cleanQuery);
        SongResponse songResponse = SongResponse.from(song);

        String message = String.format("%s의 '%s'를 재생합니다", song.getArtist(), song.getTitle());

        return VoiceCommandResponse.playSong(commandId, songResponse, message);
    }

    /**
     * 키워드 포함 여부 확인
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }
}
