package com.example.BlackLetters_spring_boot.service;

import com.example.BlackLetters_spring_boot.domain.Budget;
import com.example.BlackLetters_spring_boot.domain.Category;
import com.example.BlackLetters_spring_boot.domain.User;
import com.example.BlackLetters_spring_boot.persistence.BudgetRepository;
import com.example.BlackLetters_spring_boot.persistence.CategoryRepository;
import com.example.BlackLetters_spring_boot.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Budget> getMonthlyBudgets(Long userId, String yearMonth) {
        LocalDate budgetDate = LocalDate.parse(yearMonth + "-01");
        return budgetRepository.findByUserUserIdAndBudgetMonth(userId, budgetDate);
    }

    @Transactional
    public Budget setBudget(Long userId, Long categoryId, String yearMonth, Integer amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        LocalDate budgetDate = LocalDate.parse(yearMonth + "-01");

        // 이미 존재하면 UPDATE, 없으면 INSERT
        Optional<Budget> existing = budgetRepository
                .findByUserUserIdAndCategoryCategoryIdAndBudgetMonth(userId, categoryId, budgetDate);

        if (existing.isPresent()) {
            existing.get().updateAmount(amount);
            return existing.get(); // JPA dirty checking으로 자동 UPDATE
        }

        Budget budget = Budget.builder()
                .user(user)
                .category(category)
                .budgetMonth(budgetDate)
                .amount(amount)
                .build();

        return budgetRepository.save(budget);
    }
}
