package com.example.BlackLetters_spring_boot.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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
    @OnDelete(action = OnDeleteAction.CASCADE)
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

    @Column(name = "image_path", nullable = false, length = 1024)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 20)
    private OcrStatus ocrStatus;

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
    public Receipt(User user, Category category, String merchantName, LocalDateTime transactionDate,
                   Integer totalAmount, String imagePath, OcrStatus ocrStatus, String rawOcrText) {
        this.user = user;
        this.category = category;
        this.merchantName = merchantName;
        this.transactionDate = transactionDate;
        this.totalAmount = totalAmount != null ? totalAmount : 0;
        this.imagePath = imagePath;
        this.ocrStatus = ocrStatus;
        this.rawOcrText = rawOcrText;
    }

    public void updateOcrResult(OcrStatus ocrStatus, String merchantName, Integer totalAmount,
                                LocalDateTime transactionDate, String rawOcrText) {
        this.ocrStatus = ocrStatus;
        this.merchantName = merchantName;
        this.totalAmount = totalAmount != null ? totalAmount : 0;
        this.transactionDate = transactionDate;
        this.rawOcrText = rawOcrText;
    }
}
