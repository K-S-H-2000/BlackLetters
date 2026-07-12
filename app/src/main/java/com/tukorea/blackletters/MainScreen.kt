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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.window.Dialog
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
    val id: Long,
    val date: String,
    val title: String,
    val amount: Long
)

fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))
}

fun uploadReceipt(context: Context, api: BlackLettersApi, token: String, scope: kotlinx.coroutines.CoroutineScope, uri: Uri, onSuccess: () -> Unit) {
    scope.launch {
        try {
            val file = File(context.cacheDir, "temp_upload.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            api.uploadReceipt(token, body)
            api.runBatchQuery(token)
            
            val calendar = Calendar.getInstance()
            val yearMonth = String.format(Locale.US, "%d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
            try {
                val stats = api.getBudgetStatistics(token, yearMonth)
                stats.categories.forEach { cat ->
                    if (cat.usageRate >= 100.0) Toast.makeText(context, "\"${cat.categoryName}\" 예산 초과", Toast.LENGTH_LONG).show()
                    else if (cat.usageRate >= 80.0) Toast.makeText(context, "\"${cat.categoryName}\" 예산 80% 도달", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { e.printStackTrace() }
            Toast.makeText(context, "영수증이 업로드되었습니다.", Toast.LENGTH_SHORT).show()
            onSuccess()
        } catch (e: Exception) {
            Toast.makeText(context, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainScreen(token: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { BlackLettersApi.create() }
    val bearerToken = "Bearer $token"

    var sampleData by remember { mutableStateOf<List<MonthlyBalance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalBudgetInfo by remember { mutableStateOf<BudgetStatistics?>(null) }
    var actualMonthlySpent by remember { mutableStateOf(0L) }
    var selectedMonth by remember { mutableStateOf<MonthlyBalance?>(null) }

    val loadDashboardData = suspend {
        isLoading = true
        try {
            // 모든 영수증을 한 번에 가져와 프론트에서 정확한 합산 수행
            val allReceipts = api.getReceipts(bearerToken)
            val data = mutableListOf<MonthlyBalance>()
            val calendar = Calendar.getInstance()
            
            for (i in 0 until 12) {
                val y = calendar.get(Calendar.YEAR)
                val m = calendar.get(Calendar.MONTH) + 1
                val yearMonth = String.format(Locale.US, "%d-%02d", y, m)
                
                // 해당 월의 영수증 합계 계산
                val monthlySum = allReceipts.filter { it.transactionDate?.contains(yearMonth) == true }.sumOf { it.totalAmount }
                
                try {
                    val stats = api.getBudgetStatistics(bearerToken, yearMonth)
                    data.add(0, MonthlyBalance("${m}월", stats.totalBudget - monthlySum, "${y}년", yearMonth))
                } catch (e: Exception) {
                    data.add(0, MonthlyBalance("${m}월", -monthlySum, "${y}년", yearMonth))
                }
                calendar.add(Calendar.MONTH, -1)
            }
            sampleData = data
            
            val current = selectedMonth ?: data.lastOrNull()
            if (current != null) {
                val year = current.year.replace("년", "")
                val month = current.month.replace("월", "").padStart(2, '0')
                actualMonthlySpent = allReceipts.filter { it.transactionDate?.contains("$year-$month") == true }.sumOf { it.totalAmount }
                try { totalBudgetInfo = api.getBudgetStatistics(bearerToken, current.rawYearMonth) } catch (e: Exception) { totalBudgetInfo = null }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadDashboardData() }

    LaunchedEffect(sampleData) {
        if (sampleData.isNotEmpty() && selectedMonth == null) selectedMonth = sampleData.last()
    }

    LaunchedEffect(selectedMonth) {
        selectedMonth?.let { month ->
            isLoading = true
            try {
                val receipts = api.getReceipts(bearerToken)
                val ym = month.rawYearMonth
                actualMonthlySpent = receipts.filter { it.transactionDate?.contains(ym) == true }.sumOf { it.totalAmount }
                totalBudgetInfo = api.getBudgetStatistics(bearerToken, ym)
            } catch (e: Exception) { totalBudgetInfo = null }
            finally { isLoading = false }
        }
    }

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("dashboard") }
    var selectedReceiptId by remember { mutableStateOf<Long?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) showImageSourceDialog = true
        else Toast.makeText(context, "카메라 권한 필요", Toast.LENGTH_SHORT).show()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempPhotoUri?.let { uri -> uploadReceipt(context, api, bearerToken, scope, uri) { scope.launch { loadDashboardData() } } }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadReceipt(context, api, bearerToken, scope, it) { scope.launch { loadDashboardData() } } }
    }

    BackHandler(enabled = currentScreen != "dashboard") {
        if (currentScreen == "receipt_detail") currentScreen = "usage" else currentScreen = "dashboard"
    }

    val currentSelectedMonth = selectedMonth ?: MonthlyBalance("", 0, "", "")

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            "usage" -> UsageDetailScreen(api, bearerToken, currentSelectedMonth, { currentScreen = "dashboard" }, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showImageSourceDialog = true else permissionLauncher.launch(Manifest.permission.CAMERA) }, { id -> selectedReceiptId = id; currentScreen = "receipt_detail" })
            "receipt_detail" -> ReceiptDetailScreen(api, bearerToken, selectedReceiptId!!, { currentScreen = "usage" }, { currentScreen = "usage"; scope.launch { loadDashboardData() } }, { scope.launch { loadDashboardData() } })
            "category" -> CategoryCustomScreen(api, bearerToken) { currentScreen = "dashboard" }
            "budget" -> BudgetEntryScreen(api, bearerToken, currentSelectedMonth, totalBudgetInfo) { currentScreen = "dashboard"; scope.launch { loadDashboardData() } }
            else -> MainDashboard(sampleData, currentSelectedMonth, totalBudgetInfo, actualMonthlySpent, { selectedMonth = it }, { currentScreen = "usage" }, { currentScreen = "budget" }, { currentScreen = "category" })
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(onDismissRequest = { showImageSourceDialog = false }, title = { Text("영수증 업로드") }, text = { Text("사진을 가져올 방법을 선택하세요.") },
            confirmButton = { TextButton(onClick = { showImageSourceDialog = false; val photoFile = createImageFile(context); val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile); tempPhotoUri = photoUri; cameraLauncher.launch(photoUri) }) { Text("카메라") } },
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false; galleryLauncher.launch("image/*") }) { Text("앨범") } }
        )
    }
}

@Composable
fun MainDashboard(sampleData: List<MonthlyBalance>, selectedMonth: MonthlyBalance, budgetInfo: BudgetStatistics?, actualSpent: Long, onMonthSelected: (MonthlyBalance) -> Unit, onUsageClick: () -> Unit, onBudgetClick: () -> Unit, onCategoryClick: () -> Unit) {
    val formatter = DecimalFormat("#,###")
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(3f).background(Color(0xFFFAFAFA))) { BalanceBarChart(data = sampleData, selectedMonth = selectedMonth, onMonthSelected = onMonthSelected) }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(5f).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                val budgetTotal = budgetInfo?.totalBudget ?: 0L
                val calculatedAmount = budgetTotal - actualSpent
                Text(text = if (calculatedAmount >= 0) "${formatter.format(calculatedAmount)}원 흑자" else "${formatter.format(Math.abs(calculatedAmount))}원 적자", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (calculatedAmount >= 0) Color.Black else Color.Red)
            }
            item { Text(text = "${selectedMonth.year} ${selectedMonth.month} 상세 내역", color = Color.Gray, fontSize = 16.sp) }
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable { onUsageClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "사용 내역", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(text = "${formatter.format(actualSpent)}원", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                }
            }
            item {
                val budgetTotal = budgetInfo?.totalBudget ?: 0L
                val budgetRemaining = budgetTotal - actualSpent
                val budgetText = if (budgetInfo != null) "${formatter.format(budgetRemaining)}원" else "예산을 입력해주세요"
                Card(modifier = Modifier.fillMaxWidth().clickable { onBudgetClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Text(text = "잔여 예산: $budgetText", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Button(onClick = onCategoryClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9AA9C2)), shape = MaterialTheme.shapes.medium) {
                Text("카테고리 커스텀", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UsageDetailScreen(api: BlackLettersApi, token: String, selectedMonth: MonthlyBalance, onBack: () -> Unit, onUploadClick: () -> Unit, onReceiptClick: (Long) -> Unit) {
    var usageItems by remember { mutableStateOf<List<UsageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(selectedMonth) {
        isLoading = true
        try {
            val receipts = api.getReceipts(token)
            val year = selectedMonth.year.replace("년", "")
            val month = selectedMonth.month.replace("월", "").padStart(2, '0')
            usageItems = receipts.filter { it.transactionDate?.contains("$year-$month") == true }.map {
                UsageItem(id = it.receiptId, date = it.transactionDate?.substring(5, 10) ?: "", title = it.merchantName ?: "알 수 없음", amount = it.totalAmount)
            }
        } catch (e: Exception) {} finally { isLoading = false }
    }
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(text = "${selectedMonth.month} 사용 내역", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(usageItems) { item -> Box(modifier = Modifier.clickable { onReceiptClick(item.id) }) { UsageItemRow(item) } }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onUploadClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Default.Add, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("영수증 업로드", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UsageItemRow(item: UsageItem) {
    val formatter = DecimalFormat("#,###")
    val displayTitle = if (item.title.length > 8) item.title.take(8) + "..." else item.title
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)), shape = MaterialTheme.shapes.small) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.date, fontSize = 12.sp, color = Color.Gray)
                Text(text = displayTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(text = "-${formatter.format(item.amount)}원", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun ReceiptDetailScreen(api: BlackLettersApi, token: String, receiptId: Long, onBack: () -> Unit, onDelete: () -> Unit, onUpdate: () -> Unit) {
    var detail by remember { mutableStateOf<ReceiptDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val formatter = DecimalFormat("#,###")
    LaunchedEffect(receiptId) {
        isLoading = true
        try { detail = api.getReceiptDetail(token, receiptId) } catch (e: Exception) {} finally { isLoading = false }
    }
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }; Text(text = "영수증 상세", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Row {
                IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = { scope.launch { try { api.deleteReceipt(token, receiptId); api.runBatchQuery(token); onDelete() } catch (e: Exception) {} } }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
        if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (detail != null) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = detail!!.merchantName ?: "알 수 없음", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        detail!!.categoryName?.let { name ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = Color(0xFF9AA9C2).copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) { Text(text = name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp, color = Color(0xFF4A5568)) }
                        }
                    }
                    Text(text = detail!!.transactionDate?.replace("T", " ") ?: "", color = Color.Gray)
                }
                item { HorizontalDivider(); Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(text = "총 합계", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(text = "${formatter.format(detail!!.totalAmount)}원", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)) }; HorizontalDivider() }
                detail!!.items?.let { items -> items(items) { item -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(text = item.itemName, fontSize = 16.sp); Text(text = "${formatter.format(item.unitPrice ?: 0)}원 x ${item.quantity}", fontSize = 12.sp, color = Color.Gray) }; Text(text = "${formatter.format(item.amount)}원", fontSize = 16.sp, fontWeight = FontWeight.Medium) } } }
            }
        }
    }
    if (showEditDialog && detail != null) {
        ReceiptEditDialog(api, token, detail!!, { showEditDialog = false }, { showEditDialog = false; scope.launch { isLoading = true; api.runBatchQuery(token); detail = api.getReceiptDetail(token, receiptId); isLoading = false; onUpdate() } })
    }
}

@Composable
fun ReceiptEditDialog(api: BlackLettersApi, token: String, initialDetail: ReceiptDetail, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var merchantName by remember { mutableStateOf(initialDetail.merchantName ?: "") }
    var transactionDate by remember { mutableStateOf(initialDetail.transactionDate ?: "") }
    var selectedCategoryId by remember { mutableStateOf(initialDetail.categoryId) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    val items = remember { mutableStateListOf<UpdateReceiptItem>().apply { initialDetail.items?.forEach { add(UpdateReceiptItem(it.itemName, it.unitPrice, it.quantity, it.amount)) } } }
    val totalAmount = items.sumOf { it.amount }
    LaunchedEffect(Unit) { try { categories = api.getCategories(token).filter { it.active != false } } catch (e: Exception) {} }
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f), shape = MaterialTheme.shapes.large, color = Color.White) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "영수증 수정", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { OutlinedTextField(value = merchantName, onValueChange = { merchantName = it }, label = { Text("상호명") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = transactionDate, onValueChange = { transactionDate = it }, label = { Text("일시 (YYYY-MM-DDTHH:mm:ss)") }, modifier = Modifier.fillMaxWidth()) }
                    item { Text(text = "카테고리", fontSize = 14.sp, fontWeight = FontWeight.Medium); LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(categories) { category -> FilterChip(selected = selectedCategoryId == category.categoryId, onClick = { selectedCategoryId = category.categoryId }, label = { Text(category.name) }) } } }
                    item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(text = "품목 내역", fontSize = 14.sp, fontWeight = FontWeight.Medium); IconButton(onClick = { items.add(UpdateReceiptItem("", 0, 1, 0)) }) { Icon(Icons.Default.AddCircle, contentDescription = "Add Item") } } }
                    itemsIndexed(items) { index, item ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = item.itemName, onValueChange = { items[index] = item.copy(itemName = it) }, label = { Text("품목명") }, modifier = Modifier.weight(1f)); IconButton(onClick = { items.removeAt(index) }) { Text("-", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold) } }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = item.unitPrice?.toString() ?: "", onValueChange = { val p = it.toLongOrNull() ?: 0L; items[index] = item.copy(unitPrice = p, amount = p * item.quantity) }, label = { Text("단가") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                    OutlinedTextField(value = item.quantity.toString(), onValueChange = { val q = it.toIntOrNull() ?: 1; items[index] = item.copy(quantity = q, amount = (item.unitPrice ?: 0L) * q) }, label = { Text("수량") }, modifier = Modifier.weight(0.5f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                }
                                Text(text = "소계: ${DecimalFormat("#,###").format(item.amount)}원", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(text = "총 합계: ${DecimalFormat("#,###").format(totalAmount)}원", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("취소") }; Button(onClick = { scope.launch { try { api.updateReceipt(token, initialDetail.receiptId, UpdateReceiptRequest(merchantName, totalAmount, transactionDate, selectedCategoryId, items.toList())); api.runBatchQuery(token); onSave() } catch (e: Exception) { Toast.makeText(context, "수정 실패", Toast.LENGTH_SHORT).show() } } }) { Text("저장") } }
            }
        }
    }
}

