package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Cloudflare.CloudflareProperties;
import com.example.MedRational.Services.Interfaces.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class R2StorageServiceImpl implements R2StorageService {

    private final S3Client s3Client;
    private final CloudflareProperties cloudflareProperties;

    public String uploadFile(MultipartFile file, String prefix) throws IOException {
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("\\s+", "_")
                : "file";

        // Format: Categories/{Category}/{Reasoning}/{shortUUID}-{filename}
        String storageKey = prefix + "/" + UUID.randomUUID().toString().substring(0, 8) + "-" + originalFilename;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(cloudflareProperties.getBucketName())
                .key(storageKey)
                .contentType(file.getContentType())
                .build();

        // Fix: Pass byte array directly to avoid stream length mismatches
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

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

    public void deleteFiles(List<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> objectsToDelete = storageKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(cloudflareProperties.getBucketName())
                .delete(Delete.builder().objects(objectsToDelete).build())
                .build();

        s3Client.deleteObjects(request);
    }
}