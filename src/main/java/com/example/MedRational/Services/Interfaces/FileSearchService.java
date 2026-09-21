package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.FileSearchResultDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FileSearchService {
    Page<FileSearchResultDTO> searchFiles(String query, Long categoryId, Pageable pageable);
}