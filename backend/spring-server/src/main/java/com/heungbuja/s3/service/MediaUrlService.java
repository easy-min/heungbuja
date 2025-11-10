package com.heungbuja.s3.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import com.heungbuja.s3.entity.Media;
import com.heungbuja.s3.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private final S3Presigner presigner;
    private final MediaRepository mediaRepository;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.url-ttl-minutes:30}")
    private int ttlMinutes;

    /**
     * DB의 media.id로 프리사인드 URL 발급
     */
    public String issueUrlById(long mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDIA_NOT_FOUND, "존재하지 않는 media ID: " + mediaId));

        return issueUrlByKey(media.getS3Key());
    }

    /**
     * S3 key 직접 지정해서 URL 발급 (테스트용)
     */
    public String issueUrlByKey(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignGetObjectRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(ttlMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest =
                presigner.presignGetObject(presignGetObjectRequest);

        URL url = presignedRequest.url();
        return url.toString();
    }


    public String testPresignedUrl() {
        String s3Key = "song/당돌한여자.mp3";  // S3에 실제 있는 파일 경로

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignGetObjectRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(ttlMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest =
                presigner.presignGetObject(presignGetObjectRequest);

        return presignedRequest.url().toString();
    }

    // 임의의 S3 key를 받아 프리사인드 URL 발급(로컬 테스트용)
    public String testPresignedUrl(String s3Key) {
        return issueUrlByKey(s3Key);
    }

}
