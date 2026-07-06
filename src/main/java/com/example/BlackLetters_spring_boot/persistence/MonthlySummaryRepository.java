package com.example.BlackLetters_spring_boot.persistence;

import com.example.BlackLetters_spring_boot.domain.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {

    // 특정 사용자의 특정 월 요약 목록 조회
    List<MonthlySummary> findByUserUserIdAndBudgetMonth(Long userId, LocalDate budgetMonth);

    // 특정 사용자의 특정 월 총 지출 합계
    @Query("SELECT COALESCE(SUM(m.totalSpent), 0) FROM MonthlySummary m WHERE m.user.userId = :userId AND m.budgetMonth = :budgetMonth")
    Long findTotalSpentByUserIdAndBudgetMonth(@Param("userId") Long userId, @Param("budgetMonth") LocalDate budgetMonth);
}
