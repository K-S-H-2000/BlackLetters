package com.tukorea.blackletters

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tukorea.blackletters.network.BlackLettersApi
import com.tukorea.blackletters.network.Receipt
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

data class MonthlyBalance(
    val month: String,
    val amount: Long,
    val year: String
)

data class UsageItem(
    val date: String,
    val title: String,
    val amount: Long
)

// 임시 토큰 (로그인 구현 전까지 사용)
const val TEMP_TOKEN = "Bearer YOUR_TOKEN_HERE"

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BlackLettersApi.create() }

    var sampleData by remember { mutableStateOf<List<MonthlyBalance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 데이터 로드 (최근 12개월 통계 가져오기 시도)
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val data = mutableListOf<MonthlyBalance>()
            val calendar = Calendar.getInstance()
            
            for (i in 0 until 12) {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val yearMonth = String.format(Locale.US, "%d-%02d", year, month)
                
                try {
                    val stats = api.getBudgetStatistics(TEMP_TOKEN, yearMonth)
                    data.add(0, MonthlyBalance(
                        month = "${month}월",
                        amount = stats.totalRemaining,
                        year = "${year}년"
                    ))
                } catch (e: Exception) {
                    data.add(0, MonthlyBalance("${month}월", 0, "${year}년"))
                }
                calendar.add(Calendar.MONTH, -1)
            }
            sampleData = data
        } catch (e: Exception) {
            Toast.makeText(context, "데이터를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    var selectedMonth by remember { mutableStateOf<MonthlyBalance?>(null) }
    
    LaunchedEffect(sampleData) {
        if (sampleData.isNotEmpty() && selectedMonth == null) {
            selectedMonth = sampleData.last()
        }
    }

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showUsageDetail by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog = true
        } else {
            Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri = tempPhotoUri
            tempPhotoUri?.let { uri ->
                uploadReceipt(context, api, scope, uri)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            capturedImageUri = it
            uploadReceipt(context, api, scope, it)
        }
    }

    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    fun handleUploadClick() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            showImageSourceDialog = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    if (showUsageDetail) {
        BackHandler {
            showUsageDetail = false
        }
    }

    Crossfade(targetState = showUsageDetail, label = "ScreenTransition") { isDetail ->
        if (isDetail) {
            UsageDetailScreen(
                api = api,
                selectedMonth = selectedMonth ?: MonthlyBalance("", 0, ""),
                onBack = { showUsageDetail = false },
                onUploadClick = { handleUploadClick() }
            )
        } else {
            MainDashboard(
                sampleData = sampleData,
                selectedMonth = selectedMonth ?: MonthlyBalance("", 0, ""),
                onMonthSelected = { selectedMonth = it },
                onUsageClick = { showUsageDetail = true }
            )
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("영수증 업로드") },
            text = { Text("사진을 가져올 방법을 선택하세요.") },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    val photoFile = createImageFile()
                    val photoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    tempPhotoUri = photoUri
                    cameraLauncher.launch(photoUri)
                }) {
                    Text("카메라")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("앨범")
                }
            }
        )
    }
}

fun uploadReceipt(context: android.content.Context, api: BlackLettersApi, scope: kotlinx.coroutines.CoroutineScope, uri: Uri) {
    scope.launch {
        try {
            // 이미지 압축 처리 (413 Payload Too Large 에러 방지)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val file = File(context.cacheDir, "upload_receipt.jpg")
            file.outputStream().use { output ->
                // 화질을 70%로 압축하여 파일 크기를 대폭 줄임
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)
            }
            
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            api.uploadReceipt(TEMP_TOKEN, body)
            Toast.makeText(context, "영수증이 업로드되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainDashboard(
    sampleData: List<MonthlyBalance>,
    selectedMonth: MonthlyBalance,
    onMonthSelected: (MonthlyBalance) -> Unit,
    onUsageClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f)
                .background(Color(0xFFFAFAFA))
        ) {
            BalanceBarChart(
                data = sampleData,
                selectedMonth = selectedMonth,
                onMonthSelected = onMonthSelected
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(5f)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val formatter = DecimalFormat("#,###")
                val isProfit = selectedMonth.amount >= 0
                val amountText = formatter.format(kotlin.math.abs(selectedMonth.amount))
                
                Text(
                    text = if (isProfit) "${amountText}원 흑자" else "${amountText}원 적자",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isProfit) Color.Black else Color.Red
                )
            }
            
            item {
                Text(
                    text = "${selectedMonth.year} ${selectedMonth.month} 상세 내역",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }

            items(1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUsageClick() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "사용 내역",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageDetailScreen(
    api: BlackLettersApi,
    selectedMonth: MonthlyBalance,
    onBack: () -> Unit,
    onUploadClick: () -> Unit
) {
    var usageItems by remember { mutableStateOf<List<UsageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedMonth) {
        isLoading = true
        try {
            val receipts = api.getReceipts(TEMP_TOKEN)
            val year = selectedMonth.year.replace("년", "")
            val month = selectedMonth.month.replace("월", "").padStart(2, '0')
            val filtered = receipts.filter { 
                it.transactionDate?.contains("$year-$month") == true
            }.map {
                UsageItem(
                    date = it.transactionDate?.substring(5, 10) ?: "",
                    title = it.merchantName ?: "알 수 없음",
                    amount = it.totalAmount
                )
            }
            usageItems = filtered
        } catch (e: Exception) {
            // 에러 처리
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "${selectedMonth.month} 사용 내역",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(usageItems) { item ->
                    UsageItemRow(item)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onUploadClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("영수증 업로드", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UsageItemRow(item: UsageItem) {
    val formatter = DecimalFormat("#,###")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = item.date, fontSize = 12.sp, color = Color.Gray)
                Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "-${formatter.format(item.amount)}원",
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BalanceBarChart(
    data: List<MonthlyBalance>,
    selectedMonth: MonthlyBalance,
    onMonthSelected: (MonthlyBalance) -> Unit
) {
    val maxAbsValue = data.maxOfOrNull { kotlin.math.abs(it.amount) } ?: 1L
    val scrollState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (data.isNotEmpty()) {
            scrollState.scrollToItem(data.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(30.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Color.LightGray.copy(alpha = 0.5f)
                )
            }
        }

        LazyRow(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(data) { item ->
                BarItem(
                    item = item,
                    isSelected = item == selectedMonth,
                    maxAbsValue = maxAbsValue,
                    onClick = { onMonthSelected(item) }
                )
            }
        }
    }
}

@Composable
fun BarItem(
    item: MonthlyBalance,
    isSelected: Boolean,
    maxAbsValue: Long,
    onClick: () -> Unit
) {
    val barHeightRatio = kotlin.math.abs(item.amount).toFloat() / maxAbsValue
    
    Column(
        modifier = Modifier
            .width(35.dp)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .height(30.dp)
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-2).dp)
        ) {
            if (isSelected) {
                Text(
                    text = item.year,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 10.sp
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = item.month,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else Color.Gray,
                lineHeight = 14.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (item.amount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight(barHeightRatio)
                            .background(
                                if (isSelected) Color.Black else Color.Black.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (item.amount < 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight(barHeightRatio)
                            .background(
                                if (isSelected) Color.Red else Color.Red.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }
        }
    }
}
