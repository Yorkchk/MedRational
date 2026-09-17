package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.DownloadableFile;

import java.io.IOException;
import java.util.List;

public interface ExportDownloadService {

    DownloadableFile downloadSingleFile(Long fileId);

    DownloadableFile downloadSelectedFilesAsZip(List<Long> fileIds) throws IOException;

    DownloadableFile downloadReasoningAsZip(Long reasoningId) throws IOException;

    DownloadableFile downloadCategoryAsZip(Long categoryId) throws IOException;

    DownloadableFile downloadAllCategoriesAsZip() throws IOException;
}