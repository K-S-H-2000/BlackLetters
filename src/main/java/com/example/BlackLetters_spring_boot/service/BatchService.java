package com.example.BlackLetters_spring_boot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final JdbcTemplate jdbcTemplate;

    public void runMonthlySummaryBatch() {
        String sql = "INSERT INTO monthly_summary (user_id, category_id, budget_month, total_spent, usage_rate, created_at, updated_at) " +
                "SELECT " +
                "    r.user_id, " +
                "    r.category_id, " +
                "    DATE_FORMAT(r.transaction_date, '%Y-%m-01') AS budget_month, " +
                "    SUM(r.total_amount) AS total_spent, " +
                "    COALESCE(ROUND(SUM(r.total_amount) / NULLIF(MAX(b.amount), 0) * 100, 2), 0) AS usage_rate, " +
                "    NOW(), " +
                "    NOW() " +
                "FROM receipts r " +
                "LEFT JOIN budgets b " +
                "    ON b.user_id = r.user_id " +
                "    AND b.category_id = r.category_id " +
                "    AND b.budget_month = DATE_FORMAT(r.transaction_date, '%Y-%m-01') " +
                "WHERE r.ocr_status = 'COMPLETED' " +
                "GROUP BY r.user_id, r.category_id, DATE_FORMAT(r.transaction_date, '%Y-%m-01') " +
                "ON DUPLICATE KEY UPDATE " +
                "    total_spent = VALUES(total_spent), " +
                "    usage_rate = VALUES(usage_rate), " +
                "    updated_at = NOW()";

        jdbcTemplate.execute(sql);
        log.info("Monthly summary batch executed successfully");
    }
}
