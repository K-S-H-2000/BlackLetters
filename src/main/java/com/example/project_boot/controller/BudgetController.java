package com.example.project_boot.controller;

import com.example.project_boot.domain.Budget;
import com.example.project_boot.service.BudgetService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<List<Budget>> getBudgets(
            @AuthenticationPrincipal Long userId,
            @RequestParam("yearMonth") String yearMonth) {
        return ResponseEntity.ok(budgetService.getMonthlyBudgets(userId, yearMonth));
    }

    @PostMapping
    public ResponseEntity<Budget> setBudget(
            @AuthenticationPrincipal Long userId,
            @RequestBody SetBudgetRequest request) {
        Budget budget = budgetService.setBudget(
                userId, request.getCategoryId(), request.getYearMonth(), request.getAmount());
        return ResponseEntity.ok(budget);
    }

    @Data
    static class SetBudgetRequest {
        private Long categoryId;
        private String yearMonth; // "YYYY-MM"
        private BigDecimal amount;
    }
}
