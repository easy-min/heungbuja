package com.heungbuja.voice.service.impl;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.voice.service.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 간단한 TTS 서비스 구현 (로컬 파일 저장)
 * local 프로파일에서만 사용 (Mock)
 */
@Slf4j
@Service
@Profile({"local", "dev", "default"})
public class SimpleTtsServiceImpl implements TtsService {

    @Value("${tts.storage.path:./tts-files}")
    private String storagePath;

    @Override
    public String synthesize(String text, String voiceType) {
        try {
            // 저장 디렉토리 생성
            Path storageDir = Paths.get(storagePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            // 파일 ID 생성
            String fileId = UUID.randomUUID().toString();
            String fileName = fileId + ".mp3";

            log.info("TTS 생성: text='{}', voiceType='{}', fileId='{}'",
                    text, voiceType, fileId);

            // TODO: 실제 TTS API 호출하여 음성 파일 생성
            // 현재는 Mock으로 빈 파일 생성
            Path filePath = storageDir.resolve(fileName);
            Files.createFile(filePath);

            return fileId;

        } catch (IOException e) {
            log.error("TTS 파일 생성 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "TTS 음성 생성에 실패했습니다");
        }
    }

    @Override
    public byte[] getAudioFile(String fileId) {
        try {
            Path filePath = Paths.get(storagePath, fileId + ".mp3");

            if (!Files.exists(filePath)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "TTS 파일을 찾을 수 없습니다");
            }

            return Files.readAllBytes(filePath);

        } catch (IOException e) {
            log.error("TTS 파일 읽기 실패: fileId={}", fileId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "TTS 파일을 읽을 수 없습니다");
        }
    }

    @Override
    public byte[] synthesizeBytes(String text, String voiceType) {
        log.info("TTS 생성 (직접 반환): text='{}', voiceType='{}'", text, voiceType);

        // Mock 구현: 최소한의 유효한 MP3 헤더 반환
        // 실제 환경에서는 OpenAiTtsServiceImpl이 사용됨
        byte[] mockMp3Header = new byte[] {
                (byte)0xFF, (byte)0xFB, (byte)0x90, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        log.warn("SimpleTtsServiceImpl (Mock): 실제 TTS 대신 더미 데이터 반환");
        return mockMp3Header;
    }
}
