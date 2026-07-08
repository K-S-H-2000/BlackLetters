# 🧾 Smart Receipt Expense Manager

Spring Boot 기반의 **스마트 영수증 인식 및 소비 관리 서비스** 백엔드 애플리케이션입니다.  
사용자가 영수증 이미지를 업로드하면 **AWS S3**에 안전하게 저장하고, **Google Gemini API(OCR)**를 활용하여 영수증 내 텍스트, 결제 금액, 가맹점 정보를 자동으로 추출/분석합니다. 또한, 카테고리별 예산을 설정하여 스마트한 자산 및 지출 관리를 지원합니다.

---

## 🚀 주요 기능 (Key Features)

1. **사용자 인증 및 보안 (Security & Auth)**
   * Spring Security와 JWT(JSON Web Token)를 활용한 토큰 기반 인증 구현.
   * OAuth2 클라이언트 연동 기능 지원.

2. **스마트 영수증 관리 & OCR 분석 (Smart Receipt & OCR)**
   * **AWS S3 업로드**: 영수증 이미지를 클라우드 저장소에 안전하게 저장.
   * **Google Gemini API 연동**: 영수증 이미지를 분석하여 가맹점명(Merchant), 결제 일자(Transaction Date), 총 결제 금액(Total Amount) 및 세부 품목 목록(Receipt Items)을 자동 추출.

3. **카테고리 관리 (Category Management)**
   * 식비, 교통비, 쇼핑 등 개인 맞춤형 지출 카테고리 설정 및 관리.

4. **예산 설정 및 지출 제어 (Budget Management)**
   * 월별/카테고리별 예산(Budget) 설정.
   * 예산 대비 실제 영수증 지출액을 비교 분석하여 효율적인 소비 패턴 형성 유도 (금액 데이터는 소수점 오차 방지를 위해 `BigDecimal` 타입 사용).

---

## 🛠 기술 스택 (Tech Stack)

### Backend
* **Language**: Java 17
* **Framework**: Spring Boot 3.x / 4.x
* **Database**: MySQL, Spring Data JPA
* **Security**: Spring Security, JWT (JJWT), OAuth2 Client
* **Cloud & AI**: AWS SDK v2 (S3), Google Gemini API (OCR)
* **Build Tool**: Maven

---

## 📂 패키지 구조 (Directory Structure)

```text
src/main/java/com/example/BlackLetters_spring_boot/
├── config/                 # 설정 클래스 (AWS, Security 등)
│   ├── AwsConfig.java      # AWS S3 클라이언트 빈 등록
│   └── security/           # Security & JWT 필터 및 공급자
├── controller/             # REST API Controller 레이어 및 응답 DTO
│   ├── AuthController.java
│   ├── BudgetController.java
│   ├── CategoryController.java
│   ├── ReceiptController.java
│   ├── ReceiptResponse.java # 영수증 조회 응답 DTO (S3 presigned URL 적용)
│   ├── ReceiptDetailResponse.java # 영수증 상세 응답 DTO
│   └── StatisticsController.java # 통계 컨트롤러
├── domain/                 # JPA Entity 클래스 (Domain Models)
│   ├── Budget.java         # 예산 엔티티 (BigDecimal 적용)
│   ├── Category.java       # 지출 카테고리 엔티티
│   ├── User.java           # 회원 정보 엔티티
│   ├── Receipt.java        # 영수증 기본 정보 엔티티 (OCR 상태 포함)
│   └── ReceiptItem.java    # 영수증 상세 품목 엔티티
├── persistence/            # Spring Data JPA Repository 레이어
└── service/                # Business Logic Service 레이어
    ├── AuthService.java    # 회원가입 및 로그인 처리
    ├── BudgetService.java  # 카테고리별 월간 예산 관리
    ├── CategoryService.java# 카테고리 관리 로직
    ├── S3UploadService.java# AWS S3 파일 업로드
    ├── GeminiOcrService.java# Gemini API OCR 텍스트 추출 및 분석
    ├── ReceiptService.java # 영수증 저장 및 OCR 통합 처리
    └── StatisticsService.java # 카테고리별 소비 및 예산 대비 통계 집계
```

