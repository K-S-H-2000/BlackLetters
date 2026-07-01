package com.example.BlackLetters_spring_boot.controller;

import com.example.BlackLetters_spring_boot.domain.Category;
import com.example.BlackLetters_spring_boot.service.CategoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<Category>> getCategories(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(categoryService.getUserCategories(userId));
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(
            @AuthenticationPrincipal Long userId,
            @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.createCustomCategory(userId, request.getName()));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<Category> updateCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long categoryId,
            @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(userId, categoryId, request.getName()));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long categoryId) {
        categoryService.deleteCategory(userId, categoryId);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class CreateCategoryRequest {
        private String name;
    }

    @Data
    static class UpdateCategoryRequest {
        private String name;
    }
}
