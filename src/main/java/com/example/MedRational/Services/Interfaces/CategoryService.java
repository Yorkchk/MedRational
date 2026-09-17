package com.example.MedRational.Services.Interfaces;

import com.example.MedRational.DTOs.CategoryRequest;
import com.example.MedRational.DTOs.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long id, CategoryRequest request);

    void deleteCategory(Long id);

    List<CategoryResponse> searchCategories(String keyword);
}