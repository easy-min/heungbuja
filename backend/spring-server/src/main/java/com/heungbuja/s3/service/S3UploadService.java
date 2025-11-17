package com.heungbuja.s3.service;

import com.heungbuja.common.exception.CustomException;
import com.heungbuja.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * S3 파일 업로드 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    /**
     * MultipartFile을 S3에 업로드하고 S3 키를 반환
     *
     * @param file MultipartFile
     * @param folder S3 폴더 경로 (예: "song", "video")
     * @return S3 키 (예: "song/uuid-filename.mp3")
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "파일이 비어있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "파일명이 없습니다.");
        }

        // UUID를 사용해 고유한 파일명 생성
        String uniqueFilename = UUID.randomUUID() + "-" + originalFilename;
        String s3Key = folder + "/" + uniqueFilename;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 업로드 성공: bucket={}, key={}", bucket, s3Key);
            return s3Key;

        } catch (IOException e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED, "S3 업로드에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 오디오 파일 업로드 (song 폴더)
     */
    public String uploadAudioFile(MultipartFile file) {
        return uploadFile(file, "song");
    }

    /**
     * 비디오 파일 업로드 (video 폴더)
     */
    public String uploadVideoFile(MultipartFile file) {
        return uploadFile(file, "video");
    }
}
