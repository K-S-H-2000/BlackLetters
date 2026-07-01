package com.example.BlackLetters_spring_boot.persistence;

import com.example.BlackLetters_spring_boot.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserUserIdAndBudgetMonth(Long userId, LocalDate budgetMonth);

    // 특정 유저/카테고리/월 예산 단건 조회 (수정 시 사용)
    Optional<Budget> findByUserUserIdAndCategoryCategoryIdAndBudgetMonth(
            Long userId, Long categoryId, LocalDate budgetMonth);
}
