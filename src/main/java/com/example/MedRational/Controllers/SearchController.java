package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.FileSearchResultDTO;
import com.example.MedRational.Services.Interfaces.FileSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final FileSearchService fileSearchService;

    @GetMapping("/files")
    public ResponseEntity<Page<FileSearchResultDTO>> searchFiles(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("uploadedAt").descending());
        return ResponseEntity.ok(fileSearchService.searchFiles(query, categoryId, pageable));
    }
}