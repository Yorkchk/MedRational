package com.example.MedRational.Preview;

import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;

import java.util.Locale;
import java.util.Map;

public enum PreviewKind {
    PDF(null),
    DOCX(DefaultDocumentFormatRegistry.DOCX),
    PPTX(DefaultDocumentFormatRegistry.PPTX),
    XLSX(DefaultDocumentFormatRegistry.XLSX),
    // Served as-is: the client decodes the image itself
    IMAGE(null),
    NONE(null);

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif",
            "bmp", "image/bmp",
            "heic", "image/heic",
            "heif", "image/heif",
            "avif", "image/avif"
    );

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
        if (IMAGE_TYPES.containsKey(extension(name))) return IMAGE;

        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return IMAGE;
        return switch (type) {
            case "application/pdf" -> PDF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX;
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX;
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX;
            default -> NONE;
        };
    }

    // Stored image/* type when there is one, otherwise derived from the extension
    public static String imageContentType(String fileName, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return IMAGE_TYPES.getOrDefault(extension(name), "application/octet-stream");
    }

    private static String extension(String lowerCaseName) {
        int dot = lowerCaseName.lastIndexOf('.');
        return dot >= 0 ? lowerCaseName.substring(dot + 1) : "";
    }
}
