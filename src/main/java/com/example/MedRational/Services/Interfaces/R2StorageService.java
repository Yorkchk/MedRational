package com.example.MedRational.Services.Interfaces;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface R2StorageService {

    String uploadFile(MultipartFile file, String prefix) throws IOException;

    Resource downloadFileAsResource(String storageKey);

    byte[] downloadFileBytes(String storageKey) throws IOException;

    void deleteFile(String storageKey);

    String buildPublicUrl(String storageKey);

    void deleteFiles(List<String> storageKeys);
}