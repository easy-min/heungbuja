package com.heungbuja.context.service.impl;

import com.heungbuja.context.entity.ConversationContext;
import com.heungbuja.context.repository.ConversationContextRepository;
import com.heungbuja.context.service.ConversationContextService;
import com.heungbuja.song.entity.Song;
import com.heungbuja.song.enums.PlaybackMode;
import com.heungbuja.song.repository.jpa.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 대화 컨텍스트 관리 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextServiceImpl implements ConversationContextService {

    private final ConversationContextRepository contextRepository;
    private final SongRepository songRepository;

    @Override
    public ConversationContext getOrCreate(Long userId) {
        String key = ConversationContext.createKey(userId);

        // ID로 직접 조회 (더 확실함)
        return contextRepository.findById(key)
                .map(context -> {
                    log.debug("기존 컨텍스트 조회 성공: userId={}, currentSongId={}", userId, context.getCurrentSongId());
                    return context;
                })
                .orElseGet(() -> {
                    ConversationContext newContext = ConversationContext.builder()
                            .id(key)
                            .userId(userId)
                            .currentMode(PlaybackMode.HOME)
                            .build();

                    log.info("새 대화 컨텍스트 생성: userId={}", userId);
                    return contextRepository.save(newContext);
                });
    }

    @Override
    public ConversationContext save(ConversationContext context) {
        return contextRepository.save(context);
    }

    @Override
    public void changeMode(Long userId, PlaybackMode newMode) {
        ConversationContext context = getOrCreate(userId);
        context.changeMode(newMode);
        contextRepository.save(context);

        log.info("모드 변경: userId={}, mode={}", userId, newMode);
    }

    @Override
    public void setCurrentSong(Long userId, Long songId) {
        ConversationContext context = getOrCreate(userId);
        context.setCurrentSong(songId);
        contextRepository.save(context);

        log.info("현재 재생 곡 설정: userId={}, songId={}", userId, songId);
    }

    @Override
    public void addToQueue(Long userId, Long songId) {
        ConversationContext context = getOrCreate(userId);
        context.addToQueue(songId);
        contextRepository.save(context);

        log.debug("대기열에 곡 추가: userId={}, songId={}", userId, songId);
    }

    @Override
    public void addAllToQueue(Long userId, List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return;
        }

        ConversationContext context = getOrCreate(userId);
        songIds.forEach(context::addToQueue);
        contextRepository.save(context);

        log.info("대기열에 {} 곡 추가: userId={}", songIds.size(), userId);
    }

    @Override
    public Long pollNextSong(Long userId) {
        ConversationContext context = getOrCreate(userId);
        Long nextSongId = context.pollNextSong();
        contextRepository.save(context);

        log.debug("대기열에서 다음 곡 가져오기: userId={}, songId={}", userId, nextSongId);
        return nextSongId;
    }

    @Override
    public void clearQueue(Long userId) {
        ConversationContext context = getOrCreate(userId);
        context.clearQueue();
        contextRepository.save(context);

        log.info("대기열 초기화: userId={}", userId);
    }

    @Override
    public void delete(Long userId) {
        contextRepository.deleteByUserId(userId);
        log.info("대화 컨텍스트 삭제: userId={}", userId);
    }

    @Override
    public String formatContextForGpt(Long userId) {
        ConversationContext context = getOrCreate(userId);

        // 현재 재생 중인 곡 정보
        String currentSongInfo = "없음";
        if (context.getCurrentSongId() != null) {
            songRepository.findById(context.getCurrentSongId())
                    .ifPresent(song -> {
                        // Use mutable holder pattern to modify in lambda
                    });

            // 람다 외부에서 처리하기 위해 다시 조회
            var songOpt = songRepository.findById(context.getCurrentSongId());
            if (songOpt.isPresent()) {
                Song song = songOpt.get();
                currentSongInfo = String.format("%s - %s", song.getArtist(), song.getTitle());
            }
        }

        // 대기열 정보
        String queueInfo = "비어있음";
        if (context.getPlaylistQueue() != null && !context.getPlaylistQueue().isEmpty()) {
            queueInfo = String.format("%d곡 대기 중", context.getPlaylistQueue().size());
        }

        return String.format("""
                현재 모드: %s
                재생 중인 곡: %s
                대기열: %s
                마지막 상호작용: %s
                """,
                getModeDescription(context.getCurrentMode()),
                currentSongInfo,
                queueInfo,
                context.getLastInteractionAt()
        );
    }

    /**
     * 모드 설명 텍스트 변환
     */
    private String getModeDescription(PlaybackMode mode) {
        return switch (mode) {
            case HOME -> "홈 화면";
            case LISTENING -> "노래 감상 모드";
            case EXERCISE -> "체조 모드";
        };
    }
}
