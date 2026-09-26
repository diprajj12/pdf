package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.PageItem
import com.example.pdf.PdfProcessor
import com.example.ui.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SplitMode(val title: String, val subtitle: String) {
    EXTRACT("Extract Pages", "Pick pages into a single PDF"),
    SPLIT_MULTIPLE("Split into Files", "Separate into multiple smaller PDFs")
}

enum class MultiSplitStrategy(val title: String) {
    EVERY_PAGE("Single Pages"),
    BY_CHUNK("Fixed Chunks"),
    BY_RANGES("Custom Ranges")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onOpenViewer: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    BackHandler {
        onBack()
    }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var pageItems by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var isLoadingPages by remember { mutableStateOf(false) }

    var currentSplitMode by remember { mutableStateOf(SplitMode.EXTRACT) }
    var multiSplitStrategy by remember { mutableStateOf(MultiSplitStrategy.EVERY_PAGE) }

    // Mode 1: Extract options
    var extractOutputName by remember {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        mutableStateOf("Extracted_$dateStr.pdf")
    }
    var pageRangeInput by remember { mutableStateOf("") }

    // Mode 2: Multiple split options
    var chunkSize by remember { mutableIntStateOf(2) }
    var customRangesInput by remember { mutableStateOf("1-2, 3-4") }
    var multiBaseName by remember { mutableStateOf("Part") }

