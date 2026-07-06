package com.example.BlackLetters_spring_boot.controller;

import com.example.BlackLetters_spring_boot.domain.Receipt;
import com.example.BlackLetters_spring_boot.service.ReceiptService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    // 영수증 등록 (사진 업로드 + OCR, 카테고리 AI 자동 판단)
    @PostMapping
    public ResponseEntity<Receipt> uploadReceipt(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) throws Exception {

        Receipt receipt = receiptService.processReceiptAndSave(userId, file);
        return ResponseEntity.ok(receipt);
    }

    // 영수증 목록 조회
    @GetMapping
    public ResponseEntity<List<ReceiptResponse>> getReceipts(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(receiptService.getUserReceipts(userId));
    }

    // 영수증 상세 조회 (품목 포함)
    @GetMapping("/{receiptId}")
    public ResponseEntity<ReceiptDetailResponse> getReceiptDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long receiptId) {
        return ResponseEntity.ok(receiptService.getReceiptDetail(userId, receiptId));
    }

    // 영수증 수정 (OCR 결과 교정)
    @PatchMapping("/{receiptId}")
    public ResponseEntity<ReceiptDetailResponse> updateReceipt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long receiptId,
            @RequestBody UpdateReceiptRequest request) {
        return ResponseEntity.ok(receiptService.updateReceipt(
                userId, receiptId,
                request.getMerchantName(),
                request.getTotalAmount(),
                request.getTransactionDate(),
                request.getCategoryId(),
                request.getItems()
        ));
    }

    // 영수증 삭제
    @DeleteMapping("/{receiptId}")
    public ResponseEntity<Void> deleteReceipt(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long receiptId) {
        receiptService.deleteReceipt(userId, receiptId);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class UpdateReceiptRequest {
        private String merchantName;
        private Integer totalAmount;
        private LocalDateTime transactionDate;
        private Long categoryId;
        private List<Map<String, Object>> items;
    }
}
