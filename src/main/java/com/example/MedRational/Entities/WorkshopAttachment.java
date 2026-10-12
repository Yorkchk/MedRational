package com.example.MedRational.Entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "workshop_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkshopAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private WorkshopProject project;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    // Office files converted to PDF for preview, cached in R2; null until the first preview
    @Column(name = "preview_storage_key", length = 1000)
    private String previewStorageKey;

    /**
     * The R2 key of the file: the part of fileUrl from "WorkshopAttachments/" on, or null when it isn't
     * there. fileUrl isn't reachable from outside (the bucket is private), so files are read by key.
     */
    public String storageKey() {
        if (fileUrl == null) return null;
        int index = fileUrl.indexOf("WorkshopAttachments/");
        return index != -1 ? fileUrl.substring(index) : null;
    }
}