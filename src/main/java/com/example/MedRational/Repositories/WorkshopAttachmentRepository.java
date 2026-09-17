package com.example.MedRational.Repositories;

import com.example.MedRational.Entities.WorkshopAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkshopAttachmentRepository extends JpaRepository<WorkshopAttachment, Long> {
    List<WorkshopAttachment> findByProjectId(Long projectId);
}