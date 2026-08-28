package com.example.MedRational.Services;

import com.example.MedRational.DTOs.CategoryRequest;
import com.example.MedRational.DTOs.CategoryResponse;
import com.example.MedRational.Entities.Category;
import com.example.MedRational.Repositories.CategoryRepository;
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
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return mappingService.toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + id));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return mappingService.toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new EntityNotFoundException("Category not found with ID: " + id);
        }
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> searchCategories(String keyword) {
        return categoryRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(mappingService::toCategoryResponse)
                .collect(Collectors.toList());
    }
}