@Composable
fun CategoryCustomScreen(api: BlackLettersApi, token: String, onBack: () -> Unit) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val loadCategories = suspend { isLoading = true; try { categories = api.getCategories(token).filter { it.active != false } } catch (e: Exception) {} finally { isLoading = false } }
    LaunchedEffect(Unit) { loadCategories() }
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }; Text(text = "카테고리 편집", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            IconButton(onClick = { categoryNameInput = ""; showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
        if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(categories) { category ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(text = category.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                if (category.user == null) { Spacer(modifier = Modifier.width(8.dp)); Surface(color = Color.LightGray.copy(alpha = 0.5f), shape = MaterialTheme.shapes.extraSmall) { Text(text = "기본", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp, color = Color.Gray) } }
                            }
                            if (category.user != null) {
                                Row {
                                    IconButton(onClick = { categoryNameInput = category.name; editingCategory = category }) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray) }
                                    IconButton(onClick = { scope.launch { try { api.deleteCategory(token, category.categoryId); loadCategories() } catch (e: Exception) {} } }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAddDialog || editingCategory != null) {
        AlertDialog(onDismissRequest = { showAddDialog = false; editingCategory = null }, title = { Text(if (showAddDialog) "카테고리 추가" else "카테고리 수정") }, text = { OutlinedTextField(value = categoryNameInput, onValueChange = { categoryNameInput = it }, label = { Text("이름") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (categoryNameInput.isNotBlank()) { scope.launch { try { if (showAddDialog) api.createCategory(token, CreateCategoryRequest(categoryNameInput)) else api.updateCategory(token, editingCategory!!.categoryId, UpdateCategoryRequest(categoryNameInput)); categoryNameInput = ""; showAddDialog = false; editingCategory = null; loadCategories() } catch (e: Exception) {} } } }) { Text(if (showAddDialog) "추가" else "수정") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingCategory = null }) { Text("취소") } }
        )
    }
}

@Composable
fun BudgetEntryScreen(api: BlackLettersApi, token: String, selectedMonth: MonthlyBalance, budgetInfo: BudgetStatistics?, onBack: () -> Unit) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var budgets by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var categorySpentMap by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var totalBudget by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var editingCategoryId by remember { mutableStateOf<Long?>(null) }
    var editingValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val formatter = DecimalFormat("#,###")
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            categories = api.getCategories(token).filter { it.active != false }
            val existingBudgets = api.getBudgets(token, selectedMonth.rawYearMonth)
            budgets = existingBudgets.associate { it.category.categoryId to it.amount }
            totalBudget = budgets.values.sum()
            
            // 실제 영수증 목록을 가져와 카테고리별 합산 수행 (프론트엔드 계산 도입)
            val receipts = api.getReceipts(token)
            val ym = selectedMonth.rawYearMonth
            categorySpentMap = receipts.filter { it.transactionDate?.contains(ym) == true }.groupBy { it.categoryId ?: 0L }.mapValues { it.value.sumOf { r -> r.totalAmount } }
        } catch (e: Exception) {} finally { isLoading = false }
    }
    if (editingCategoryId != null) {
        CustomKeypadScreen(editingValue, { newValue -> val amount = newValue.toLongOrNull() ?: 0L; if (editingCategoryId == -1L) totalBudget = amount else { val updated = budgets.toMutableMap(); updated[editingCategoryId!!] = amount; budgets = updated }; editingCategoryId = null }, { editingCategoryId = null })
    } else {
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }; Text(text = "${selectedMonth.month} 예산 설정", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(text = "총 예산", fontWeight = FontWeight.Bold, fontSize = 16.sp); Card(modifier = Modifier.fillMaxWidth().clickable { editingCategoryId = -1L; editingValue = totalBudget.toString() }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))) { Text(text = "${formatter.format(totalBudget)}원", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = TextAlign.End, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    items(categories) { category ->
                        val amount = budgets[category.categoryId] ?: 0L
                        val spent = categorySpentMap[category.categoryId] ?: 0L
                        val usageRate = if (amount > 0) (spent.toDouble() / amount.toDouble() * 100).toInt() else null
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = category.name, fontSize = 14.sp, color = Color.Gray)
                                if (usageRate != null) Text(text = "사용률: $usageRate%", fontSize = 12.sp, color = if (usageRate >= 80) Color.Red else Color.Gray)
                            }
                            Card(modifier = Modifier.fillMaxWidth().clickable { editingCategoryId = category.categoryId; editingValue = amount.toString() }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8))) { Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) { Text(text = "${formatter.format(amount)}원", textAlign = TextAlign.End, fontSize = 16.sp, modifier = Modifier.fillMaxWidth()); Text(text = "사용액: ${formatter.format(spent)}원", textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Red, modifier = Modifier.fillMaxWidth()) } }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)); Button(onClick = { scope.launch { try { budgets.forEach { (catId, amt) -> api.setBudget(token, SetBudgetRequest(catId, selectedMonth.rawYearMonth, amt)) }; api.runBatchQuery(token); onBack() } catch (e: Exception) {} } }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("설정 완료") } }
                }
            }
        }
    }
}

