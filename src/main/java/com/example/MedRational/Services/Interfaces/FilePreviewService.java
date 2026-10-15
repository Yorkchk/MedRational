package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.DownloadableFile;

public interface FilePreviewService {

    DownloadableFile getPreview(Long fileId);

    DownloadableFile getAttachmentPreview(Long attachmentId);

    DownloadableFile getSuggestionAttachmentPreview(Long suggestionId);
}
