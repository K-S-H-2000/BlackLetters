package com.example.project_boot.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id")
    private Long receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount = 0;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "ocr_status", nullable = false, length = 20)
    private String ocrStatus;

    @Lob
    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Receipt(User user, Category category, String merchantName, LocalDateTime transactionDate, Integer totalAmount, String imageUrl, String ocrStatus, String rawOcrText) {
        this.user = user;
        this.category = category;
        this.merchantName = merchantName;
        this.transactionDate = transactionDate;
        this.totalAmount = totalAmount != null ? totalAmount : 0;
        this.imageUrl = imageUrl;
        this.ocrStatus = ocrStatus;
        this.rawOcrText = rawOcrText;
    }
}
