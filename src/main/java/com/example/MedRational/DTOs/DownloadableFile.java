package com.example.MedRational.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;

@Data
@AllArgsConstructor
public class DownloadableFile {
    private String fileName;
    private String contentType;
    private Resource resource;
}