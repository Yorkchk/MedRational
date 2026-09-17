package com.example.MedRational.Controllers;

import com.example.MedRational.DTOs.CategoryRequest;
import com.example.MedRational.DTOs.CategoryResponse;
import com.example.MedRational.Services.Implementations.CategoryServiceImpl;
import com.example.MedRational.Services.Interfaces.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // Public / Student: Fetch all categories
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    // Public / Student: Fetch category by ID with its reasonings
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    // Public / Student: Search categories
    @GetMapping("/search")
    public ResponseEntity<List<CategoryResponse>> searchCategories(@RequestParam String query) {
        return ResponseEntity.ok(categoryService.searchCategories(query));
    }

    // Admin Only: Create new category
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    // Admin Only: Update category
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    // Admin Only: Delete category
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}