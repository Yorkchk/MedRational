package com.example.MedRational.Services.Implementations;

import com.example.MedRational.Repositories.FavoriteRepository;
import com.example.MedRational.Repositories.FileDownloadEventRepository;
import com.example.MedRational.Repositories.FileRatingRepository;
import com.example.MedRational.Repositories.UserFileDownloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

// Rows that point at study files without ON DELETE CASCADE, so they must go before the files do,
// or Postgres refuses the delete. ddl-auto=update never changes existing foreign keys, so adding
// @OnDelete to the entities wouldn't fix databases that already exist.
// file_view_events cascades by itself, and Hibernate removes file_hashtags rows with the file.
// A new table with a file_id column belongs here.
@Component
@RequiredArgsConstructor
public class StudyFileReferences {

    private final FileRatingRepository fileRatingRepository;
    private final FileDownloadEventRepository fileDownloadEventRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserFileDownloadRepository userFileDownloadRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllFor(Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        fileRatingRepository.deleteAllByFileIdIn(fileIds);
        fileDownloadEventRepository.deleteAllByFileIdIn(fileIds);
        favoriteRepository.deleteAllByFileIdIn(fileIds);
        userFileDownloadRepository.deleteAllByFileIdIn(fileIds);
    }
}