@Composable
fun CustomKeypadScreen(initialValue: String, onValueEntered: (String) -> Unit, onCancel: () -> Unit) {
    var currentValue by remember { mutableStateOf(if (initialValue == "0") "" else initialValue) }
    val formatter = DecimalFormat("#,###")
    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }; TextButton(onClick = { onValueEntered(if (currentValue.isEmpty()) "0" else currentValue) }) { Text("입력", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) } }
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(text = if (currentValue.isEmpty()) "0원" else "${formatter.format(currentValue.toLong())}원", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { currentValue = ((currentValue.toLongOrNull() ?: 0L) + 1000).toString() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) { Text("+1,000", color = Color.Black) }; Button(onClick = { currentValue = ((currentValue.toLongOrNull() ?: 0L) + 10000).toString() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) { Text("+10,000", color = Color.Black) } }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "Del")
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(300.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(keys) { key -> Button(onClick = { when (key) { "Del" -> if (currentValue.isNotEmpty()) currentValue = currentValue.dropLast(1) else -> currentValue += key } }, modifier = Modifier.fillMaxSize().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0))) { if (key == "Del") Text(text = "⌫", color = Color.Black, fontSize = 20.sp) else Text(text = key, color = Color.Black, fontSize = 20.sp) } }
            }
        }
    }
}

