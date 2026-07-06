-- BlackLetters DE 배치 파이프라인 쿼리 모음
-- 매일 자정 실행을 가정한 ETL 쿼리

-- ────────────────────────────────────────
-- 1. 월별 카테고리 집계 배치 (ETL)
-- Extract → Transform → Load
-- ────────────────────────────────────────
INSERT INTO monthly_summary
    (user_id, category_id, budget_month, total_spent, usage_rate)
SELECT
    r.user_id,
    r.category_id,
    DATE_FORMAT(r.transaction_date, '%Y-%m-01')      AS budget_month,
    SUM(r.total_amount)                              AS total_spent,
    ROUND(SUM(r.total_amount) / b.amount * 100, 2)   AS usage_rate
FROM receipts r
JOIN categories c ON r.category_id = c.category_id
JOIN budgets b
    ON  b.user_id      = r.user_id
    AND b.category_id  = r.category_id
    AND b.budget_month = DATE_FORMAT(r.transaction_date, '%Y-%m-01')
WHERE DATE_FORMAT(r.transaction_date, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')
GROUP BY
    r.user_id,
    r.category_id,
    DATE_FORMAT(r.transaction_date, '%Y-%m-01'),
    b.amount
ON DUPLICATE KEY UPDATE
    total_spent = VALUES(total_spent),
    usage_rate  = VALUES(usage_rate),
    updated_at  = CURRENT_TIMESTAMP;

-- ────────────────────────────────────────
-- 2. 예산 초과 알림 트리거
-- usage_rate >= 80% 사용자 조회
-- ────────────────────────────────────────
SELECT
    u.name                          AS 사용자,
    c.name                          AS 카테고리,
    ms.budget_month                 AS 월,
    ms.total_spent                  AS 지출액,
    b.amount                        AS 예산,
    ms.usage_rate                   AS 사용률,
    CASE
        WHEN ms.usage_rate >= 100 THEN '예산 초과'
        WHEN ms.usage_rate >= 80  THEN '예산 80% 도달'
        ELSE '정상'
    END                             AS 알림_단계
FROM monthly_summary ms
JOIN users u      ON ms.user_id      = u.user_id
JOIN categories c ON ms.category_id  = c.category_id
JOIN budgets b
    ON  b.user_id      = ms.user_id
    AND b.category_id  = ms.category_id
    AND b.budget_month = ms.budget_month
WHERE ms.usage_rate >= 80
ORDER BY ms.usage_rate DESC;

-- ────────────────────────────────────────
-- 3. OCR 품질 모니터링
-- Gemini 인식 성공/실패율 월별 집계
-- ────────────────────────────────────────
SELECT
    DATE_FORMAT(created_at, '%Y-%m')        AS 월,
    COUNT(*)                                AS 전체_건수,
    SUM(CASE WHEN ocr_status = 'COMPLETED' THEN 1 ELSE 0 END) AS 성공,
    SUM(CASE WHEN ocr_status = 'FAILED'    THEN 1 ELSE 0 END) AS 실패,
    SUM(CASE WHEN ocr_status = 'PENDING'   THEN 1 ELSE 0 END) AS 대기중,
    ROUND(
        SUM(CASE WHEN ocr_status = 'FAILED' THEN 1 ELSE 0 END)
        / COUNT(*) * 100, 2
    )                                       AS 실패율
FROM receipts
GROUP BY DATE_FORMAT(created_at, '%Y-%m')
ORDER BY 월 DESC;
