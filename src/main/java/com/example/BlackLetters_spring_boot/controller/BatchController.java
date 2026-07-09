package com.example.BlackLetters_spring_boot.controller;

import com.example.BlackLetters_spring_boot.service.BatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> runBatch() {
        batchService.runMonthlySummaryBatch();
        return ResponseEntity.ok(Map.of("message", "배치 쿼리 실행 완료"));
    }
}
