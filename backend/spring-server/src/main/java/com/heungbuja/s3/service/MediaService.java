package com.heungbuja.s3.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.s3.entity.Media;
import com.heungbuja.s3.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Media 엔티티 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaService {

    private final MediaRepository mediaRepository;

    @Value("${app.s3.bucket}")
    private String bucket;

    /**
     * S3 키로 Media 엔티티 생성
     *
     * @param title 제목
     * @param type 미디어 타입 (MUSIC / VIDEO)
     * @param s3Key S3 키
     * @param uploaderId 업로더 ID (관리자 ID)
     * @return 생성된 Media 엔티티
     */
    @Transactional
    public Media createMedia(String title, String type, String s3Key, Long uploaderId) {
        Media media = new Media();
        media.setTitle(title);
        media.setType(type);
        media.setS3Key(s3Key);
        media.setBucket(bucket);
        media.setUploaderId(uploaderId);

        Media saved = mediaRepository.save(media);
        log.info("Media 생성 완료: id={}, title={}, s3Key={}", saved.getId(), title, s3Key);

        return saved;
    }

    /**
     * Media ID로 조회
     */
    public Media findById(Long id) {
        return mediaRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND, "Media를 찾을 수 없습니다: " + id));
    }

    /**
     * Media 삭제
     */
    @Transactional
    public void deleteMedia(Long id) {
        if (!mediaRepository.existsById(id)) {
            throw new CustomException(ErrorCode.MEDIA_NOT_FOUND, "Media를 찾을 수 없습니다: " + id);
        }
        mediaRepository.deleteById(id);
        log.info("Media 삭제 완료: id={}", id);
    }
}
