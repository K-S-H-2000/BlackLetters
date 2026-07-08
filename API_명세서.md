# BlackLetters API 명세서

> Base URL: `http://43.202.24.80:8080`  
> 인증이 필요한 API는 Header에 `Authorization: Bearer {token}` 포함

---

## 인증 (Auth)

### 카카오 로그인 / 회원가입
```
POST /api/v1/auth/kakao
```

**Request Body**
```json
{
  "kakaoId": "카카오 고유 ID",
  "email": "이메일",
  "name": "사용자 이름"
}
```

**Response**
```json
{
  "token": "JWT 토큰"
}
```

---

## 카테고리 (Categories)

> 모든 API 인증 필요

### 카테고리 목록 조회
```
GET /api/v1/categories
```

**Response**
```json
[
  {
    "categoryId": 1,
    "name": "식비",
    "active": true,
    "user": null,
    "createdAt": "2026-07-01T00:00:00"
  }
]
```
> `user`가 `null`이면 기본 카테고리, 값이 있으면 사용자 커스텀 카테고리

---

### 카테고리 생성
```
POST /api/v1/categories
```

**Request Body**
```json
{
  "name": "카테고리 이름"
}
```

**Response**: 생성된 카테고리 객체

---

### 카테고리 수정
```
PATCH /api/v1/categories/{categoryId}
```

**Request Body**
```json
{
  "name": "변경할 이름"
}
```

**Response**: 수정된 카테고리 객체

---

### 카테고리 삭제
```
DELETE /api/v1/categories/{categoryId}
```

**Response**: `204 No Content`

> 실제 삭제가 아닌 소프트 삭제 (`is_active = false`)

---

## 영수증 (Receipts)

> 모든 API 인증 필요

### 영수증 등록 (OCR + AI 카테고리 자동 분류)
```
POST /api/v1/receipts
Content-Type: multipart/form-data
```

**Request (form-data)**

| Key  | Type | 설명        |
|------|------|-------------|
| file | File | 영수증 이미지 |

> `categoryId` 없음 — AI(Gemini)가 자동으로 카테고리 판단

**Response**
```json
{
  "receiptId": 1,
  "merchantName": "이마트",
  "transactionDate": "2026-07-02T00:00:00",
  "totalAmount": 35200,
  "ocrStatus": "COMPLETED",
  "rawOcrText": "...",
  "imagePath": "receipts/uuid_filename.jpg",
  "category": {
    "categoryId": 1,
    "name": "식비"
  },
  "user": { ... },
  "createdAt": "2026-07-02T00:00:00"
}
```

---

### 영수증 목록 조회
```
GET /api/v1/receipts
```

**Response**
```json
[
  {
    "receiptId": 1,
    "merchantName": "이마트",
    "transactionDate": "2026-07-02T00:00:00",
    "totalAmount": 35200,
    "ocrStatus": "COMPLETED",
    "imageUrl": "https://s3.../presigned-url",
    "categoryId": 1,
    "categoryName": "식비",
    "createdAt": "2026-07-02T00:00:00"
  }
]
```

---

### 영수증 상세 조회
```
GET /api/v1/receipts/{receiptId}
```

**Response**
```json
{
  "receiptId": 1,
  "merchantName": "이마트",
  "transactionDate": "2026-07-02T00:00:00",
  "totalAmount": 35200,
  "ocrStatus": "COMPLETED",
  "rawOcrText": "...",
  "imageUrl": "https://s3.../presigned-url",
  "categoryId": 1,
  "categoryName": "식비",
  "createdAt": "2026-07-02T00:00:00",
  "items": [
    {
      "itemId": 1,
      "itemName": "상품명",
      "unitPrice": 1350,
      "quantity": 1,
      "amount": 1350
    }
  ]
}
```

---

### 영수증 수정
```
PATCH /api/v1/receipts/{receiptId}
```

**Request Body** (모든 필드 선택사항 — 보낸 필드만 수정)
```json
{
  "merchantName": "수정할 상호명",
  "totalAmount": 10000,
  "transactionDate": "2026-07-02T00:00:00",
  "categoryId": 2,
  "items": [
    {
      "itemName": "품목명",
      "unitPrice": 1000,
      "quantity": 2,
      "amount": 2000
    }
  ]
}
```

**Response**: 수정된 영수증 상세 객체

---

### 영수증 삭제
```
DELETE /api/v1/receipts/{receiptId}
```

**Response**: `204 No Content`

---

## 예산 (Budgets)

> 모든 API 인증 필요

### 월별 예산 조회
```
GET /api/v1/budgets?yearMonth=YYYY-MM
```

**Response**
```json
[
  {
    "budgetId": 1,
    "amount": 300000,
    "budgetMonth": "2026-07-01",
    "category": { ... },
    "createdAt": "2026-07-01T00:00:00"
  }
]
```

---

### 예산 설정 (등록/수정)
```
POST /api/v1/budgets
```

**Request Body**
```json
{
  "categoryId": 1,
  "yearMonth": "2026-07",
  "amount": 300000
}
```

**Response**: 설정된 예산 객체

> 같은 월/카테고리에 이미 예산이 있으면 UPDATE, 없으면 INSERT

---

## 통계 (Statistics)

> 모든 API 인증 필요

### 월별 카테고리별 지출 통계
```
GET /api/v1/statistics/monthly?yearMonth=YYYY-MM
```

**Response**
```json
{
  "yearMonth": "2026-07",
  "totalSpending": 50000,
  "categories": [
    {
      "categoryId": 1,
      "categoryName": "식비",
      "totalSpent": 50000
    }
  ]
}
```

---

### 예산 대비 사용률
```
GET /api/v1/statistics/budget?yearMonth=YYYY-MM
```

**Response**
```json
{
  "yearMonth": "2026-07",
  "totalBudget": 300000,
  "totalSpent": 50000,
  "totalRemaining": 250000,
  "totalUsageRate": 16.7,
  "categories": [
    {
      "categoryId": 1,
      "categoryName": "식비",
      "budgetAmount": 300000,
      "spentAmount": 50000,
      "remainingAmount": 250000,
      "usageRate": 16.7
    }
  ]
}
```

---

### 예산 알림 대상 조회
```
GET /api/v1/statistics/alerts?yearMonth=YYYY-MM
```

> 예산 사용률 80% 이상인 카테고리 반환

**Response**
```json
[
  {
    "categoryId": 1,
    "categoryName": "식비",
    "totalSpent": 90000,
    "usageRate": 90.00,
    "alertLevel": "예산 80% 도달"
  }
]
```

> `alertLevel`: `"예산 80% 도달"` 또는 `"예산 초과"`

---

## 공통 에러 응답

| 상태코드 | 설명 |
|---------|------|
| 400 | 잘못된 요청 (필드 누락 등) |
| 403 | 인증 실패 (토큰 없음 또는 만료) |
| 404 | 리소스 없음 |
| 500 | 서버 오류 |
