package com.example.MedRational.Preview;

import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;

import java.util.Locale;

public enum PreviewKind {
    PDF(null),
    DOCX(DefaultDocumentFormatRegistry.DOCX),
    PPTX(DefaultDocumentFormatRegistry.PPTX),
    XLSX(DefaultDocumentFormatRegistry.XLSX),
    NONE(null);

    // Source format for LibreOffice; null when no conversion is needed / possible
    private final DocumentFormat sourceFormat;

    PreviewKind(DocumentFormat sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public DocumentFormat getSourceFormat() {
        return sourceFormat;
    }

    public boolean isPreviewable() {
        return this != NONE;
    }

    public boolean needsConversion() {
        return sourceFormat != null;
    }

    // Extension wins over content type: mobile uploads often arrive as application/octet-stream
    public static PreviewKind detect(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return PDF;
        if (name.endsWith(".docx")) return DOCX;
        if (name.endsWith(".pptx")) return PPTX;
        if (name.endsWith(".xlsx")) return XLSX;

        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "application/pdf" -> PDF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX;
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX;
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX;
            default -> NONE;
        };
    }
}
