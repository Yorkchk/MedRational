package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.AttachHashtagsRequestDTO;
import com.example.MedRational.DTOs.HashtagResponseDTO;
import com.example.MedRational.Services.HashtagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/files/{fileId}/hashtags")
@RequiredArgsConstructor
public class HashtagController {

    private final HashtagService hashtagService;

    @GetMapping
    public ResponseEntity<List<HashtagResponseDTO>> getFileHashtags(@PathVariable Long fileId) {
        return ResponseEntity.ok(hashtagService.getHashtagsForFile(fileId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Set<HashtagResponseDTO>> attachHashtags(
            @PathVariable Long fileId,
            @Valid @RequestBody AttachHashtagsRequestDTO request) {
        return ResponseEntity.ok(hashtagService.attachHashtagsToFile(fileId, request.getTagNames()));
    }

    @DeleteMapping("/{hashtagId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> removeHashtag(
            @PathVariable Long fileId,
            @PathVariable Long hashtagId) {
        hashtagService.removeHashtagFromFile(fileId, hashtagId);
        return ResponseEntity.noContent().build();
    }
}