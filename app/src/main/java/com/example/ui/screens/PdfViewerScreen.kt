package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.PdfProcessor
import com.example.pdf.PdfRendererCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ViewDisplayMode {
    PAGE_FLIP,       // Single page with horizontal flipping
    CONTINUOUS_SCROLL // Vertical continuous scroll
}

enum class ReadingFilterMode(val label: String) {
    NORMAL("Normal"),
    SEPIA("Sepia"),
    NIGHT("Night")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    file: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Handle system back button
    BackHandler {
        onBack()
    }

    // PDF Renderer instance
    val rendererCore = remember(file) { PdfRendererCore(file) }
    var totalPages by remember { mutableIntStateOf(1) }
    var isInitialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }

    // Navigation and viewing states
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var displayMode by remember { mutableStateOf(ViewDisplayMode.PAGE_FLIP) }
    var readingFilter by remember { mutableStateOf(ReadingFilterMode.NORMAL) }
    var pageRotation by remember { mutableIntStateOf(0) } // 0, 90, 180, 270

    // Zoom & Pan state (for Page Flip mode)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // UI visibility toggles
    var showChromeControls by remember { mutableStateOf(true) }
    var showThumbnailsStrip by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Initialize renderer
    LaunchedEffect(file) {
        val success = rendererCore.init()
        if (success) {
            totalPages = rendererCore.pageCount.coerceAtLeast(1)
            isInitialized = true
        } else {
            initError = "Unable to open PDF document. The file may be corrupt or encrypted."
        }
    }

    // Cleanup renderer when screen leaves composition
    DisposableEffect(file) {
        onDispose {
            rendererCore.close()
        }
    }

    // Reset zoom when switching pages in page flip mode
    LaunchedEffect(currentPageIndex) {
        zoomScale = 1.0f
        panOffsetX = 0f
        panOffsetY = 0f
    }

    // Color filter matrix
    val currentColorFilter = remember(readingFilter) {
        when (readingFilter) {
            ReadingFilterMode.NORMAL -> null
            ReadingFilterMode.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReadingFilterMode.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val backgroundColor = when (readingFilter) {
        ReadingFilterMode.NORMAL -> Color(0xFFF1F5F9)
        ReadingFilterMode.SEPIA -> Color(0xFFFBF0D9)
        ReadingFilterMode.NIGHT -> Color(0xFF121212)
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showChromeControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Page ${currentPageIndex + 1} of $totalPages • ${PdfProcessor.formatFileSize(file.length())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_viewer_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        // Display Mode Switcher (Horizontal Page Flip vs Continuous Scroll)
                        IconButton(
                            onClick = {
                                displayMode = if (displayMode == ViewDisplayMode.PAGE_FLIP) {
                                    ViewDisplayMode.CONTINUOUS_SCROLL
                                } else {
                                    ViewDisplayMode.PAGE_FLIP
                                }
                            },
                            modifier = Modifier.testTag("btn_viewer_toggle_mode")
                        ) {
                            Icon(
                                imageVector = if (displayMode == ViewDisplayMode.PAGE_FLIP) {
                                    Icons.Default.ViewAgenda
                                } else {
                                    Icons.Default.MenuBook
                                },
                                contentDescription = if (displayMode == ViewDisplayMode.PAGE_FLIP) {
                                    "Switch to Continuous Scroll"
                                } else {
                                    "Switch to Page Flip"
                                }
                            )
                        }

                        // Rotate Page 90 degrees
                        IconButton(
                            onClick = {
                                pageRotation = (pageRotation + 90) % 360
                            },
                            modifier = Modifier.testTag("btn_viewer_rotate")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate 90 degrees"
                            )
                        }

                        // Reading Filter Mode Menu (Normal / Sepia / Night)
                        var filterMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { filterMenuExpanded = true },
                                modifier = Modifier.testTag("btn_viewer_filter_mode")
                            ) {
                                Icon(
                                    imageVector = when (readingFilter) {
                                        ReadingFilterMode.NORMAL -> Icons.Default.WbSunny
                                        ReadingFilterMode.SEPIA -> Icons.Default.Book
                                        ReadingFilterMode.NIGHT -> Icons.Default.DarkMode
                                    },
                                    contentDescription = "Reading Theme"
                                )
                            }
                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false }
                            ) {
                                ReadingFilterMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (mode) {
                                                    ReadingFilterMode.NORMAL -> Icons.Default.WbSunny
                                                    ReadingFilterMode.SEPIA -> Icons.Default.Book
                                                    ReadingFilterMode.NIGHT -> Icons.Default.DarkMode
                                                },
                                                contentDescription = null
                                            )
                                        },
                                        trailingIcon = {
                                            if (readingFilter == mode) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            readingFilter = mode
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // More options (Share, Print, Info)
                        var moreMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { moreMenuExpanded = true },
                                modifier = Modifier.testTag("btn_viewer_more")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Jump to Page") },
                                    leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showJumpDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Thumbnails Strip") },
                                    leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showThumbnailsStrip = !showThumbnailsStrip
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Document Info") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showInfoDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Share PDF") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        sharePdf(context, file)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print Document") },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        printPdf(context, file)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showChromeControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Interactive Page Scrubber Slider (for rapid flipping)
                        if (totalPages > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "1",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = currentPageIndex.toFloat(),
                                    onValueChange = { newVal ->
                                        currentPageIndex = newVal.toInt().coerceIn(0, totalPages - 1)
                                    },
                                    valueRange = 0f..(totalPages - 1).toFloat(),
                                    steps = (totalPages - 2).coerceAtLeast(0),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .testTag("slider_page_scrubber"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFE5322D),
                                        activeTrackColor = Color(0xFFE5322D)
                                    )
                                )
                                Text(
                                    text = "$totalPages",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Main Navigation Deck (First, Prev, Page Badge with Click-to-Jump, Next, Last)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalIconButton(
                                    onClick = { currentPageIndex = 0 },
                                    enabled = currentPageIndex > 0,
                                    modifier = Modifier.size(40.dp).testTag("btn_first_page")
                                ) {
                                    Icon(Icons.Default.FirstPage, contentDescription = "First Page")
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        if (currentPageIndex > 0) currentPageIndex--
                                    },
                                    enabled = currentPageIndex > 0,
                                    modifier = Modifier.size(40.dp).testTag("btn_prev_page")
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                                }
                            }

                            // Interactive Page Pill (tap opens Jump to Page)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { showJumpDialog = true }
                                    .testTag("pill_jump_page")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Page ${currentPageIndex + 1} / $totalPages",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (currentPageIndex < totalPages - 1) currentPageIndex++
                                    },
                                    enabled = currentPageIndex < totalPages - 1,
                                    modifier = Modifier.size(40.dp).testTag("btn_next_page")
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                                }
                                FilledTonalIconButton(
                                    onClick = { currentPageIndex = totalPages - 1 },
                                    enabled = currentPageIndex < totalPages - 1,
                                    modifier = Modifier.size(40.dp).testTag("btn_last_page")
                                ) {
                                    Icon(Icons.Default.LastPage, contentDescription = "Last Page")
                                }
                            }
                        }

                        // Collapsible Page Thumbnail Strip for visual page flipping
                        if (showThumbnailsStrip && totalPages > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalThumbnailStrip(
                                rendererCore = rendererCore,
                                totalPages = totalPages,
                                currentPageIndex = currentPageIndex,
                                onSelectPage = { selectedIdx ->
                                    currentPageIndex = selectedIdx
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundColor)
        ) {
            if (!isInitialized) {
                if (initError != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = initError!!,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFFE5322D))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading document...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                when (displayMode) {
                    ViewDisplayMode.PAGE_FLIP -> {
                        PageFlipView(
                            rendererCore = rendererCore,
                            totalPages = totalPages,
                            currentPageIndex = currentPageIndex,
                            zoomScale = zoomScale,
                            panOffsetX = panOffsetX,
                            panOffsetY = panOffsetY,
                            rotation = pageRotation,
                            colorFilter = currentColorFilter,
                            onPageChanged = { newPage -> currentPageIndex = newPage },
                            onZoomChanged = { newZoom, newPanX, newPanY ->
                                zoomScale = newZoom
                                panOffsetX = newPanX
                                panOffsetY = newPanY
                            },
                            onToggleControls = { showChromeControls = !showChromeControls }
                        )
                    }
                    ViewDisplayMode.CONTINUOUS_SCROLL -> {
                        ContinuousScrollView(
                            rendererCore = rendererCore,
                            totalPages = totalPages,
                            currentPageIndex = currentPageIndex,
                            rotation = pageRotation,
                            colorFilter = currentColorFilter,
                            onCurrentPageVisible = { pageIdx -> currentPageIndex = pageIdx },
                            onToggleControls = { showChromeControls = !showChromeControls }
                        )
                    }
                }

                // Floating Zoom & Navigation Controls Dock (Bottom Right / Floating Center)
                FloatingZoomControls(
                    zoomScale = zoomScale,
                    displayMode = displayMode,
                    showChromeControls = showChromeControls,
                    onZoomIn = {
                        zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f)
                    },
                    onZoomOut = {
                        zoomScale = (zoomScale - 0.25f).coerceAtLeast(1.0f)
                        if (zoomScale == 1.0f) {
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    },
                    onResetZoom = {
                        zoomScale = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    },
                    onToggleFullScreen = {
                        showChromeControls = !showChromeControls
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        JumpToPageDialog(
            totalPages = totalPages,
            currentPage = currentPageIndex + 1,
            onDismiss = { showJumpDialog = false },
            onJumpToPage = { targetPage ->
                currentPageIndex = (targetPage - 1).coerceIn(0, totalPages - 1)
                showJumpDialog = false
            }
        )
    }

    // Document Info Dialog
    if (showInfoDialog) {
        DocumentInfoDialog(
            file = file,
            totalPages = totalPages,
            onDismiss = { showInfoDialog = false }
        )
    }
}

/**
 * Single page flip view using HorizontalPager for natural swipe and tap page flipping.
 */
@Composable
fun PageFlipView(
    rendererCore: PdfRendererCore,
    totalPages: Int,
    currentPageIndex: Int,
    zoomScale: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    rotation: Int,
    colorFilter: ColorFilter?,
    onPageChanged: (Int) -> Unit,
    onZoomChanged: (scale: Float, panX: Float, panY: Float) -> Unit,
    onToggleControls: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { totalPages }
    )

    // Sync external page changes to pagerState
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    // Sync pagerState swipe to external page index
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentPageIndex) {
            onPageChanged(pagerState.currentPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = zoomScale <= 1.05f, // Allow swiping pages only when not zoomed in
        modifier = Modifier.fillMaxSize().testTag("horizontal_pager_pdf")
    ) { pageIdx ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            PdfPageRenderedView(
                rendererCore = rendererCore,
                pageIndex = pageIdx,
                isCurrentPage = pageIdx == currentPageIndex,
                scale = if (pageIdx == currentPageIndex) zoomScale else 1f,
                panX = if (pageIdx == currentPageIndex) panOffsetX else 0f,
                panY = if (pageIdx == currentPageIndex) panOffsetY else 0f,
                rotation = rotation,
                colorFilter = colorFilter,
                onTransform = { newScale, newPanX, newPanY ->
                    onZoomChanged(newScale, newPanX, newPanY)
                },
                onDoubleTap = {
                    if (zoomScale > 1.2f) {
                        onZoomChanged(1.0f, 0f, 0f)
                    } else {
                        onZoomChanged(2.2f, 0f, 0f)
                    }
                },
                onSingleTap = onToggleControls
            )
        }
    }
}

