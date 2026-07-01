package com.example.BlackLetters_spring_boot.service;

import com.example.BlackLetters_spring_boot.domain.Category;
import com.example.BlackLetters_spring_boot.domain.User;
import com.example.BlackLetters_spring_boot.persistence.CategoryRepository;
import com.example.BlackLetters_spring_boot.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Category> getUserCategories(Long userId) {
        // is_active=true인 카테고리만 반환
        return categoryRepository.findGlobalAndUserCategories(userId);
    }

    @Transactional
    public Category createCustomCategory(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Category category = Category.builder()
                .user(user)
                .name(name)
                .build();

        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // 시스템 기본 카테고리(user_id IS NULL)는 삭제 불가
        if (category.getUser() == null) {
            throw new IllegalArgumentException("기본 카테고리는 삭제할 수 없습니다.");
        }

        // 다른 사용자의 카테고리 삭제 불가
        if (!category.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        // 실제 삭제 아닌 소프트 삭제 (is_active = false)
        category.deactivate();
    }

    @Transactional
    public Category updateCategory(Long userId, Long categoryId, String name) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // 시스템 기본 카테고리 수정 불가
        if (category.getUser() == null) {
            throw new IllegalArgumentException("기본 카테고리는 수정할 수 없습니다.");
        }

        // 다른 사용자의 카테고리 수정 불가
        if (!category.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        category.updateName(name);
        return category;
    }
}
