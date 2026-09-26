package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pdf.*
import com.example.ui.PdfViewModel
import com.example.ui.components.SignaturePad
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailScreen(
    tool: ToolType,
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onOpenViewer: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Selected files state
    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var singlePdfFile by remember { mutableStateOf<File?>(null) }
    var pageItems by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var selectedImageFile by remember { mutableStateOf<File?>(null) }
    var selectedSingleImageUri by remember { mutableStateOf<Uri?>(null) }

    // Tool specific configs
    var compressionMode by remember { mutableStateOf(CompressionMode.RECOMMENDED) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkPosition by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.35f) }
    var pageNumberPosition by remember { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var isJpegFormat by remember { mutableStateOf(true) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var selectedSignaturePage by remember { mutableIntStateOf(0) }

    // Enhanced Merge & Image tools configs
    var mergeOutputName by remember { mutableStateOf("") }
    var imageCompressionQuality by remember { mutableFloatStateOf(70f) }
    var imageScalePercent by remember { mutableIntStateOf(100) }
    var imageOutputName by remember { mutableStateOf("") }
    var imageToPdfOrientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var imageToPdfFitMode by remember { mutableStateOf(ImageFitMode.FIT_PAGE) }
    var imageToPdfMargin by remember { mutableFloatStateOf(20f) }
    var imageToPdfCustomName by remember { mutableStateOf("") }

    // Pickers
    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val temp = PdfProcessor.copyUriToTempFile(context, it, "single_")
                singlePdfFile = temp
                loadPagesForFile(temp) { items -> pageItems = items }
            }
        }
    }

    val multiplePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val files = uris.map { uri ->
                    PdfProcessor.copyUriToTempFile(context, uri, "merge_")
                }
                selectedFiles = selectedFiles + files
            }
        }
    }

    val imagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris = selectedImageUris + uris
        }
    }

    val singleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedSingleImageUri = it
            selectedImageFile = null
        }
    }

    // Helper to load sample doc if needed
    fun loadSample() {
        viewModel.createSampleDocument { sampleFile ->
            singlePdfFile = sampleFile
            selectedFiles = listOf(sampleFile)
            loadPagesForFile(sampleFile) { items -> pageItems = items }
        }
    }

    fun addDemoPdfForMerge() {
        viewModel.createSampleDocument { sampleFile ->
            selectedFiles = selectedFiles + sampleFile
        }
    }

    fun loadSampleJpg() {
        viewModel.createSampleImage { sampleJpg ->
            selectedImageFile = sampleJpg
            selectedSingleImageUri = null
        }
    }

    fun addDemoJpgToImagePdf() {
        viewModel.createSampleImage { sampleJpg ->
            val uri = Uri.fromFile(sampleJpg)
            selectedImageUris = selectedImageUris + uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tool.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_tool")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tool description card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = tool.color.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(tool.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // File Selector / Action based on tool
                when (tool) {
                    ToolType.MERGE -> {
                        item {
                            MergeToolContent(
                                selectedFiles = selectedFiles,
                                outputName = mergeOutputName,
                                onOutputNameChange = { mergeOutputName = it },
                                onAddFiles = { multiplePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { addDemoPdfForMerge() },
                                onMoveUp = { idx ->
                                    if (idx > 0) {
                                        val mutable = selectedFiles.toMutableList()
                                        val item = mutable.removeAt(idx)
                                        mutable.add(idx - 1, item)
                                        selectedFiles = mutable
                                    }
                                },
                                onMoveDown = { idx ->
                                    if (idx < selectedFiles.lastIndex) {
                                        val mutable = selectedFiles.toMutableList()
                                        val item = mutable.removeAt(idx)
                                        mutable.add(idx + 1, item)
                                        selectedFiles = mutable
                                    }
                                },
                                onReverseOrder = {
                                    selectedFiles = selectedFiles.reversed()
                                },
                                onClearAll = {
                                    selectedFiles = emptyList()
                                },
                                onRemove = { idx ->
                                    selectedFiles = selectedFiles.filterIndexed { i, _ -> i != idx }
                                },
                                onMerge = {
                                    viewModel.mergePdfs(selectedFiles, mergeOutputName)
                                }
                            )
                        }
                    }

                    ToolType.SPLIT -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null && pageItems.isNotEmpty()) {
                            item {
                                SplitToolContent(
                                    file = singlePdfFile!!,
                                    pageItems = pageItems,
                                    onTogglePage = { index ->
                                        pageItems = pageItems.mapIndexed { idx, item ->
                                            if (idx == index) item.copy(isSelected = !item.isSelected) else item
                                        }
                                    },
                                    onSplitSelected = {
                                        val indices = pageItems.filter { it.isSelected }.map { it.pageIndex }
                                        viewModel.splitPdf(singlePdfFile!!, indices)
                                    },
                                    onSplitAll = {
                                        viewModel.splitAllPages(singlePdfFile!!)
                                    }
                                )
                            }
                        }
                    }

                    ToolType.COMPRESS -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null) {
                            item {
                                CompressToolContent(
                                    selectedMode = compressionMode,
                                    onModeSelected = { compressionMode = it },
                                    onCompress = {
                                        viewModel.compressPdf(singlePdfFile!!, compressionMode)
                                    }
                                )
                            }
                        }
                    }

                    ToolType.IMAGE_TO_PDF -> {
                        item {
                            ImageToPdfContent(
                                imageUris = selectedImageUris,
                                orientation = imageToPdfOrientation,
                                onOrientationChange = { imageToPdfOrientation = it },
                                fitMode = imageToPdfFitMode,
                                onFitModeChange = { imageToPdfFitMode = it },
                                margin = imageToPdfMargin,
                                onMarginChange = { imageToPdfMargin = it },
                                customName = imageToPdfCustomName,
                                onCustomNameChange = { imageToPdfCustomName = it },
                                onPickImages = {
                                    imagesPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onAddDemoJpg = {
                                    addDemoJpgToImagePdf()
                                },
                                onMoveLeft = { idx ->
                                    if (idx > 0) {
                                        val mutable = selectedImageUris.toMutableList()
                                        val item = mutable.removeAt(idx)
                                        mutable.add(idx - 1, item)
                                        selectedImageUris = mutable
                                    }
                                },
                                onMoveRight = { idx ->
                                    if (idx < selectedImageUris.lastIndex) {
                                        val mutable = selectedImageUris.toMutableList()
                                        val item = mutable.removeAt(idx)
                                        mutable.add(idx + 1, item)
                                        selectedImageUris = mutable
                                    }
                                },
                                onRemoveImage = { uri ->
                                    selectedImageUris = selectedImageUris.filter { it != uri }
                                },
                                onConvert = {
                                    viewModel.imagesToPdf(
                                        uris = selectedImageUris,
                                        customName = imageToPdfCustomName,
                                        orientation = imageToPdfOrientation,
                                        fitMode = imageToPdfFitMode,
                                        margin = imageToPdfMargin
                                    )
                                }
                            )
                        }
                    }

                    ToolType.COMPRESS_IMAGE -> {
                        item {
                            CompressImageContent(
                                selectedFile = selectedImageFile,
                                selectedUri = selectedSingleImageUri,
                                quality = imageCompressionQuality,
                                onQualityChange = { imageCompressionQuality = it },
                                scalePercent = imageScalePercent,
                                onScaleChange = { imageScalePercent = it },
                                customName = imageOutputName,
                                onCustomNameChange = { imageOutputName = it },
                                onPickImage = {
                                    singleImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onUseDemo = { loadSampleJpg() },
                                onCompress = {
                                    viewModel.compressImage(
                                        uri = selectedSingleImageUri,
                                        file = selectedImageFile,
                                        quality = imageCompressionQuality.toInt(),
                                        scalePercent = imageScalePercent,
                                        customName = imageOutputName
                                    )
                                }
                            )
                        }
                    }

                    ToolType.PDF_TO_IMAGE -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null) {
                            item {
                                PdfToImageContent(
                                    isJpeg = isJpegFormat,
                                    onFormatChange = { isJpegFormat = it },
                                    onConvert = {
                                        viewModel.pdfToImages(singlePdfFile!!, isJpegFormat)
                                    }
                                )
                            }
                        }
                    }

                    ToolType.ORGANIZE -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null && pageItems.isNotEmpty()) {
                            item {
                                OrganizeToolContent(
                                    pageItems = pageItems,
                                    onRotate = { index ->
                                        pageItems = pageItems.mapIndexed { idx, item ->
                                            if (idx == index) item.copy(rotation = (item.rotation + 90) % 360) else item
                                        }
                                    },
                                    onDelete = { index ->
                                        pageItems = pageItems.mapIndexed { idx, item ->
                                            if (idx == index) item.copy(isSelected = !item.isSelected) else item
                                        }
                                    },
                                    onSave = {
                                        viewModel.organizePages(singlePdfFile!!, pageItems)
                                    }
                                )
                            }
                        }
                    }

                    ToolType.WATERMARK -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null) {
                            item {
                                WatermarkToolContent(
                                    text = watermarkText,
                                    onTextChange = { watermarkText = it },
                                    position = watermarkPosition,
                                    onPositionChange = { watermarkPosition = it },
                                    opacity = watermarkOpacity,
                                    onOpacityChange = { watermarkOpacity = it },
                                    onApply = {
                                        val config = WatermarkConfig(
                                            text = watermarkText,
                                            position = watermarkPosition,
                                            opacity = watermarkOpacity
                                        )
                                        viewModel.watermarkPdf(singlePdfFile!!, config)
                                    }
                                )
                            }
                        }
                    }

                    ToolType.PAGE_NUMBERS -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null) {
                            item {
                                PageNumberToolContent(
                                    position = pageNumberPosition,
                                    onPositionChange = { pageNumberPosition = it },
                                    onApply = {
                                        val config = PageNumberConfig(position = pageNumberPosition)
                                        viewModel.addPageNumbers(singlePdfFile!!, config)
                                    }
                                )
                            }
                        }
                    }

                    ToolType.SIGN -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null && pageItems.isNotEmpty()) {
                            item {
                                SignToolContent(
                                    pageCount = pageItems.size,
                                    selectedPage = selectedSignaturePage,
                                    onPageSelected = { selectedSignaturePage = it },
                                    onStartSigning = { showSignaturePad = true }
                                )
                            }
                        }
                    }

                    ToolType.READER -> {
                        item {
                            SingleFilePickerHeader(
                                currentFile = singlePdfFile,
                                onPick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                                onUseDemo = { loadSample() }
                            )
                        }

                        if (singlePdfFile != null) {
                            item {
                                Button(
                                    onClick = { onOpenViewer(singlePdfFile!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_launch_reader")
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in PDF Reader", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Signature drawing dialog/pad
            if (showSignaturePad && singlePdfFile != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    SignaturePad(
                        onSignatureReady = { sigBitmap ->
                            showSignaturePad = false
                            viewModel.signPdf(singlePdfFile!!, sigBitmap, selectedSignaturePage)
                        },
                        onCancel = { showSignaturePad = false }
                    )
                }
            }
        }
    }
}

private fun loadPagesForFile(file: File, onLoaded: (List<PageItem>) -> Unit) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val count = PdfProcessor.getPageCount(file)
        val list = mutableListOf<PageItem>()
        for (i in 0 until count) {
            val thumb = PdfProcessor.renderPageThumbnail(file, i, 220)
            list.add(PageItem(pageIndex = i, thumbnail = thumb, rotation = 0, isSelected = true))
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onLoaded(list)
        }
    }
}

