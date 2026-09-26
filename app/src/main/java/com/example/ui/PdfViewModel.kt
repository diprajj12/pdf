package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PdfRecord
import com.example.data.local.PdfRepository
import com.example.pdf.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PdfRepository
    val allRecords: StateFlow<List<PdfRecord>>

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingMessage = MutableStateFlow("Processing your PDF...")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    private val _lastResult = MutableStateFlow<ProcessResult?>(null)
    val lastResult: StateFlow<ProcessResult?> = _lastResult.asStateFlow()

    private val _activeViewingFile = MutableStateFlow<File?>(null)
    val activeViewingFile: StateFlow<File?> = _activeViewingFile.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).pdfRecordDao()
        repository = PdfRepository(dao)

        allRecords = _searchQuery
            .debounce(200)
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    repository.allRecords
                } else {
                    repository.search(query.trim())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearResult() {
        _lastResult.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setActiveViewingFile(file: File?) {
        _activeViewingFile.value = file
        if (file != null && file.exists()) {
            // Save to recent records
            viewModelScope.launch {
                val pageCount = PdfProcessor.getPageCount(file)
                repository.insert(
                    PdfRecord(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        pageCount = pageCount,
                        operationType = "Viewed"
                    )
                )
            }
        }
    }

    fun createSampleDocument(onDone: (File) -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Creating sample PDF..."
            try {
                val file = PdfProcessor.generateSamplePdf(getApplication(), "Sample Business Report")
                val pageCount = PdfProcessor.getPageCount(file)
                repository.insert(
                    PdfRecord(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        pageCount = pageCount,
                        operationType = "Sample Document"
                    )
                )
                _isProcessing.value = false
                onDone(file)
            } catch (e: Exception) {
                _isProcessing.value = false
                _errorMessage.value = "Failed to create sample: ${e.localizedMessage}"
            }
        }
    }

    fun mergePdfs(files: List<File>, customName: String = "") {
        if (files.size < 2) {
            _errorMessage.value = "Please select at least 2 PDF files to merge."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Merging ${files.size} PDF files..."
            val outName = if (customName.isNotBlank()) {
                if (customName.endsWith(".pdf", ignoreCase = true)) customName else "$customName.pdf"
            } else {
                "Merged_${System.currentTimeMillis()}.pdf"
            }

            val result = PdfProcessor.mergePdfs(getApplication(), files, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Merge",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun splitPdf(file: File, pagesToKeep: List<Int>, customName: String = "") {
        if (pagesToKeep.isEmpty()) {
            _errorMessage.value = "Please select at least one page to extract."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Extracting pages..."
            val outName = if (customName.isNotBlank()) {
                if (customName.endsWith(".pdf", ignoreCase = true)) customName else "$customName.pdf"
            } else {
                "Split_${System.currentTimeMillis()}.pdf"
            }

            val result = PdfProcessor.splitPdf(getApplication(), file, pagesToKeep, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Split",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun splitAllPages(file: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Splitting into individual pages..."
            val result = PdfProcessor.splitAllPages(getApplication(), file)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                repository.insert(
                    PdfRecord(
                        fileName = "${result.outputFiles.size} Pages Separated",
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.newSize,
                        pageCount = result.outputFiles.size,
                        operationType = "Split All",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun compressPdf(file: File, mode: CompressionMode) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Compressing PDF with ${mode.title}..."
            val outName = "Compressed_${System.currentTimeMillis()}.pdf"
            val result = PdfProcessor.compressPdf(getApplication(), file, mode, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Compress",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun imagesToPdf(
        uris: List<Uri>,
        customName: String = "",
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        fitMode: ImageFitMode = ImageFitMode.FIT_PAGE,
        margin: Float = 20f
    ) {
        if (uris.isEmpty()) {
            _errorMessage.value = "Please select at least one image."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Converting ${uris.size} JPG/PNG images to PDF..."
            val outName = if (customName.isNotBlank()) {
                if (customName.endsWith(".pdf", ignoreCase = true)) customName else "$customName.pdf"
            } else {
                "Images_${System.currentTimeMillis()}.pdf"
            }

            val result = PdfProcessor.imagesToPdf(
                context = getApplication(),
                imageUris = uris,
                outputName = outName,
                orientation = orientation,
                fitMode = fitMode,
                margin = margin
            )
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "JPG to PDF"
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun compressImage(
        uri: Uri? = null,
        file: File? = null,
        quality: Int = 70,
        scalePercent: Int = 100,
        customName: String = ""
    ) {
        if (uri == null && file == null) {
            _errorMessage.value = "Please select a JPG/JPEG image to compress."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Compressing JPG with $quality% quality..."
            val outName = if (customName.isNotBlank()) {
                if (customName.endsWith(".jpg", ignoreCase = true) || customName.endsWith(".jpeg", ignoreCase = true)) {
                    customName
                } else "$customName.jpg"
            } else {
                "Compressed_${System.currentTimeMillis()}.jpg"
            }

            val result = PdfProcessor.compressImage(
                context = getApplication(),
                imageUri = uri,
                imageFile = file,
                quality = quality,
                scalePercent = scalePercent,
                outputName = outName
            )
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = 1,
                        operationType = "Compress JPG",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun createSampleImage(onDone: (File) -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Generating sample JPG..."
            try {
                val file = PdfProcessor.generateSampleJpg(getApplication(), "Sample Photo (Invoice/Scan)")
                _isProcessing.value = false
                onDone(file)
            } catch (e: Exception) {
                _isProcessing.value = false
                _errorMessage.value = "Failed to generate sample image: ${e.localizedMessage}"
            }
        }
    }

    fun pdfToImages(file: File, isJpeg: Boolean = true) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Extracting pages as images..."
            val result = PdfProcessor.pdfToImages(getApplication(), file, isJpeg)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                repository.insert(
                    PdfRecord(
                        fileName = "${result.outputFiles.size} Extracted Images",
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.newSize,
                        pageCount = result.outputFiles.size,
                        operationType = "PDF to Image",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun organizePages(file: File, pageItems: List<PageItem>) {
        val selectedPages = pageItems.filter { it.isSelected }
        if (selectedPages.isEmpty()) {
            _errorMessage.value = "At least one page must be included in the organized PDF."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Organizing PDF pages..."
            val outName = "Organized_${System.currentTimeMillis()}.pdf"
            val result = PdfProcessor.organizePages(getApplication(), file, pageItems, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Organize",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun watermarkPdf(file: File, config: WatermarkConfig) {
        if (config.text.isBlank()) {
            _errorMessage.value = "Watermark text cannot be empty."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Applying watermark..."
            val outName = "Watermarked_${System.currentTimeMillis()}.pdf"
            val result = PdfProcessor.watermarkPdf(getApplication(), file, config, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Watermark",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun addPageNumbers(file: File, config: PageNumberConfig) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Adding page numbers..."
            val outName = "Numbered_${System.currentTimeMillis()}.pdf"
            val result = PdfProcessor.addPageNumbers(getApplication(), file, config, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Page Numbers",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun signPdf(file: File, signatureBitmap: Bitmap, targetPageIndex: Int = 0) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Applying signature to document..."
            val outName = "Signed_${System.currentTimeMillis()}.pdf"
            val result = PdfProcessor.signPdf(getApplication(), file, signatureBitmap, targetPageIndex, outName)
            _isProcessing.value = false
            _lastResult.value = result

            if (result.success && result.outputFile != null) {
                val pageCount = PdfProcessor.getPageCount(result.outputFile)
                repository.insert(
                    PdfRecord(
                        fileName = result.outputFile.name,
                        filePath = result.outputFile.absolutePath,
                        fileSize = result.outputFile.length(),
                        pageCount = pageCount,
                        operationType = "Sign",
                        originalSize = result.originalSize
                    )
                )
            } else {
                _errorMessage.value = result.message
            }
        }
    }

    fun deleteRecord(record: PdfRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
