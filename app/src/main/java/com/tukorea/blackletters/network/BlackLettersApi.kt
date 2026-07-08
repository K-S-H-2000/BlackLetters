package com.tukorea.blackletters.network

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class User(
    val userId: Long,
    val kakaoId: String,
    val email: String?,
    val name: String
)

data class Category(
    val categoryId: Long,
    val name: String,
    val active: Boolean? = null
)

data class Receipt(
    val receiptId: Long,
    val merchantName: String?,
    val transactionDate: String?,
    val totalAmount: Long,
    val imageUrl: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val ocrStatus: String?
)

data class ReceiptDetail(
    val receiptId: Long,
    val merchantName: String?,
    val transactionDate: String?,
    val totalAmount: Long,
    val imageUrl: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val ocrStatus: String?,
    val items: List<ReceiptItem>?
)

data class ReceiptItem(
    val itemId: Long,
    val itemName: String,
    val unitPrice: Long?,
    val quantity: Int,
    val amount: Long
)

data class MonthlyStatistics(
    val yearMonth: String,
    val totalSpending: Long,
    val categories: List<CategorySpending>
)

data class BudgetStatistics(
    val yearMonth: String,
    val totalBudget: Long,
    val totalSpent: Long,
    val totalRemaining: Long,
    val totalUsageRate: Double,
    val categories: List<BudgetCategoryStatistics>
)

data class BudgetCategoryStatistics(
    val categoryId: Long,
    val categoryName: String,
    val budgetAmount: Long,
    val spentAmount: Long,
    val remainingAmount: Long,
    val usageRate: Double
)

data class CategorySpending(
    val categoryId: Long,
    val categoryName: String,
    val totalSpent: Long
)

data class KakaoLoginRequest(
    val kakaoId: String,
    val email: String?,
    val name: String
)

data class AuthResponse(
    val token: String
)

interface BlackLettersApi {
    @POST("/api/v1/auth/kakao")
    suspend fun loginWithKakao(@Body request: KakaoLoginRequest): AuthResponse

    @GET("/api/v1/receipts")
    suspend fun getReceipts(@Header("Authorization") token: String): List<Receipt>

    @GET("/api/v1/receipts/{receiptId}")
    suspend fun getReceiptDetail(
        @Header("Authorization") token: String,
        @Path("receiptId") receiptId: Long
    ): ReceiptDetail

    @Multipart
    @POST("/api/v1/receipts")
    suspend fun uploadReceipt(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Receipt

    @GET("/api/v1/statistics/monthly")
    suspend fun getMonthlyStatistics(
        @Header("Authorization") token: String,
        @Query("yearMonth") yearMonth: String
    ): MonthlyStatistics

    @GET("/api/v1/statistics/budget")
    suspend fun getBudgetStatistics(
        @Header("Authorization") token: String,
        @Query("yearMonth") yearMonth: String
    ): BudgetStatistics

    companion object {
        private const val BASE_URL = "http://43.202.24.80:8080"

        fun create(): BlackLettersApi {
            val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BlackLettersApi::class.java)
        }
    }
}
