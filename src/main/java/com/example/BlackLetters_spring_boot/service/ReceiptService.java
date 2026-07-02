package com.example.BlackLetters_spring_boot.service;

import com.example.BlackLetters_spring_boot.controller.ReceiptDetailResponse;
import com.example.BlackLetters_spring_boot.controller.ReceiptResponse;
import com.example.BlackLetters_spring_boot.domain.*;
import com.example.BlackLetters_spring_boot.persistence.CategoryRepository;
import com.example.BlackLetters_spring_boot.persistence.ReceiptItemRepository;
import com.example.BlackLetters_spring_boot.persistence.ReceiptRepository;
import com.example.BlackLetters_spring_boot.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final S3UploadService s3UploadService;
    private final GeminiOcrService geminiOcrService;

    @Transactional
    public Receipt processReceiptAndSave(Long userId, Long categoryId, MultipartFile file) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // 1. S3 업로드 (경로만 저장)
        String imagePath = s3UploadService.uploadFile(file);

        // 2. PENDING 상태로 먼저 저장
        Receipt receipt = Receipt.builder()
                .user(user)
                .category(category)
                .imagePath(imagePath)
                .ocrStatus(OcrStatus.PENDING)
                .build();
        receipt = receiptRepository.save(receipt);

        // 3. OCR 텍스트 추출
        Map<String, Object> extractedData = geminiOcrService.extractExpenseInfo(file);

        String merchantName = (String) extractedData.get("merchantName");
        Integer totalAmount = (Integer) extractedData.get("totalAmount");
        LocalDateTime receiptDate = (LocalDateTime) extractedData.get("receiptDate");
        String ocrStatusStr = (String) extractedData.get("ocrStatus");
        String rawOcrText = (String) extractedData.get("rawOcrText");
        OcrStatus ocrStatus = OcrStatus.valueOf(ocrStatusStr);

        // 4. OCR 결과로 영수증 업데이트 (COMPLETED 또는 FAILED)
        receipt.updateOcrResult(ocrStatus, merchantName, totalAmount, receiptDate, rawOcrText);

        // 5. 품목 리스트 저장
        List<Map<String, Object>> items = (List<Map<String, Object>>) extractedData.get("items");
        if (items != null) {
            for (Map<String, Object> itemData : items) {
                String itemName = (String) itemData.get("itemName");
                Integer unitPrice = (Integer) itemData.get("unitPrice");
                Integer quantity = (Integer) itemData.get("quantity");

                ReceiptItem item = ReceiptItem.builder()
                        .receipt(receipt)
                        .itemName(itemName)
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .build();
                receiptItemRepository.save(item);
            }
        }

        return receipt;
    }

    // 영수증 목록 조회
    @Transactional(readOnly = true)
    public List<ReceiptResponse> getUserReceipts(Long userId) {
        List<Receipt> receipts = receiptRepository.findByUserUserIdOrderByTransactionDateDesc(userId);

        return receipts.stream()
                .map(receipt -> {
                    String imageUrl = s3UploadService.getPresignedUrl(receipt.getImagePath());
                    return new ReceiptResponse(receipt, imageUrl);
                })
                .collect(Collectors.toList());
    }

    // 영수증 상세 조회 (품목 포함)
    @Transactional(readOnly = true)
    public ReceiptDetailResponse getReceiptDetail(Long userId, Long receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("영수증을 찾을 수 없습니다."));

        // 본인 영수증인지 확인
        if (!receipt.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        List<ReceiptItem> items = receiptItemRepository.findByReceiptReceiptId(receiptId);
        String imageUrl = s3UploadService.getPresignedUrl(receipt.getImagePath());

        return new ReceiptDetailResponse(receipt, imageUrl, items);
    }

    // 영수증 수정 (OCR 결과 교정)
    @Transactional
    public ReceiptDetailResponse updateReceipt(Long userId, Long receiptId,
                                               String merchantName, Integer totalAmount,
                                               LocalDateTime transactionDate, Long categoryId,
                                               List<Map<String, Object>> items) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("영수증을 찾을 수 없습니다."));

        // 본인 영수증인지 확인
        if (!receipt.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        // 카테고리 변경
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
            receipt.updateCategory(category);
        }

        // 기본 정보 수정
        receipt.updateOcrResult(OcrStatus.COMPLETED, merchantName, totalAmount, transactionDate, receipt.getRawOcrText());

        // 품목 수정 (기존 품목 삭제 후 새로 저장)
        if (items != null) {
            receiptItemRepository.deleteByReceiptReceiptId(receiptId);
            for (Map<String, Object> itemData : items) {
                String itemName = (String) itemData.get("itemName");
                Integer unitPrice = (Integer) itemData.get("unitPrice");
                Integer quantity = (Integer) itemData.get("quantity");
                Integer amount = (Integer) itemData.get("amount");

                ReceiptItem item = ReceiptItem.builder()
                        .receipt(receipt)
                        .itemName(itemName)
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .amount(amount)
                        .build();
                receiptItemRepository.save(item);
            }
        }

        List<ReceiptItem> updatedItems = receiptItemRepository.findByReceiptReceiptId(receiptId);
        String imageUrl = s3UploadService.getPresignedUrl(receipt.getImagePath());
        return new ReceiptDetailResponse(receipt, imageUrl, updatedItems);
    }

    // 영수증 삭제
    @Transactional
    public void deleteReceipt(Long userId, Long receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("영수증을 찾을 수 없습니다."));

        if (!receipt.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        receiptItemRepository.deleteByReceiptReceiptId(receiptId);
        receiptRepository.deleteById(receiptId);
    }
}
