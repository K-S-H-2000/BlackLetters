package com.tukorea.blackletters

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tukorea.blackletters.network.*
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
    val year: String,
    val rawYearMonth: String
)

data class UsageItem(
    val date: String,
    val title: String,
    val amount: Long
)

@Composable
fun MainScreen(token: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BlackLettersApi.create() }
    val bearerToken = "Bearer $token"

    var sampleData by remember { mutableStateOf<List<MonthlyBalance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalBudgetInfo by remember { mutableStateOf<BudgetStatistics?>(null) }

    // 데이터 로드 (최근 12개월의 기본 데이터만 먼저 로드)
    val loadInitialData = suspend {
        isLoading = true
        try {
            val data = mutableListOf<MonthlyBalance>()
            val calendar = Calendar.getInstance()
            
            for (i in 0 until 12) {
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val yearMonth = String.format(Locale.US, "%d-%02d", year, month)
                
                try {
                    val stats = api.getBudgetStatistics(bearerToken, yearMonth)
                    data.add(0, MonthlyBalance(
                        month = "${month}월",
                        amount = stats.totalRemaining,
                        year = "${year}년",
                        rawYearMonth = yearMonth
                    ))
                } catch (e: Exception) {
                    data.add(0, MonthlyBalance("${month}월", 0, "${year}년", yearMonth))
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

    LaunchedEffect(Unit) {
        loadInitialData()
    }

    var selectedMonth by remember { mutableStateOf<MonthlyBalance?>(null) }
    
    // 초기 로드 시 마지막 달 선택
    LaunchedEffect(sampleData) {
        if (sampleData.isNotEmpty() && selectedMonth == null) {
            selectedMonth = sampleData.last()
        }
    }

    // 선택된 월이 바뀔 때마다 상세 예산 정보 갱신
    LaunchedEffect(selectedMonth) {
        selectedMonth?.let { month ->
            try {
                totalBudgetInfo = api.getBudgetStatistics(bearerToken, month.rawYearMonth)
            } catch (e: Exception) {
                totalBudgetInfo = null
            }
        }
    }

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("dashboard") } // dashboard, usage, category, budget
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
                uploadReceipt(context, api, bearerToken, scope, uri) {
                    scope.launch { loadInitialData() }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            capturedImageUri = it
            uploadReceipt(context, api, bearerToken, scope, it) {
                scope.launch { loadInitialData() }
            }
        }
    }

    fun handleUploadClick() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            showImageSourceDialog = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    BackHandler(enabled = currentScreen != "dashboard") {
        currentScreen = "dashboard"
    }

    val currentSelectedMonth = selectedMonth ?: MonthlyBalance("", 0, "", "")

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            "usage" -> UsageDetailScreen(
                api = api,
                token = bearerToken,
                selectedMonth = currentSelectedMonth,
                onBack = { currentScreen = "dashboard" },
                onUploadClick = { handleUploadClick() }
            )
            "category" -> CategoryCustomScreen(
                api = api,
                token = bearerToken,
                onBack = { currentScreen = "dashboard" }
            )
            "budget" -> BudgetEntryScreen(
                api = api,
                token = bearerToken,
                selectedMonth = currentSelectedMonth,
                onBack = { 
                    currentScreen = "dashboard"
                    scope.launch { loadInitialData() }
                }
            )
            else -> MainDashboard(
                sampleData = sampleData,
                selectedMonth = currentSelectedMonth,
                budgetInfo = totalBudgetInfo,
                onMonthSelected = { selectedMonth = it },
                onUsageClick = { currentScreen = "usage" },
                onBudgetClick = { currentScreen = "budget" },
                onCategoryClick = { currentScreen = "category" }
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
                    val photoFile = createImageFile(context)
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

fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}

fun uploadReceipt(context: Context, api: BlackLettersApi, token: String, scope: kotlinx.coroutines.CoroutineScope, uri: Uri, onSuccess: () -> Unit) {
    scope.launch {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            var inSampleSize = 1
            val maxDimension = 1024
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { this.inSampleSize = inSampleSize })
            } ?: return@launch

            val file = File(context.cacheDir, "upload_receipt.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 60, it) }
            
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            api.uploadReceipt(token, body)
            Toast.makeText(context, "영수증이 업로드되었습니다.", Toast.LENGTH_SHORT).show()
            onSuccess()
        } catch (e: Exception) {
            Toast.makeText(context, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainDashboard(
    sampleData: List<MonthlyBalance>,
    selectedMonth: MonthlyBalance,
    budgetInfo: BudgetStatistics?,
    onMonthSelected: (MonthlyBalance) -> Unit,
    onUsageClick: () -> Unit,
    onBudgetClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    val formatter = DecimalFormat("#,###")
    
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
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onUsageClick() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = "사용 내역", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                val budgetText = if (budgetInfo != null) "${formatter.format(budgetInfo.totalRemaining)}원" else "예산을 입력해주세요"
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onBudgetClick() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = "잔여 예산: $budgetText", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 맨 아래 "카테고리 커스텀" 버튼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onCategoryClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9AA9C2)),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("카테고리 커스텀", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UsageDetailScreen(
    api: BlackLettersApi,
    token: String,
    selectedMonth: MonthlyBalance,
    onBack: () -> Unit,
    onUploadClick: () -> Unit
) {
    var usageItems by remember { mutableStateOf<List<UsageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedMonth) {
        isLoading = true
        try {
            val receipts = api.getReceipts(token)
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
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(text = "${selectedMonth.month} 사용 내역", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(usageItems) { item -> UsageItemRow(item) }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onUploadClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
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
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = item.date, fontSize = 12.sp, color = Color.Gray)
                Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(text = "-${formatter.format(item.amount)}원", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun CategoryCustomScreen(
    api: BlackLettersApi,
    token: String,
    onBack: () -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val loadCategories = suspend {
        isLoading = true
        try {
            categories = api.getCategories(token).filter { it.active != false }
        } catch (e: Exception) {
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadCategories() }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(text = "카테고리 편집", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { category ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = category.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        api.deleteCategory(token, category.categoryId)
                                        loadCategories()
                                    } catch (e: Exception) {}
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("카테고리 추가") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        scope.launch {
                            try {
                                api.createCategory(token, CreateCategoryRequest(newCategoryName))
                                newCategoryName = ""
                                showAddDialog = false
                                loadCategories()
                            } catch (e: Exception) {}
                        }
                    }
                }) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
fun BudgetEntryScreen(
    api: BlackLettersApi,
    token: String,
    selectedMonth: MonthlyBalance,
    onBack: () -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var budgets by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var totalBudget by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var editingCategoryId by remember { mutableStateOf<Long?>(null) } // -1 for total budget
    var editingValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            categories = api.getCategories(token).filter { it.active != false }
            val existingBudgets = api.getBudgets(token, selectedMonth.rawYearMonth)
            val budgetMap = existingBudgets.associate { it.category.categoryId to it.amount }
            budgets = budgetMap
            totalBudget = budgetMap.values.sum()
        } catch (e: Exception) {
        } finally {
            isLoading = false
        }
    }

    if (editingCategoryId != null) {
        CustomKeypadScreen(
            initialValue = editingValue,
            onValueEntered = { newValue ->
                val amount = newValue.toLongOrNull() ?: 0L
                if (editingCategoryId == -1L) {
                    totalBudget = amount
                } else {
                    val updatedBudgets = budgets.toMutableMap()
                    updatedBudgets[editingCategoryId!!] = amount
                    budgets = updatedBudgets
                }
                editingCategoryId = null
            },
            onCancel = { editingCategoryId = null }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(text = "${selectedMonth.month} 예산 설정", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val formatter = DecimalFormat("#,###")
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(text = "총 예산", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { 
                                editingCategoryId = -1L
                                editingValue = totalBudget.toString()
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                        ) {
                            Text(
                                text = "${formatter.format(totalBudget)}원",
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                textAlign = TextAlign.End,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                    items(categories) { category ->
                        val amount = budgets[category.categoryId] ?: 0L
                        Column {
                            Text(text = category.name, fontSize = 14.sp, color = Color.Gray)
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    editingCategoryId = category.categoryId
                                    editingValue = amount.toString()
                                },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))
                            ) {
                                Text(
                                    text = "${formatter.format(amount)}원",
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        // 각 카테고리별 예산 저장 (API 명세에 맞춰 반복 호출)
                                        budgets.forEach { (catId, amt) ->
                                            api.setBudget(token, SetBudgetRequest(catId, selectedMonth.rawYearMonth, amt))
                                        }
                                        onBack()
                                    } catch (e: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text("설정 완료")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomKeypadScreen(
    initialValue: String,
    onValueEntered: (String) -> Unit,
    onCancel: () -> Unit
) {
    var currentValue by remember { mutableStateOf(if (initialValue == "0") "" else initialValue) }
    val formatter = DecimalFormat("#,###")

    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
            TextButton(onClick = { onValueEntered(if (currentValue.isEmpty()) "0" else currentValue) }) {
                Text("입력", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
        
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (currentValue.isEmpty()) "0원" else "${formatter.format(currentValue.toLong())}원",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { currentValue = ((currentValue.toLongOrNull() ?: 0L) + 1000).toString() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                ) { Text("+1,000", color = Color.Black) }
                Button(
                    onClick = { currentValue = ((currentValue.toLongOrNull() ?: 0L) + 10000).toString() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                ) { Text("+10,000", color = Color.Black) }
            }

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "Del")
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(keys) { key ->
                    Button(
                        onClick = {
                            when (key) {
                                "Del" -> if (currentValue.isNotEmpty()) currentValue = currentValue.dropLast(1)
                                else -> currentValue += key
                            }
                        },
                        modifier = Modifier.fillMaxSize().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0))
                    ) {
                        if (key == "Del") Text(text = "⌫", color = Color.Black, fontSize = 20.sp)
                        else Text(text = key, color = Color.Black, fontSize = 20.sp)
                    }
                }
            }
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
                    isSelected = item.rawYearMonth == selectedMonth.rawYearMonth,
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
            // 상단: 흑자
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
            
            // 하단: 적자
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
