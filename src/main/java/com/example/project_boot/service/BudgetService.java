package com.example.project_boot.service;

import com.example.project_boot.domain.Budget;
import com.example.project_boot.domain.Category;
import com.example.project_boot.domain.User;
import com.example.project_boot.persistence.BudgetRepository;
import com.example.project_boot.persistence.CategoryRepository;
import com.example.project_boot.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Budget> getMonthlyBudgets(Long userId, String yearMonth) {
        // yearMonth format: YYYY-MM
        LocalDate budgetDate = LocalDate.parse(yearMonth + "-01");
        return budgetRepository.findByUserUserIdAndBudgetMonth(userId, budgetDate);
    }

    @Transactional
    public Budget setBudget(Long userId, Long categoryId, String yearMonth, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        LocalDate budgetDate = LocalDate.parse(yearMonth + "-01");

        // 이미 해당 월/카테고리에 예산이 있으면 업데이트, 없으면 생성
        List<Budget> existingBudgets = budgetRepository.findByUserUserIdAndBudgetMonth(userId, budgetDate);
        for (Budget existing : existingBudgets) {
            if (existing.getCategory().getCategoryId().equals(categoryId)) {
                // JPA Dirty Checking을 위해 setter 사용하거나 새 객체로 대체. 여기서는 삭제 후 재삽입 방식 혹은 엔티티 update
                // 메소드 필요
                budgetRepository.delete(existing);
            }
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
