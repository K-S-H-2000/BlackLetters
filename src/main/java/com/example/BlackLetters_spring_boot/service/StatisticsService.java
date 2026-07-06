package com.example.BlackLetters_spring_boot.service;

import com.example.BlackLetters_spring_boot.domain.Budget;
import com.example.BlackLetters_spring_boot.domain.MonthlySummary;
import com.example.BlackLetters_spring_boot.persistence.BudgetRepository;
import com.example.BlackLetters_spring_boot.persistence.MonthlySummaryRepository;
import com.example.BlackLetters_spring_boot.persistence.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ReceiptRepository receiptRepository;
    private final BudgetRepository budgetRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;

    // 월별 카테고리별 지출 통계 (monthly_summary 조회)
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyCategoryStats(Long userId, String yearMonth) {
        LocalDate budgetMonth = LocalDate.parse(yearMonth + "-01");

        List<MonthlySummary> summaries = monthlySummaryRepository.findByUserUserIdAndBudgetMonth(userId, budgetMonth);
        Long totalSpending = monthlySummaryRepository.findTotalSpentByUserIdAndBudgetMonth(userId, budgetMonth);

        List<Map<String, Object>> categories = new ArrayList<>();
        for (MonthlySummary summary : summaries) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoryId", summary.getCategory().getCategoryId());
            item.put("categoryName", summary.getCategory().getName());
            item.put("totalSpent", summary.getTotalSpent());
            categories.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("yearMonth", yearMonth);
        result.put("totalSpending", totalSpending != null ? totalSpending : 0);
        result.put("categories", categories);
        return result;
    }

    // 예산 대비 사용률 (monthly_summary의 usage_rate 활용)
    @Transactional(readOnly = true)
    public Map<String, Object> getBudgetUsageStats(Long userId, String yearMonth) {
        LocalDate budgetMonth = LocalDate.parse(yearMonth + "-01");

        List<Budget> budgets = budgetRepository.findByUserUserIdAndBudgetMonth(userId, budgetMonth);
        List<MonthlySummary> summaries = monthlySummaryRepository.findByUserUserIdAndBudgetMonth(userId, budgetMonth);

        Map<Long, MonthlySummary> summaryMap = new HashMap<>();
        for (MonthlySummary summary : summaries) {
            summaryMap.put(summary.getCategory().getCategoryId(), summary);
        }

        List<Map<String, Object>> budgetUsages = new ArrayList<>();
        int totalBudget = 0;
        int totalSpent = 0;

        for (Budget budget : budgets) {
            Long categoryId = budget.getCategory().getCategoryId();
            int budgetAmount = budget.getAmount();

            MonthlySummary summary = summaryMap.get(categoryId);
            int spentAmount = summary != null ? summary.getTotalSpent().intValue() : 0;
            double usageRate = summary != null && summary.getUsageRate() != null
                    ? summary.getUsageRate().doubleValue()
                    : (budgetAmount > 0 ? (double) spentAmount / budgetAmount * 100 : 0);

            Map<String, Object> item = new HashMap<>();
            item.put("categoryId", categoryId);
            item.put("categoryName", budget.getCategory().getName());
            item.put("budgetAmount", budgetAmount);
            item.put("spentAmount", spentAmount);
            item.put("remainingAmount", budgetAmount - spentAmount);
            item.put("usageRate", Math.round(usageRate * 10) / 10.0);

            budgetUsages.add(item);
            totalBudget += budgetAmount;
            totalSpent += spentAmount;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("yearMonth", yearMonth);
        result.put("totalBudget", totalBudget);
        result.put("totalSpent", totalSpent);
        result.put("totalRemaining", totalBudget - totalSpent);
        result.put("totalUsageRate", totalBudget > 0 ? Math.round((double) totalSpent / totalBudget * 1000) / 10.0 : 0);
        result.put("categories", budgetUsages);
        return result;
    }
}