@Composable
fun BalanceBarChart(data: List<MonthlyBalance>, selectedMonth: MonthlyBalance, onMonthSelected: (MonthlyBalance) -> Unit) {
    val scrollState = rememberLazyListState()
    LaunchedEffect(Unit) { if (data.isNotEmpty()) scrollState.scrollToItem(data.size - 1) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) { Spacer(modifier = Modifier.height(30.dp)); Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f)) } }
        LazyRow(state = scrollState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(data) { item -> BarItem(item = item, isSelected = item.rawYearMonth == selectedMonth.rawYearMonth, maxAbsValue = data.maxOfOrNull { Math.abs(it.amount) } ?: 1L, onClick = { onMonthSelected(item) }) } }
    }
}

@Composable
fun BarItem(item: MonthlyBalance, isSelected: Boolean, maxAbsValue: Long, onClick: () -> Unit) {
    val barHeightRatio = Math.abs(item.amount).toFloat() / maxAbsValue
    Column(modifier = Modifier.width(35.dp).fillMaxHeight().clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(modifier = Modifier.height(30.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-2).dp)) { if (isSelected) Text(text = item.year, fontSize = 10.sp, color = Color.Gray, lineHeight = 10.sp) else Spacer(modifier = Modifier.height(10.dp)); Text(text = item.month, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.Black else Color.Gray, lineHeight = 14.sp) }
        Column(modifier = Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { if (item.amount > 0) Box(modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(barHeightRatio).background(if (isSelected) Color.Black else Color.Black.copy(alpha = 0.3f), shape = MaterialTheme.shapes.extraSmall)) }; Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { if (item.amount < 0) Box(modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(barHeightRatio).background(if (isSelected) Color.Red else Color.Red.copy(alpha = 0.3f), shape = MaterialTheme.shapes.extraSmall)) } }
    }
}
