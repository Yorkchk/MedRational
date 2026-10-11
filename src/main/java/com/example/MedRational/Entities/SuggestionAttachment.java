package com.example.MedRational.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// File attached to a suggestion or to one of its messages. Stored privately in R2 and only
// streamed through the API to the suggestion's owner or an admin. All columns are nullable
// because attachments are optional.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionAttachment {

    @Column(name = "attachment_storage_key", length = 1000)
    private String storageKey;

    @Column(name = "attachment_file_name", length = 255)
    private String fileName;

    @Column(name = "attachment_content_type", length = 255)
    private String contentType;

    @Column(name = "attachment_size_bytes")
    private Long sizeBytes;
}
