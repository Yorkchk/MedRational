package com.example.MedRational.Services;

import com.example.MedRational.Cloudflare.CloudflareProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Client s3Client;
    private final CloudflareProperties cloudflareProperties;

    public String uploadFile(MultipartFile file, String prefix) throws IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String storageKey = prefix + "/" + UUID.randomUUID() + "-" + originalFilename;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(cloudflareProperties.getBucketName())
                .key(storageKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return storageKey;
    }

    public Resource downloadFileAsResource(String storageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(cloudflareProperties.getBucketName())
                .key(storageKey)
                .build();

        ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
        return new InputStreamResource(s3Object);
    }

    public byte[] downloadFileBytes(String storageKey) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(cloudflareProperties.getBucketName())
                .key(storageKey)
                .build();

        return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
    }

    public void deleteFile(String storageKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(cloudflareProperties.getBucketName())
                .key(storageKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    public String buildPublicUrl(String storageKey) {
        return cloudflareProperties.getEndpoint() + "/" + cloudflareProperties.getBucketName() + "/" + storageKey;
    }
}