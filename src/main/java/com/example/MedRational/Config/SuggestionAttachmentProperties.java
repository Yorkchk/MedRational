package com.example.MedRational.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "suggestions.attachments")
public class SuggestionAttachmentProperties {

    // Per-file limit for suggestion and reply attachments
    private DataSize maxSize = DataSize.ofMegabytes(10);

    // Lower-case extensions without the dot
    private List<String> allowedExtensions = List.of(
            "pdf", "png", "jpg", "jpeg", "webp", "gif",
            "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv"
    );
}
