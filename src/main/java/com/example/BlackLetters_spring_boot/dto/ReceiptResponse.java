package com.example.BlackLetters_spring_boot.dto;

import com.example.BlackLetters_spring_boot.domain.Category;
import com.example.BlackLetters_spring_boot.domain.OcrStatus;
import com.example.BlackLetters_spring_boot.domain.Receipt;
import com.example.BlackLetters_spring_boot.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReceiptResponse {
    private Long receiptId;
    private User user;
    private Category category;
    private String merchantName;
    private LocalDateTime transactionDate;
    private Integer totalAmount;
    private String imagePath;
    private OcrStatus ocrStatus;
    private String rawOcrText;
    private LocalDateTime createdAt;

    public static ReceiptResponse from(Receipt receipt, String presignedUrl) {
        return ReceiptResponse.builder()
                .receiptId(receipt.getReceiptId())
                .user(receipt.getUser())
                .category(receipt.getCategory())
                .merchantName(receipt.getMerchantName())
                .transactionDate(receipt.getTransactionDate())
                .totalAmount(receipt.getTotalAmount())
                .imagePath(presignedUrl)
                .ocrStatus(receipt.getOcrStatus())
                .rawOcrText(receipt.getRawOcrText())
                .createdAt(receipt.getCreatedAt())
                .build();
    }
}
