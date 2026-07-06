package com.example.BlackLetters_spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiOcrService {

    @Value("${gemini.api-key:#{null}}")
    private String apiKey;

    @Value("${gemini.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String apiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> extractExpenseInfo(MultipartFile file) {
        Map<String, Object> extractedData = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        extractedData.put("items", items);

        try {
            if (apiKey == null || apiKey.trim().isEmpty() || "YOUR_GEMINI_API_KEY".equals(apiKey)) {
                throw new IllegalStateException("Gemini API key is not configured.");
            }

            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            String mimeType = file.getContentType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "image/jpeg";
            }

            // Build request payload using Jackson Node API
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("text", "Analyze this receipt image and extract the information. " +
                    "For the category field, you MUST return exactly one of these Korean strings: 식비, 카페/간식, 교통, 쇼핑, 생활용품, 의료/건강, 문화/여가, 통신, 기타. " +
                    "Do NOT translate or modify the category value. Return JSON matching the schema.");

            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Data);

            ObjectNode inlineDataPart = objectMapper.createObjectNode();
            inlineDataPart.set("inlineData", inlineData);

            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(textPart);
            parts.add(inlineDataPart);

            ObjectNode content = objectMapper.createObjectNode();
            content.set("parts", parts);

            ArrayNode contents = objectMapper.createArrayNode();
            contents.add(content);

            // Construct responseSchema JSON
            ObjectNode merchantNameSchema = objectMapper.createObjectNode();
            merchantNameSchema.put("type", "STRING");

            ObjectNode receiptDateSchema = objectMapper.createObjectNode();
            receiptDateSchema.put("type", "STRING");

            ObjectNode totalAmountSchema = objectMapper.createObjectNode();
            totalAmountSchema.put("type", "INTEGER");

            ObjectNode itemNameSchema = objectMapper.createObjectNode();
            itemNameSchema.put("type", "STRING");

            ObjectNode unitPriceSchema = objectMapper.createObjectNode();
            unitPriceSchema.put("type", "INTEGER");

            ObjectNode quantitySchema = objectMapper.createObjectNode();
            quantitySchema.put("type", "INTEGER");

            ObjectNode itemProperties = objectMapper.createObjectNode();
            itemProperties.set("itemName", itemNameSchema);
            itemProperties.set("unitPrice", unitPriceSchema);
            itemProperties.set("quantity", quantitySchema);

            ArrayNode itemRequired = objectMapper.createArrayNode();
            itemRequired.add("itemName");
            itemRequired.add("unitPrice");
            itemRequired.add("quantity");

            ObjectNode itemSchema = objectMapper.createObjectNode();
            itemSchema.put("type", "OBJECT");
            itemSchema.set("properties", itemProperties);
            itemSchema.set("required", itemRequired);

            ObjectNode itemsSchema = objectMapper.createObjectNode();
            itemsSchema.put("type", "ARRAY");
            itemsSchema.set("items", itemSchema);

            ObjectNode categorySchema = objectMapper.createObjectNode();
            categorySchema.put("type", "STRING");

            ObjectNode schemaProperties = objectMapper.createObjectNode();
            schemaProperties.set("merchantName", merchantNameSchema);
            schemaProperties.set("receiptDate", receiptDateSchema);
            schemaProperties.set("totalAmount", totalAmountSchema);
            schemaProperties.set("category", categorySchema);
            schemaProperties.set("items", itemsSchema);

            ArrayNode requiredFields = objectMapper.createArrayNode();
            requiredFields.add("merchantName");
            requiredFields.add("receiptDate");
            requiredFields.add("totalAmount");
            requiredFields.add("category");
            requiredFields.add("items");

            ObjectNode responseSchema = objectMapper.createObjectNode();
            responseSchema.put("type", "OBJECT");
            responseSchema.set("properties", schemaProperties);
            responseSchema.set("required", requiredFields);

            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", responseSchema);

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.set("contents", contents);
            requestBody.set("generationConfig", generationConfig);

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode textNode = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

                if (!textNode.isMissingNode()) {
                    String responseText = textNode.asText();
                    JsonNode data = objectMapper.readTree(responseText);

                    extractedData.put("ocrStatus", "COMPLETED");
                    extractedData.put("rawOcrText", responseText);
                    extractedData.put("merchantName", data.path("merchantName").asText(""));
                    extractedData.put("totalAmount", data.path("totalAmount").asInt(0));
                    extractedData.put("receiptDate", parseReceiptDate(data.path("receiptDate").asText("")));
                    extractedData.put("category", data.path("category").asText("기타"));

                    JsonNode itemsNode = data.path("items");
                    if (itemsNode.isArray()) {
                        for (JsonNode itemNode : itemsNode) {
                            Map<String, Object> itemMap = new HashMap<>();
                            itemMap.put("itemName", itemNode.path("itemName").asText(""));
                            itemMap.put("unitPrice", itemNode.path("unitPrice").asInt(0));
                            itemMap.put("quantity", itemNode.path("quantity").asInt(1));
                            items.add(itemMap);
                        }
                    }
                } else {
                    throw new RuntimeException("Empty response parts from Gemini API. Response body: " + response.body());
                }
            } else {
                throw new RuntimeException("Gemini API call failed with status code: " + response.statusCode() + ", body: " + response.body());
            }

        } catch (Exception e) {
            log.error("Gemini OCR extraction failed: {}", e.getMessage(), e);
            extractedData.put("ocrStatus", "FAILED");
            extractedData.put("rawOcrText", e.getMessage() != null ? e.getMessage() : e.toString());
            extractedData.put("merchantName", "더미 상호명(Gemini 연결안됨)");
            extractedData.put("totalAmount", 15000);
            extractedData.put("receiptDate", LocalDateTime.now());
            extractedData.put("category", "기타");

            Map<String, Object> dummyItem = new HashMap<>();
            dummyItem.put("itemName", "더미 상품");
            dummyItem.put("unitPrice", 15000);
            dummyItem.put("quantity", 1);
            items.add(dummyItem);
        }

        if (!extractedData.containsKey("receiptDate")) {
            extractedData.put("receiptDate", LocalDateTime.now());
        }

        return extractedData;
    }

    private LocalDateTime parseReceiptDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            dateStr = dateStr.trim();
            if (dateStr.length() == 10) {
                return LocalDateTime.parse(dateStr + "T00:00:00");
            }
            if (dateStr.endsWith("Z")) {
                dateStr = dateStr.substring(0, dateStr.length() - 1);
            }
            if (dateStr.contains("+")) {
                dateStr = dateStr.substring(0, dateStr.indexOf("+"));
            }
            return LocalDateTime.parse(dateStr);
        } catch (Exception e) {
            log.warn("Failed to parse receipt date: {}, falling back to now", dateStr);
            return LocalDateTime.now();
        }
    }
}
