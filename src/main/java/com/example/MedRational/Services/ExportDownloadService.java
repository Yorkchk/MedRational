package com.example.MedRational.Services;

import com.example.MedRational.DTOs.DownloadableFile;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.CategoryRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import com.example.MedRational.Repositories.StudyFileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ExportDownloadService {

    private final StudyFileRepository studyFileRepository;
    private final ReasoningRepository reasoningRepository;
    private final CategoryRepository categoryRepository;
    private final R2StorageService r2StorageService;

    // 1. Download Single File
    @Transactional(readOnly = true)
    public DownloadableFile downloadSingleFile(Long fileId) {
        StudyFile file = studyFileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with ID: " + fileId));

        Resource resource = r2StorageService.downloadFileAsResource(file.getStorageKey());
        return new DownloadableFile(file.getFileName(), file.getFileType(), resource);
    }

    // 2. Download Selected Files as ZIP
    @Transactional(readOnly = true)
    public DownloadableFile downloadSelectedFilesAsZip(List<Long> fileIds) throws IOException {
        List<StudyFile> files = studyFileRepository.findAllById(fileIds);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (StudyFile file : files) {
                byte[] data = r2StorageService.downloadFileBytes(file.getStorageKey());
                ZipEntry entry = new ZipEntry(file.getFileName());
                zos.putNextEntry(entry);
                zos.write(data);
                zos.closeEntry();
            }
        }
        return new DownloadableFile("selected_files.zip", "application/zip", new ByteArrayResource(baos.toByteArray()));
    }

    // 3. Download an entire Reasoning (Notes markdown/text + attached media)
    @Transactional(readOnly = true)
    public DownloadableFile downloadReasoningAsZip(Long reasoningId) throws IOException {
        Reasoning reasoning = reasoningRepository.findByIdWithFiles(reasoningId)
                .orElseThrow(() -> new EntityNotFoundException("Reasoning not found with ID: " + reasoningId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Write Summary / Notes file
            String content = "# " + reasoning.getTitle() + "\n\n" + (reasoning.getContent() != null ? reasoning.getContent() : "");
            ZipEntry notesEntry = new ZipEntry("summary.md");
            zos.putNextEntry(notesEntry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Write attached files
            for (StudyFile file : reasoning.getFiles()) {
                byte[] data = r2StorageService.downloadFileBytes(file.getStorageKey());
                ZipEntry entry = new ZipEntry("files/" + file.getFileName());
                zos.putNextEntry(entry);
                zos.write(data);
                zos.closeEntry();
            }
        }

        String zipName = sanitizeFilename(reasoning.getTitle()) + ".zip";
        return new DownloadableFile(zipName, "application/zip", new ByteArrayResource(baos.toByteArray()));
    }

    // 4. Download a Category (Structured folder with all reasonings and their files)
    @Transactional(readOnly = true)
    public DownloadableFile downloadCategoryAsZip(Long categoryId) throws IOException {
        Category category = categoryRepository.findByIdWithReasonings(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + categoryId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addCategoryToZip(category, zos, "");
        }

        String zipName = sanitizeFilename(category.getName()) + ".zip";
        return new DownloadableFile(zipName, "application/zip", new ByteArrayResource(baos.toByteArray()));
    }

    // 5. Download the entire catalog of all Categories
    @Transactional(readOnly = true)
    public DownloadableFile downloadAllCategoriesAsZip() throws IOException {
        List<Category> allCategories = categoryRepository.findAll();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Category category : allCategories) {
                // Eagerly fetch reasonings and files for export
                Category fullCategory = categoryRepository.findByIdWithReasonings(category.getId()).orElse(category);
                addCategoryToZip(fullCategory, zos, sanitizeFilename(fullCategory.getName()) + "/");
            }
        }
        return new DownloadableFile("medrational_full_export.zip", "application/zip", new ByteArrayResource(baos.toByteArray()));
    }

    private void addCategoryToZip(Category category, ZipOutputStream zos, String rootPrefix) throws IOException {
        List<Reasoning> reasonings = reasoningRepository.findByCategoryIdWithFiles(category.getId());

        for (Reasoning reasoning : reasonings) {
            String folder = rootPrefix + sanitizeFilename(reasoning.getTitle()) + "/";

            // Add summary notes
            String content = "# " + reasoning.getTitle() + "\n\n" + (reasoning.getContent() != null ? reasoning.getContent() : "");
            ZipEntry notesEntry = new ZipEntry(folder + "summary.md");
            zos.putNextEntry(notesEntry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add files
            for (StudyFile file : reasoning.getFiles()) {
                byte[] data = r2StorageService.downloadFileBytes(file.getStorageKey());
                ZipEntry fileEntry = new ZipEntry(folder + file.getFileName());
                zos.putNextEntry(fileEntry);
                zos.write(data);
                zos.closeEntry();
            }
        }
    }

    private String sanitizeFilename(String input) {
        return input.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }
}