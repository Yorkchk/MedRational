package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.DownloadableFile;

public interface FilePreviewService {

    DownloadableFile getPreview(Long fileId);
}
