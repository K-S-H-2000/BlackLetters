-- 영수증 가계부 앱 DB 스키마 (MySQL 8.0 기준)
-- 이 파일 하나를 그대로 실행하면 DB 생성 + 테이블 생성 + 기본 카테고리 시드까지 끝난다.

CREATE DATABASE IF NOT EXISTS receipt_app DEFAULT CHARACTER SET utf8mb4;
USE receipt_app;

CREATE TABLE users (
    user_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    kakao_id    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(255),
    name        VARCHAR(50)  NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- user_id가 NULL이면 시스템 기본 카테고리, 값이 있으면 사용자 커스텀 카테고리
-- is_active=FALSE는 소프트 삭제(논리적 삭제) — 행은 남기고 신규 선택 목록에서만 제외
CREATE TABLE categories (
    category_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NULL,
    name        VARCHAR(50) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_categories_user_name
        UNIQUE (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- image_path: S3/Firebase의 영구 객체 경로(키)만 저장한다.
-- presigned URL은 시간이 지나면 만료되므로 DB에 저장하지 않고,
-- 조회 시점에 이 경로를 이용해 매번 새로 발급한다.
CREATE TABLE receipts (
    receipt_id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT UNSIGNED NOT NULL,
    category_id      BIGINT UNSIGNED NOT NULL,
    merchant_name    VARCHAR(100),
    transaction_date DATETIME,
    total_amount     DECIMAL(12,0) NOT NULL DEFAULT 0,
    image_path       VARCHAR(255) NOT NULL,
    ocr_status       ENUM('PENDING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    raw_ocr_text     TEXT,
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipts_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_receipts_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_receipts_amount CHECK (total_amount >= 0),
    INDEX idx_receipts_user_date (user_id, transaction_date),
    INDEX idx_receipts_user_category_date (user_id, category_id, transaction_date),
    INDEX idx_receipts_ocr_status (ocr_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE receipt_items (
    item_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_id  BIGINT UNSIGNED NOT NULL,
    item_name   VARCHAR(100) NOT NULL,
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  DECIMAL(12,0),
    amount      DECIMAL(12,0) NOT NULL,
    CONSTRAINT fk_items_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts(receipt_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_items_unit_price CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT chk_items_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- budget_month는 매월 1일로 통일해서 저장 (예: '2026-07-01')
CREATE TABLE budgets (
    budget_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    category_id   BIGINT UNSIGNED NOT NULL,
    budget_month  DATE NOT NULL,
    amount        DECIMAL(12,0) NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_budgets_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_budget_period
        UNIQUE (user_id, category_id, budget_month),
    CONSTRAINT chk_budgets_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 월별 카테고리 집계 요약 테이블 (DE 배치 파이프라인 적재 대상)
CREATE TABLE monthly_summary (
    summary_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    category_id   BIGINT UNSIGNED NOT NULL,
    budget_month  DATE NOT NULL,
    total_spent   DECIMAL(12,0) NOT NULL DEFAULT 0,
    usage_rate    DECIMAL(5,2),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_summary_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_summary_category
        FOREIGN KEY (category_id) REFERENCES categories(category_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_summary_period
        UNIQUE (user_id, category_id, budget_month),
    INDEX idx_summary_user_month (user_id, budget_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 시스템 기본 카테고리 시드 데이터 (user_id = NULL → 모든 사용자 공용)
INSERT INTO categories (user_id, name) VALUES
    (NULL, '식비'),
    (NULL, '카페/간식'),
    (NULL, '교통'),
    (NULL, '쇼핑'),
    (NULL, '생활용품'),
    (NULL, '의료/건강'),
    (NULL, '문화/여가'),
    (NULL, '통신'),
    (NULL, '기타');