---

## 📊 데이터 모델 (ERD & Entities)

* **User**: 시스템 회원 정보 (이메일, 비밀번호, 권한 등).
* **Category**: 지출 카테고리 (사용자별 혹은 공용 카테고리 매핑).
* **Budget**: 월별 카테고리별 예산 한도 (소수점 오차 없는 `BigDecimal` 사용).
* **Receipt**: 업로드된 영수증 정보 (가맹점, 날짜, 총액, S3 이미지 URL, OCR 처리 상태 등).
* **ReceiptItem**: 단일 영수증 내의 세부 구매 품목 및 금액 정보.

---

## 🔑 주요 API 명세 (API Endpoints)

### 1. 인증 (Authentication)
* `POST /api/v1/auth/kakao` : 카카오 로그인 및 자동 회원가입

### 2. 카테고리 (Category)
* `GET /api/v1/categories` : 카테고리 목록 조회
* `POST /api/v1/categories` : 새 카테고리 추가
* `PATCH /api/v1/categories/{categoryId}` : 카테고리 수정
* `DELETE /api/v1/categories/{categoryId}` : 카테고리 삭제

### 3. 예산 (Budget)
* `GET /api/v1/budgets?yearMonth=YYYY-MM` : 특정 월의 카테고리별 예산 목록 조회
* `POST /api/v1/budgets` : 특정 카테고리의 예산 설정 및 수정 (BigDecimal 대응)

### 4. 영수증 (Receipt)
* `POST /api/v1/receipts` : 영수증 이미지 업로드 및 OCR 분석 요청 (Multipart Form-Data)
* `GET /api/v1/receipts` : 사용자의 영수증 목록 및 소비 내역 조회 (S3 Presigned URL 적용)
* `GET /api/v1/receipts/{receiptId}` : 영수증 상세 조회 (세부 품목 리스트 포함)
* `PATCH /api/v1/receipts/{receiptId}` : 영수증 내용 수정 (OCR 결과 오차 직접 교정)
* `DELETE /api/v1/receipts/{receiptId}` : 영수증 내역 삭제

### 5. 통계 (Statistics)
* `GET /api/v1/statistics/monthly?yearMonth=YYYY-MM` : 월별 카테고리별 지출 통계 조회
* `GET /api/v1/statistics/budget?yearMonth=YYYY-MM` : 카테고리 예산 대비 사용률 통계 조회
* `GET /api/v1/statistics/alerts?yearMonth=YYYY-MM` : 예산 사용률 80% 이상인 알림 대상 조회

---

## ⚙️ 실행 방법 (Getting Started)

### 1. 환경 변수 설정
`src/main/resources/application.yml` 파일 또는 시스템 환경 변수에 아래 정보를 설정해야 합니다:

* **MySQL 연결 설정**:
  * `spring.datasource.url`
  * `spring.datasource.username`
  * `spring.datasource.password`
* **AWS 자격 증명 (S3 이용 목적)**:
  * `cloud.aws.credentials.access-key`
  * `cloud.aws.credentials.secret-key`
  * `cloud.aws.region.static` (기본값: ap-northeast-2)
  * `cloud.aws.s3.bucket`
* **Google Gemini API 설정 (OCR 이용 목적)**:
  * `gemini.api-key` (제미나이 API 키 입력)
  * `gemini.url` (기본값: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent)
* **JWT 비밀키**:
  * `jwt.secret` (최소 256비트 이상의 문자열)

### 2. 빌드 및 실행 (Maven Wrapper)

**Windows (PowerShell/CMD):**
```bash
# 빌드 및 테스트 컴파일
./mvnw clean compile

# 애플리케이션 실행
./mvnw spring-boot:run
```

**Linux / macOS:**
```bash
chmod +x mvnw
# 빌드 및 테스트 컴파일
./mvnw clean compile

# 애플리케이션 실행
./mvnw spring-boot:run
```
