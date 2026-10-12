package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.WorkshopAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface WorkshopAttachmentRepository extends JpaRepository<WorkshopAttachment, Long> {
    List<WorkshopAttachment> findByProjectId(Long projectId);

    // 0 when the attachment was deleted meanwhile
    @Transactional
    @Modifying
    @Query("UPDATE WorkshopAttachment a SET a.previewStorageKey = :key WHERE a.id = :id")
    int updatePreviewStorageKey(@Param("id") Long id, @Param("key") String key);
}
