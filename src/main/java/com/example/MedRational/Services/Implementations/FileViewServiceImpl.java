package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Entities.FileViewEvent;
import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.FileViewEventRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import com.example.MedRational.Services.Interfaces.FileViewService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileViewServiceImpl implements FileViewService {

    // Going back and forth to the same file while studying counts as one view
    static final Duration REPEAT_WINDOW = Duration.ofMinutes(30);

    private final FileViewEventRepository viewEventRepository;
    private final StudyFileRepository studyFileRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void recordView(User user, Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));
        // Admins open files to manage them, which says nothing about what students need
        if (user.getRole() != Role.ROLE_USER) return;

        LocalDateTime now = LocalDateTime.now(clock);
        if (viewEventRepository.existsByUserIdAndFileIdAndCreatedAtAfter(user.getId(), fileId, now.minus(REPEAT_WINDOW))) {
            return;
        }
        viewEventRepository.save(FileViewEvent.builder()
                .file(file)
                .user(user)
                .createdAt(now)
                .build());
    }
}
