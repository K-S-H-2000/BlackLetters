package com.example.BlackLetters_spring_boot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket:#{null}}")
    private String bucket;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    /**
     * 파일을 S3에 업로드하고 객체 경로(키)를 반환합니다.
     * 전체 URL이 아닌 경로만 저장하는 이유: presigned URL은 만료되기 때문에
     * DB에는 영구적인 경로만 저장하고, 조회 시마다 새 URL을 발급합니다.
     */
    public String uploadFile(MultipartFile file) throws IOException {
        String filePath = "receipts/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        if (bucket == null || bucket.isEmpty()) {
            // AWS 미설정 시 더미 경로 반환
            return filePath;
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(filePath)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return filePath;
    }

    /**
     * 저장된 경로로 presigned URL을 생성합니다 (유효시간 1시간).
     * 영수증 조회 API에서 이미지 URL 반환 시 사용합니다.
     */
    public String getPresignedUrl(String imagePath) {
        if (bucket == null || bucket.isEmpty()) {
            return "https://dummy-s3-url.com/" + imagePath;
        }

        S3Presigner presigner = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imagePath)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
        presigner.close();

        return presignedRequest.url().toString();
    }
}
