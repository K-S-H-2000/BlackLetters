package com.example.BlackLetters_spring_boot.controller;

import com.example.BlackLetters_spring_boot.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    // 월별 카테고리별 지출 통계
    // GET /api/v1/statistics/monthly?yearMonth=2026-07
    @GetMapping("/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyCategoryStats(
            @AuthenticationPrincipal Long userId,
            @RequestParam("yearMonth") String yearMonth) {
        return ResponseEntity.ok(statisticsService.getMonthlyCategoryStats(userId, yearMonth));
    }

    // 예산 대비 사용률
    // GET /api/v1/statistics/budget?yearMonth=2026-07
    @GetMapping("/budget")
    public ResponseEntity<Map<String, Object>> getBudgetUsageStats(
            @AuthenticationPrincipal Long userId,
            @RequestParam("yearMonth") String yearMonth) {
        return ResponseEntity.ok(statisticsService.getBudgetUsageStats(userId, yearMonth));
    }

    // 예산 알림 대상 조회 (usage_rate >= 80%)
    // GET /api/v1/statistics/alerts?yearMonth=2026-07
    @GetMapping("/alerts")
    public ResponseEntity<?> getBudgetAlerts(
            @AuthenticationPrincipal Long userId,
            @RequestParam("yearMonth") String yearMonth) {
        return ResponseEntity.ok(statisticsService.getBudgetAlerts(userId, yearMonth));
    }
}