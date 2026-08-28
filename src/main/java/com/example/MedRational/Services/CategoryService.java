package com.example.MedRational.Services;

import com.example.MedRational.DTOs.CategoryRequest;
import com.example.MedRational.DTOs.CategoryResponse;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Entities.Reasoning;
import com.example.MedRational.Entities.StudyFile;
import com.example.MedRational.Repositories.CategoryRepository;
import com.example.MedRational.Repositories.ReasoningRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MappingService mappingService;
    private final ReasoningRepository reasoningRepository;
    private final R2StorageService r2StorageService;


    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(mappingService::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findByIdWithReasonings(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + id));
        return mappingService.toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new IllegalArgumentException("A category with the name '" + request.getName() + "' already exists.");
        }

        Category category = Category.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .build();
        return mappingService.toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + id));

        String updatedName = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(updatedName, id)) {
            throw new IllegalArgumentException("Another category with the name '" + updatedName + "' already exists.");
        }

        category.setName(updatedName);
        category.setDescription(request.getDescription());
        return mappingService.toCategoryResponse(categoryRepository.save(category));
    }




    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findByIdWithReasonings(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + id));

        // Find all files belonging to all reasonings in this category
        List<Reasoning> reasonings = reasoningRepository.findByCategoryIdWithFiles(id);
        List<String> keysToDelete = reasonings.stream()
                .flatMap(r -> r.getFiles().stream())
                .map(StudyFile::getStorageKey)
                .toList();

        // 1. Clean R2
        if (!keysToDelete.isEmpty()) {
            r2StorageService.deleteFiles(keysToDelete);
        }

        // 2. Clean Supabase
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> searchCategories(String keyword) {
        return categoryRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(mappingService::toCategoryResponse)
                .collect(Collectors.toList());
    }
}