    // Helper to load pages
    fun loadFilePages(file: File) {
        selectedFile = file
        isLoadingPages = true
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val count = PdfProcessor.getPageCount(file)
                val list = mutableListOf<PageItem>()
                for (i in 0 until count) {
                    val thumb = PdfProcessor.renderPageThumbnail(file, i, 220)
                    list.add(PageItem(pageIndex = i, thumbnail = thumb, rotation = 0, isSelected = true))
                }
                withContext(Dispatchers.Main) {
                    pageItems = list
                    isLoadingPages = false
                    chunkSize = 2.coerceAtMost(list.size.coerceAtLeast(1))
                    customRangesInput = if (list.size >= 4) "1-2, 3-${list.size}" else "1-1, 2-${list.size}"
                }
            }
        }
    }

    // PDF file picker
    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val temp = PdfProcessor.copyUriToTempFile(context, it, "split_source_")
                loadFilePages(temp)
            }
        }
    }

    // Add demo sample PDF
    fun addDemoPdf() {
        viewModel.createSampleDocument { sampleFile ->
            loadFilePages(sampleFile)
        }
    }

    // Helper to parse page range string like "1-3, 5, 7" into 0-indexed integer list
    fun parsePageRanges(input: String, maxPages: Int): List<Int> {
        val result = mutableSetOf<Int>()
        val parts = input.split(",").map { it.trim() }
        for (part in parts) {
            if (part.contains("-")) {
                val rangeParts = part.split("-").map { it.trim().toIntOrNull() }
                if (rangeParts.size == 2 && rangeParts[0] != null && rangeParts[1] != null) {
                    val start = rangeParts[0]!!.coerceIn(1, maxPages)
                    val end = rangeParts[1]!!.coerceIn(1, maxPages)
                    val (low, high) = if (start <= end) Pair(start, end) else Pair(end, start)
                    for (p in low..high) {
                        result.add(p - 1)
                    }
                }
            } else {
                part.toIntOrNull()?.let { p ->
                    if (p in 1..maxPages) {
                        result.add(p - 1)
                    }
                }
            }
        }
        return result.sorted()
    }

    // Helper to parse multi-range string like "1-2, 3-5" into List<List<Int>>
    fun parseMultiRanges(input: String, maxPages: Int): List<List<Int>> {
        val groups = mutableListOf<List<Int>>()
        val parts = input.split(",").map { it.trim() }.filter { it.isNotBlank() }
        for (part in parts) {
            val groupPages = mutableListOf<Int>()
            if (part.contains("-")) {
                val rangeParts = part.split("-").map { it.trim().toIntOrNull() }
                if (rangeParts.size == 2 && rangeParts[0] != null && rangeParts[1] != null) {
                    val start = rangeParts[0]!!.coerceIn(1, maxPages)
                    val end = rangeParts[1]!!.coerceIn(1, maxPages)
                    val (low, high) = if (start <= end) Pair(start, end) else Pair(end, start)
                    for (p in low..high) {
                        groupPages.add(p - 1)
                    }
                }
            } else {
                part.toIntOrNull()?.let { p ->
                    if (p in 1..maxPages) {
                        groupPages.add(p - 1)
                    }
                }
            }
            if (groupPages.isNotEmpty()) {
                groups.add(groupPages.sorted())
            }
        }
        return groups
    }

    val selectedCount = pageItems.count { it.isSelected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Split & Extract PDF",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Extract pages or divide into smaller files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_split_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedFile != null) {
                        IconButton(
                            onClick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.testTag("btn_split_change_file")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Choose another PDF"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (selectedFile != null && pageItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        when (currentSplitMode) {
                            SplitMode.EXTRACT -> {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        val indices = pageItems.filter { it.isSelected }.map { it.pageIndex }
                                        viewModel.splitPdf(selectedFile!!, indices, extractOutputName)
                                    },
                                    enabled = selectedCount > 0,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE5322D),
                                        disabledContainerColor = Color(0xFFE5322D).copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("btn_execute_extract")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterFrames,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedCount > 0) {
                                            "Extract $selectedCount Selected ${if (selectedCount == 1) "Page" else "Pages"} to PDF"
                                        } else {
                                            "Select at least 1 page to extract"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            SplitMode.SPLIT_MULTIPLE -> {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        when (multiSplitStrategy) {
                                            MultiSplitStrategy.EVERY_PAGE -> {
                                                viewModel.splitAllPages(selectedFile!!, multiBaseName)
                                            }
                                            MultiSplitStrategy.BY_CHUNK -> {
                                                viewModel.splitPdfByChunk(selectedFile!!, chunkSize, multiBaseName)
                                            }
                                            MultiSplitStrategy.BY_RANGES -> {
                                                val ranges = parseMultiRanges(customRangesInput, pageItems.size)
                                                viewModel.splitPdfByRanges(selectedFile!!, ranges, multiBaseName)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("btn_execute_split_multiple")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val buttonText = when (multiSplitStrategy) {
                                        MultiSplitStrategy.EVERY_PAGE -> "Split into ${pageItems.size} Separate PDFs"
                                        MultiSplitStrategy.BY_CHUNK -> {
                                            val filesCount = (pageItems.size + chunkSize - 1) / chunkSize
                                            "Split into $filesCount Smaller PDFs ($chunkSize pgs each)"
                                        }
                                        MultiSplitStrategy.BY_RANGES -> {
                                            val count = parseMultiRanges(customRangesInput, pageItems.size).size
                                            "Split into $count Smaller PDFs"
                                        }
                                    }
                                    Text(
                                        text = buttonText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Hero Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFE5322D), Color(0xFF991B1B))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Split Suite",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "100% Offline & Instant",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Extract specific pages or break a PDF into smaller files.",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Select pages visually, extract custom ranges, or separate all pages.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // File selection action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_select_split_pdf")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedFile == null) "Select PDF File" else "Change PDF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { addDemoPdf() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE5322D)),
                        border = BorderStroke(1.5.dp, Color(0xFFE5322D)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_split_add_demo")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Demo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Empty state when no file loaded
            if (selectedFile == null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFF1F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = null,
                                    tint = Color(0xFFE5322D),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No PDF file selected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Select any PDF to extract chosen pages or break it down into smaller files. You can also tap 'Add Demo' to test right away.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Active document info card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFF1F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFFE5322D),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFile!!.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${pageItems.size} Pages • ${PdfProcessor.formatFileSize(selectedFile!!.length())}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            OutlinedButton(
                                onClick = { onOpenViewer(selectedFile!!) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).testTag("btn_preview_source_pdf")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Mode Tabs: Mode 1 (Extract Pages) vs Mode 2 (Split into Multiple Smaller Files)
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SplitMode.entries.forEach { mode ->
                                val isSelected = currentSplitMode == mode
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { currentSplitMode = mode }
                                        .testTag("tab_split_mode_${mode.name}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = mode.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) Color(0xFFE5322D) else Color(0xFF475569)
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color(0xFF64748B) else Color(0xFF94A3B8),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isLoadingPages) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFE5322D))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Rendering page previews...", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // MODE 1: EXTRACT SPECIFIC PAGES CONTENT
                    if (currentSplitMode == SplitMode.EXTRACT) {
                        // Quick selection actions
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Selected: $selectedCount of ${pageItems.size} pages",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    pageItems = pageItems.map { it.copy(isSelected = true) }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp).testTag("btn_select_all_pages")
                                            ) {
                                                Text("All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5322D))
                                            }
                                            TextButton(
                                                onClick = {
                                                    pageItems = pageItems.map { it.copy(isSelected = false) }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp).testTag("btn_deselect_all_pages")
                                            ) {
                                                Text("None", fontSize = 11.sp, color = Color.Gray)
                                            }
                                            TextButton(
                                                onClick = {
                                                    pageItems = pageItems.mapIndexed { idx, item ->
                                                        item.copy(isSelected = (idx + 1) % 2 != 0)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp).testTag("btn_select_odd_pages")
                                            ) {
                                                Text("Odd", fontSize = 11.sp, color = Color(0xFF475569))
                                            }
                                            TextButton(
                                                onClick = {
                                                    pageItems = pageItems.mapIndexed { idx, item ->
                                                        item.copy(isSelected = (idx + 1) % 2 == 0)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp).testTag("btn_select_even_pages")
                                            ) {
                                                Text("Even", fontSize = 11.sp, color = Color(0xFF475569))
                                            }
                                        }
                                    }

                                    // Direct page range input filter
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = pageRangeInput,
                                            onValueChange = { pageRangeInput = it },
                                            placeholder = { Text("e.g. 1-2, 4, 6", fontSize = 12.sp) },
                                            label = { Text("Page Range", fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("input_page_ranges")
                                        )
                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                val parsed = parsePageRanges(pageRangeInput, pageItems.size)
                                                if (parsed.isNotEmpty()) {
                                                    pageItems = pageItems.mapIndexed { idx, item ->
                                                        item.copy(isSelected = parsed.contains(idx))
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                            modifier = Modifier.height(48.dp).testTag("btn_apply_page_range")
                                        ) {
                                            Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Page thumbnails grid
                        item {
                            Text(
                                text = "TAP PAGES TO SELECT / DESELECT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // Grid inside LazyColumn using manual chunking or fixed height Box
                        item {
                            val columns = 3
                            val rows = (pageItems.size + columns - 1) / columns
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (r in 0 until rows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (c in 0 until columns) {
                                            val index = r * columns + c
                                            if (index < pageItems.size) {
                                                val item = pageItems[index]
                                                Box(modifier = Modifier.weight(1f)) {
                                                    PageThumbnailCard(
                                                        pageItem = item,
                                                        onToggle = {
                                                            pageItems = pageItems.mapIndexed { idx, p ->
                                                                if (idx == index) p.copy(isSelected = !p.isSelected) else p
                                                            }
                                                        }
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Output filename field for extracted PDF
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Output Document Name",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = extractOutputName,
                                        onValueChange = { extractOutputName = it },
                                        label = { Text("Extracted PDF File Name") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = Color(0xFFE5322D)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("input_extract_filename")
                                    )
                                }
                            }
                        }
                    }

                    // MODE 2: SPLIT INTO MULTIPLE SMALLER PDF FILES CONTENT
                    if (currentSplitMode == SplitMode.SPLIT_MULTIPLE) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Split Strategy",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Strategy Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MultiSplitStrategy.entries.forEach { strat ->
                                            val isSelected = multiSplitStrategy == strat
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { multiSplitStrategy = strat },
                                                label = { Text(strat.title, fontSize = 11.sp) },
                                                modifier = Modifier.weight(1f).testTag("chip_strat_${strat.name}")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    when (multiSplitStrategy) {
                                        MultiSplitStrategy.EVERY_PAGE -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF8FAFC),
                                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        text = "• Generates ${pageItems.size} separate PDF files.",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF1E293B)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Each page will become an individual document: ${multiBaseName}_1.pdf, ${multiBaseName}_2.pdf ... ${multiBaseName}_${pageItems.size}.pdf",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }
                                        }
                                        MultiSplitStrategy.BY_CHUNK -> {
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Pages per file: $chunkSize",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF1E293B)
                                                    )
                                                    val fileCount = (pageItems.size + chunkSize - 1) / chunkSize
                                                    Text(
                                                        text = "Will create $fileCount files",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFE5322D)
                                                    )
                                                }
                                                Slider(
                                                    value = chunkSize.toFloat(),
                                                    onValueChange = { chunkSize = it.toInt() },
                                                    valueRange = 1f..(pageItems.size - 1).coerceAtLeast(1).toFloat(),
                                                    steps = (pageItems.size - 2).coerceAtLeast(0),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(0xFFE5322D),
                                                        activeTrackColor = Color(0xFFE5322D)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth().testTag("slider_chunk_size")
                                                )

                                                // Preview generated chunks
                                                val fileCount = (pageItems.size + chunkSize - 1) / chunkSize
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    for (f in 0 until fileCount.coerceAtMost(5)) {
                                                        val start = f * chunkSize + 1
                                                        val end = ((f + 1) * chunkSize).coerceAtMost(pageItems.size)
                                                        Text(
                                                            text = "📄 ${multiBaseName}_${f + 1}: Pages $start to $end",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF334155)
                                                        )
                                                    }
                                                    if (fileCount > 5) {
                                                        Text(
                                                            text = "...and ${fileCount - 5} more files",
                                                            fontSize = 10.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        MultiSplitStrategy.BY_RANGES -> {
                                            Column {
                                                Text(
                                                    text = "Enter Page Ranges (comma-separated):",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF475569)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                OutlinedTextField(
                                                    value = customRangesInput,
                                                    onValueChange = { customRangesInput = it },
                                                    placeholder = { Text("e.g. 1-2, 3-5", fontSize = 12.sp) },
                                                    label = { Text("Ranges") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth().testTag("input_multi_ranges")
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                val parsedRanges = parseMultiRanges(customRangesInput, pageItems.size)
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Files to generate (${parsedRanges.size}):",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF1E293B)
                                                    )
                                                    parsedRanges.forEachIndexed { idx, range ->
                                                        val start = (range.firstOrNull() ?: 0) + 1
                                                        val end = (range.lastOrNull() ?: 0) + 1
                                                        Text(
                                                            text = "📄 ${multiBaseName}_${idx + 1}: Pages $start - $end (${range.size} pgs)",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF334155)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Base name prefix
                                    OutlinedTextField(
                                        value = multiBaseName,
                                        onValueChange = { multiBaseName = it },
                                        label = { Text("Output Files Prefix Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_multi_base_name")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageThumbnailCard(
    pageItem: PageItem,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .border(
                width = if (pageItem.isSelected) 2.5.dp else 1.dp,
                color = if (pageItem.isSelected) Color(0xFFE5322D) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(8.dp)
            )
            .testTag("page_card_${pageItem.pageIndex}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (pageItem.isSelected) 3.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (pageItem.thumbnail != null) {
                Image(
                    bitmap = pageItem.thumbnail.asImageBitmap(),
                    contentDescription = "Page ${pageItem.pageIndex + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Pg ${pageItem.pageIndex + 1}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Page label top left
            Surface(
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                color = if (pageItem.isSelected) Color(0xFFE5322D) else Color.DarkGray.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "${pageItem.pageIndex + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Checkmark top right
            if (pageItem.isSelected) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE5322D),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp)
                    )
                }
            }
        }
    }
}