/**
 * Continuous vertical scroll mode using LazyColumn with active visible page tracking.
 */
@Composable
fun ContinuousScrollView(
    rendererCore: PdfRendererCore,
    totalPages: Int,
    currentPageIndex: Int,
    rotation: Int,
    colorFilter: ColorFilter?,
    onCurrentPageVisible: (Int) -> Unit,
    onToggleControls: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)

    // Track visible page
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onCurrentPageVisible(listState.firstVisibleItemIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleControls() })
            }
            .testTag("continuous_scroll_pdf"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(totalPages) { pageIdx ->
            Card(
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .wrapContentHeight()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PdfPageStaticItem(
                        rendererCore = rendererCore,
                        pageIndex = pageIdx,
                        rotation = rotation,
                        colorFilter = colorFilter
                    )
                    Surface(
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Page ${pageIdx + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders a single PDF page with pinch-to-zoom, 2D pan scrolling, and double-tap gestures.
 */
@Composable
fun PdfPageRenderedView(
    rendererCore: PdfRendererCore,
    pageIndex: Int,
    isCurrentPage: Boolean,
    scale: Float,
    panX: Float,
    panY: Float,
    rotation: Int,
    colorFilter: ColorFilter?,
    onTransform: (scale: Float, panX: Float, panY: Float) -> Unit,
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, rotation) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val bmp = rendererCore.renderPage(pageIndex, targetWidth = 1200, rotation = rotation)
            withContext(Dispatchers.Main) {
                pageBitmap = bmp
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1.0f, 4.0f)
                    if (newScale > 1.0f) {
                        val maxPanX = 600f * (newScale - 1f)
                        val maxPanY = 800f * (newScale - 1f)
                        val newPanX = (panX + pan.x).coerceIn(-maxPanX, maxPanX)
                        val newPanY = (panY + pan.y).coerceIn(-maxPanY, maxPanY)
                        onTransform(newScale, newPanX, newPanY)
                    } else {
                        onTransform(1.0f, 0f, 0f)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFE5322D),
                modifier = Modifier.size(36.dp)
            )
        } else if (pageBitmap != null) {
            Card(
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panX,
                        translationY = panY
                    )
            ) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .wrapContentSize()
                        .background(Color.White)
                )
            }
        } else {
            Text(
                text = "Failed to render page ${pageIndex + 1}",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Static page renderer for continuous vertical scroll mode.
 */
@Composable
fun PdfPageStaticItem(
    rendererCore: PdfRendererCore,
    pageIndex: Int,
    rotation: Int,
    colorFilter: ColorFilter?
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, rotation) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val bmp = rendererCore.renderPage(pageIndex, targetWidth = 1000, rotation = rotation)
            withContext(Dispatchers.Main) {
                pageBitmap = bmp
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFE5322D),
                modifier = Modifier.size(32.dp).padding(24.dp)
            )
        } else if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.White)
            )
        } else {
            Text("Error loading page ${pageIndex + 1}", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

/**
 * Floating Zoom and View controls dock.
 */
@Composable
fun FloatingZoomControls(
    zoomScale: Float,
    displayMode: ViewDisplayMode,
    showChromeControls: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // Zoom Out (-)
            IconButton(
                onClick = onZoomOut,
                enabled = zoomScale > 1.0f,
                modifier = Modifier.size(36.dp).testTag("btn_zoom_out")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }

            // Zoom Percentage Indicator (Click to reset 100%)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (zoomScale > 1.0f) Color(0xFFE5322D).copy(alpha = 0.15f) else Color.Transparent,
                modifier = Modifier
                    .clickable { onResetZoom() }
                    .padding(horizontal = 4.dp)
                    .testTag("pill_zoom_percentage")
            ) {
                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (zoomScale > 1.0f) Color(0xFFE5322D) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Zoom In (+)
            IconButton(
                onClick = onZoomIn,
                enabled = zoomScale < 4.0f,
                modifier = Modifier.size(36.dp).testTag("btn_zoom_in")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }

            // Reset Zoom / Fit Page
            if (zoomScale > 1.0f) {
                IconButton(
                    onClick = onResetZoom,
                    modifier = Modifier.size(36.dp).testTag("btn_zoom_fit")
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = "Fit Page", modifier = Modifier.size(18.dp))
                }
            }

            // Fullscreen Immersion Toggle
            IconButton(
                onClick = onToggleFullScreen,
                modifier = Modifier.size(36.dp).testTag("btn_fullscreen_toggle")
            ) {
                Icon(
                    imageVector = if (showChromeControls) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    contentDescription = "Toggle Fullscreen",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Bottom horizontal thumbnail carousel allowing visual page flipping with mini preview cards.
 */
@Composable
fun HorizontalThumbnailStrip(
    rendererCore: PdfRendererCore,
    totalPages: Int,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit
) {
    Column {
        Text(
            text = "Page Previews",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth().height(100.dp)
        ) {
            items(totalPages) { pageIdx ->
                val isSelected = pageIdx == currentPageIndex
                var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(pageIdx) {
                    withContext(Dispatchers.IO) {
                        val bmp = rendererCore.renderThumbnail(pageIdx, targetWidth = 140)
                        withContext(Dispatchers.Main) {
                            thumbBitmap = bmp
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(8.dp),
                    border = if (isSelected) BorderStroke(2.dp, Color(0xFFE5322D)) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFFFF1F2) else Color.White
                    ),
                    modifier = Modifier
                        .width(68.dp)
                        .fillMaxHeight()
                        .clickable { onSelectPage(pageIdx) }
                        .testTag("thumb_page_$pageIdx")
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbBitmap != null) {
                                Image(
                                    bitmap = thumbBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFE5322D)
                                )
                            }
                        }
                        Surface(
                            color = if (isSelected) Color(0xFFE5322D) else Color(0xFFF1F5F9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${pageIdx + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF475569),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Direct "Jump to Page" Dialog.
 */
@Composable
fun JumpToPageDialog(
    totalPages: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FindInPage,
                    contentDescription = null,
                    tint = Color(0xFFE5322D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Jump to Page", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter a page number between 1 and $totalPages:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Page Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            val page = pageInput.toIntOrNull()
                            if (page != null) onJumpToPage(page)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_jump_page")
                )

                // Quick jump preset chips
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Quick Jump:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onJumpToPage(1) },
                        label = { Text("First (1)") }
                    )
                    if (totalPages > 2) {
                        SuggestionChip(
                            onClick = { onJumpToPage(totalPages / 2) },
                            label = { Text("Middle (${totalPages / 2})") }
                        )
                    }
                    SuggestionChip(
                        onClick = { onJumpToPage(totalPages) },
                        label = { Text("Last ($totalPages)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val page = pageInput.toIntOrNull()
                    if (page != null) onJumpToPage(page)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5322D)),
                modifier = Modifier.testTag("btn_confirm_jump")
            ) {
                Text("Go to Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Document Metadata / Info Dialog.
 */
@Composable
fun DocumentInfoDialog(
    file: File,
    totalPages: Int,
    onDismiss: () -> Unit
) {
    val dateStr = remember(file) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(file.lastModified()))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFE5322D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Document Info", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "File Name", value = file.name)
                InfoRow(label = "Total Pages", value = "$totalPages pages")
                InfoRow(label = "File Size", value = PdfProcessor.formatFileSize(file.length()))
                InfoRow(label = "Modified", value = dateStr)
                InfoRow(label = "Format", value = "Portable Document Format (PDF)")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = PdfProcessor.getFileUri(context, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    } catch (e: Exception) {
        // Handled
    }
}

private fun printPdf(context: Context, file: File) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(file.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val input = FileInputStream(file)
                    val output = FileOutputStream(destination?.fileDescriptor)
                    input.copyTo(output)
                    input.close()
                    output.close()
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager?.print(file.name, printAdapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        // Handled
    }
}