@Composable
fun SingleFilePickerHeader(
    currentFile: File?,
    onPick: () -> Unit,
    onUseDemo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (currentFile == null) {
                Text(
                    text = "Select Document",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose a PDF from your storage or try our demo file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                        modifier = Modifier.weight(1f).testTag("btn_select_pdf")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select PDF")
                    }
                    OutlinedButton(
                        onClick = onUseDemo,
                        modifier = Modifier.weight(1f).testTag("btn_demo_file")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Demo")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFECEB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFFE5322D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentFile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = PdfProcessor.formatFileSize(currentFile.length()),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onPick) {
                        Text("Change", color = Color(0xFFE5322D))
                    }
                }
            }
        }
    }
}

@Composable
fun MergeToolContent(
    selectedFiles: List<File>,
    outputName: String,
    onOutputNameChange: (String) -> Unit,
    onAddFiles: () -> Unit,
    onUseDemo: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onReverseOrder: () -> Unit,
    onClearAll: () -> Unit,
    onRemove: (Int) -> Unit,
    onMerge: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Combine Documents (${selectedFiles.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (selectedFiles.isNotEmpty()) {
                    Text(
                        text = "Arrange files in your desired merge order",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onUseDemo) {
                    Text("+ Add Demo", color = Color(0xFFE5322D))
                }
                FilledTonalButton(onClick = onAddFiles, modifier = Modifier.testTag("btn_add_pdf_merge")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add PDFs")
                }
            }
        }

        if (selectedFiles.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CallMerge,
                        contentDescription = null,
                        tint = Color(0xFFE5322D),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No PDF files selected", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap 'Add PDFs' or '+ Add Demo' to select and arrange documents in any order.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Reordering toolbar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF1F0),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = Color(0xFFE5322D),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Order: Top to Bottom",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onReverseOrder,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFE5322D))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reverse", fontSize = 11.sp, color = Color(0xFFE5322D), fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // List of PDF files with sequence numbers and reordering arrows
            selectedFiles.forEachIndexed { index, file ->
                val pageCount by produceState(initialValue = 1, file.absolutePath) {
                    value = PdfProcessor.getPageCount(file)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE5322D),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "$pageCount ${if (pageCount == 1) "page" else "pages"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = PdfProcessor.formatFileSize(file.length()),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Move Up
                        IconButton(
                            onClick = { onMoveUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                tint = if (index > 0) Color(0xFFE5322D) else Color.LightGray
                            )
                        }

                        // Move Down
                        IconButton(
                            onClick = { onMoveDown(index) },
                            enabled = index < selectedFiles.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                tint = if (index < selectedFiles.lastIndex) Color(0xFFE5322D) else Color.LightGray
                            )
                        }

                        // Delete
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove",
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Custom output name field
            OutlinedTextField(
                value = outputName,
                onValueChange = onOutputNameChange,
                label = { Text("Output PDF Name (Optional)") },
                placeholder = { Text("e.g. Merged_Report.pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_merge_output_name")
            )

            // Primary Merge Button
            Button(
                onClick = onMerge,
                enabled = selectedFiles.size >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_do_merge")
            ) {
                Icon(Icons.Default.CallMerge, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedFiles.size >= 2) "Merge ${selectedFiles.size} PDFs in This Order" else "Select at least 2 PDFs to Merge",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SplitToolContent(
    file: File,
    pageItems: List<PageItem>,
    onTogglePage: (Int) -> Unit,
    onSplitSelected: () -> Unit,
    onSplitAll: () -> Unit
) {
    val selectedCount = pageItems.count { it.isSelected }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Select Pages to Extract (${selectedCount} of ${pageItems.size} selected)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pageItems) { index, item ->
                Card(
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTogglePage(index) }
                        .border(
                            width = if (item.isSelected) 2.5.dp else 1.dp,
                            color = if (item.isSelected) Color(0xFFE5322D) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        item.thumbnail?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                            color = if (item.isSelected) Color(0xFFE5322D) else Color.DarkGray.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "Pg ${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSplitSelected,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                modifier = Modifier.weight(1.2f).height(50.dp).testTag("btn_extract_selected")
            ) {
                Text("Extract ($selectedCount)", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSplitAll,
                modifier = Modifier.weight(1f).height(50.dp).testTag("btn_split_all")
            ) {
                Text("Split All")
            }
        }
    }
}

@Composable
fun CompressToolContent(
    selectedMode: CompressionMode,
    onModeSelected: (CompressionMode) -> Unit,
    onCompress: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Select Compression Level",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        CompressionMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onModeSelected(mode) }
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFF0F9FF) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeSelected(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0284C7))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = mode.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mode.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onCompress,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_do_compress")
        ) {
            Icon(Icons.Default.Compress, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compress PDF Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ImageToPdfContent(
    imageUris: List<Uri>,
    orientation: PageOrientation,
    onOrientationChange: (PageOrientation) -> Unit,
    fitMode: ImageFitMode,
    onFitModeChange: (ImageFitMode) -> Unit,
    margin: Float,
    onMarginChange: (Float) -> Unit,
    customName: String,
    onCustomNameChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onAddDemoJpg: () -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onConvert: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Selected Images (${imageUris.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (imageUris.isNotEmpty()) {
                    Text(
                        text = "Use arrows to arrange page sequence",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onAddDemoJpg) {
                    Text("+ Demo JPG", color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(onClick = onPickImages, modifier = Modifier.testTag("btn_pick_images")) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Photos")
                }
            }
        }

        if (imageUris.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No JPG/PNG images selected", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Select JPG, JPEG or PNG photos to create your PDF. You can reorder and customize page layouts.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Grid of images with order badges and move arrows
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(imageUris) { index, uri ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top badge: Page index
                            Surface(
                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                color = Color(0xFF16A34A),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "Page ${index + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = { onRemoveImage(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(26.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                            }

                            // Bottom bar: Reorder arrows
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onMoveLeft(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Move Earlier",
                                            tint = if (index > 0) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Text(
                                        text = "#${index + 1}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    IconButton(
                                        onClick = { onMoveRight(index) },
                                        enabled = index < imageUris.lastIndex,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "Move Later",
                                            tint = if (index < imageUris.lastIndex) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Page Setup Card (Orientation & Fit)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "PDF Layout Options", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    // Orientation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Orientation:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilterChip(
                            selected = orientation == PageOrientation.PORTRAIT,
                            onClick = { onOrientationChange(PageOrientation.PORTRAIT) },
                            label = { Text("Portrait", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = orientation == PageOrientation.LANDSCAPE,
                            onClick = { onOrientationChange(PageOrientation.LANDSCAPE) },
                            label = { Text("Landscape", fontSize = 11.sp) }
                        )
                    }

                    // Fit mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Page Fit:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilterChip(
                            selected = fitMode == ImageFitMode.FIT_PAGE,
                            onClick = { onFitModeChange(ImageFitMode.FIT_PAGE) },
                            label = { Text("Fit (Margins)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = fitMode == ImageFitMode.FILL_PAGE,
                            onClick = { onFitModeChange(ImageFitMode.FILL_PAGE) },
                            label = { Text("Fill (Borderless)", fontSize = 11.sp) }
                        )
                    }

                    // Margins if Fit mode
                    if (fitMode == ImageFitMode.FIT_PAGE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Margins:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf(0f to "None", 10f to "Small", 20f to "Normal", 36f to "Large").forEach { (mVal, mLabel) ->
                                FilterChip(
                                    selected = margin == mVal,
                                    onClick = { onMarginChange(mVal) },
                                    label = { Text(mLabel, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Custom output name
            OutlinedTextField(
                value = customName,
                onValueChange = onCustomNameChange,
                label = { Text("PDF Filename (Optional)") },
                placeholder = { Text("e.g. MyPhotos_Album.pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_images_pdf_name")
            )

            Button(
                onClick = onConvert,
                enabled = imageUris.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_do_images_to_pdf")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert ${imageUris.size} Images to PDF", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CompressImageContent(
    selectedFile: File?,
    selectedUri: Uri?,
    quality: Float,
    onQualityChange: (Float) -> Unit,
    scalePercent: Int,
    onScaleChange: (Int) -> Unit,
    customName: String,
    onCustomNameChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onUseDemo: () -> Unit,
    onCompress: () -> Unit
) {
    val hasImage = selectedFile != null || selectedUri != null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Picker Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!hasImage) {
                    Text(
                        text = "Select JPG / JPEG Image",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose an image from your gallery or load a sample JPG to test instant offline compression.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPickImage,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                            modifier = Modifier.weight(1f).testTag("btn_select_image_compress")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select JPG")
                        }
                        OutlinedButton(
                            onClick = onUseDemo,
                            modifier = Modifier.weight(1f).testTag("btn_demo_image_compress")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Demo JPG")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFF1F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedFile != null) {
                                AsyncImage(
                                    model = selectedFile,
                                    contentDescription = "Selected JPG",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (selectedUri != null) {
                                AsyncImage(
                                    model = selectedUri,
                                    contentDescription = "Selected JPG",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFile?.name ?: "Selected Photo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            val fileSizeText = if (selectedFile != null) {
                                PdfProcessor.formatFileSize(selectedFile.length())
                            } else {
                                "Ready to compress"
                            }
                            Text(
                                text = fileSizeText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(onClick = onPickImage) {
                            Text("Change", color = Color(0xFFE5322D))
                        }
                    }
                }
            }
        }

        if (hasImage) {
            // Quality Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Compression Quality",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE5322D).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${quality.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE5322D),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val qualityFeedback = when {
                        quality <= 35f -> "Extreme Compression (Smallest file size, lower clarity)"
                        quality <= 60f -> "High Compression (Great file savings, good clarity)"
                        quality <= 80f -> "Recommended (Balanced file size & excellent clarity)"
                        else -> "Maximum Quality (Minimal compression, sharpest details)"
                    }

                    Text(
                        text = qualityFeedback,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = quality,
                        onValueChange = onQualityChange,
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE5322D),
                            activeTrackColor = Color(0xFFE5322D)
                        ),
                        modifier = Modifier.testTag("slider_jpg_quality")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Resolution Scaling",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            100 to "100% (Original)",
                            75 to "75%",
                            50 to "50%",
                            25 to "25%"
                        ).forEach { (scaleVal, scaleLabel) ->
                            FilterChip(
                                selected = scalePercent == scaleVal,
                                onClick = { onScaleChange(scaleVal) },
                                label = { Text(scaleLabel, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Output name
            OutlinedTextField(
                value = customName,
                onValueChange = onCustomNameChange,
                label = { Text("Output Filename (Optional)") },
                placeholder = { Text("e.g. Compressed_Photo.jpg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_compress_jpg_name")
            )

            // Compress Action Button
            Button(
                onClick = onCompress,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_do_compress_jpg")
            ) {
                Icon(Icons.Default.Compress, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Compress JPG (${quality.toInt()}%)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PdfToImageContent(
    isJpeg: Boolean,
    onFormatChange: (Boolean) -> Unit,
    onConvert: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Choose Image Format", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = isJpeg,
                onClick = { onFormatChange(true) },
                label = { Text("JPG (Smaller file size)") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !isJpeg,
                onClick = { onFormatChange(false) },
                label = { Text("PNG (Crisp lossless)") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onConvert,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_do_pdf_to_images")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Pages to Images", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OrganizeToolContent(
    pageItems: List<PageItem>,
    onRotate: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Tap page to rotate (90°). Use trash icon to omit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pageItems) { index, item ->
                Card(
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRotate(index) }
                        .border(
                            width = 1.dp,
                            color = if (item.isSelected) Color(0xFF8B5CF6) else Color.LightGray,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        item.thumbnail?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxSize().rotate(item.rotation.toFloat())
                            )
                        }

                        // Rotation badge
                        if (item.rotation > 0) {
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                color = Color(0xFF8B5CF6),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = "${item.rotation}°",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Bottom bar: page # & delete
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "P.${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onDelete(index) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isSelected) Icons.Default.Delete else Icons.Default.Restore,
                                    contentDescription = "Toggle",
                                    tint = if (item.isSelected) Color.White else Color(0xFF22C55E),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_save_organize")
        ) {
            Icon(Icons.Default.DoneAll, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Organized PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WatermarkToolContent(
    text: String,
    onTextChange: (String) -> Unit,
    position: WatermarkPosition,
    onPositionChange: (WatermarkPosition) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    onApply: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Watermark Text") },
            placeholder = { Text("e.g. CONFIDENTIAL, DRAFT") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Watermark Position", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatermarkPosition.entries.forEach { pos ->
                FilterChip(
                    selected = pos == position,
                    onClick = { onPositionChange(pos) },
                    label = { Text(pos.title, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(text = "Opacity: ${(opacity * 100).toInt()}%", fontSize = 14.sp)
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.1f..0.8f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFD97706), activeTrackColor = Color(0xFFD97706))
        )

        Button(
            onClick = onApply,
            enabled = text.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_apply_watermark")
        ) {
            Icon(Icons.Default.BrandingWatermark, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply Watermark to All Pages", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PageNumberToolContent(
    position: PageNumberPosition,
    onPositionChange: (PageNumberPosition) -> Unit,
    onApply: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Position on Page", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        PageNumberPosition.entries.forEach { pos ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPositionChange(pos) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = pos == position,
                    onClick = { onPositionChange(pos) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2563EB))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = pos.title, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onApply,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_apply_page_numbers")
        ) {
            Icon(Icons.Default.FormatListNumbered, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Page Numbers", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SignToolContent(
    pageCount: Int,
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
    onStartSigning: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Select Page to Place Signature", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (p in 0 until pageCount.coerceAtMost(5)) {
                FilterChip(
                    selected = selectedPage == p,
                    onClick = { onPageSelected(p) },
                    label = { Text("Page ${p + 1}") }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onStartSigning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_open_signature_pad")
        ) {
            Icon(Icons.Default.Draw, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Draw & Stamp Signature", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
