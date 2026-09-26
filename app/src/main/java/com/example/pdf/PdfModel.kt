package com.example.pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*
import java.io.File

enum class ToolCategory(val title: String) {
    ALL("All Tools"),
    POPULAR("Popular"),
    ORGANIZE("Organize"),
    OPTIMIZE("Optimize"),
    CONVERT("Convert"),
    EDIT("Edit & Sign")
}

enum class ToolType(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val badge: String?,
    val color: Color,
    val icon: ImageVector,
    val category: ToolCategory
) {
    MERGE(
        id = "merge",
        title = "Merge PDF",
        subtitle = "Combine & reorder files",
        description = "Select multiple PDF files, arrange their order with intuitive controls, and combine them into one document.",
        badge = "POPULAR",
        color = ToolColorMerge,
        icon = Icons.Default.CallMerge,
        category = ToolCategory.ORGANIZE
    ),
    SPLIT(
        id = "split",
        title = "Split PDF",
        subtitle = "Separate pages",
        description = "Extract selected page ranges or separate individual pages into independent files.",
        badge = null,
        color = ToolColorSplit,
        icon = Icons.AutoMirrored.Filled.CallSplit,
        category = ToolCategory.ORGANIZE
    ),
    COMPRESS(
        id = "compress",
        title = "Compress PDF",
        subtitle = "Reduce PDF file size",
        description = "Reduce PDF file size up to 80% while retaining crisp visual quality and formatting.",
        badge = "POPULAR",
        color = ToolColorCompress,
        icon = Icons.Default.Compress,
        category = ToolCategory.OPTIMIZE
    ),
    COMPRESS_IMAGE(
        id = "compress_image",
        title = "Compress JPG",
        subtitle = "Reduce JPG / JPEG size",
        description = "Compress JPG and JPEG photos with quality slider, resolution scaling, and instant file size savings.",
        badge = "NEW",
        color = Color(0xFFEA580C),
        icon = Icons.Default.Compress,
        category = ToolCategory.OPTIMIZE
    ),
    IMAGE_TO_PDF(
        id = "image_to_pdf",
        title = "JPG to PDF",
        subtitle = "JPG, JPEG & PNG to PDF",
        description = "Convert JPG, JPEG & PNG photos into a clean multi-page PDF with custom order, margins & orientation.",
        badge = "POPULAR",
        color = ToolColorImgToPdf,
        icon = Icons.Default.Image,
        category = ToolCategory.CONVERT
    ),
    PDF_TO_IMAGE(
        id = "pdf_to_image",
        title = "PDF to Image",
        subtitle = "Extract pages as JPG",
        description = "Convert each PDF page into high-definition JPG/PNG images ready for sharing.",
        badge = null,
        color = ToolColorPdfToImg,
        icon = Icons.Default.PhotoLibrary,
        category = ToolCategory.CONVERT
    ),
    ORGANIZE(
        id = "organize",
        title = "Organize Pages",
        subtitle = "Sort & rotate",
        description = "Rearrange page orders, rotate orientations (90°/180°), or remove unwanted pages.",
        badge = "NEW",
        color = ToolColorOrganize,
        icon = Icons.Default.ViewModule,
        category = ToolCategory.ORGANIZE
    ),
    WATERMARK(
        id = "watermark",
        title = "Watermark",
        subtitle = "Stamp text overlay",
        description = "Add custom text watermarks, confidential stamps, opacity, and rotation.",
        badge = null,
        color = ToolColorWatermark,
        icon = Icons.Default.BrandingWatermark,
        category = ToolCategory.EDIT
    ),
    PAGE_NUMBERS(
        id = "page_numbers",
        title = "Page Numbers",
        subtitle = "Add header / footer",
        description = "Add clean pagination and header numbering with custom positioning and typography.",
        badge = null,
        color = ToolColorPageNumber,
        icon = Icons.Default.FormatListNumbered,
        category = ToolCategory.EDIT
    ),
    SIGN(
        id = "sign",
        title = "Sign PDF",
        subtitle = "Digital signature",
        description = "Draw your personal signature on touch canvas and stamp it directly onto PDF documents.",
        badge = "PRO",
        color = ToolColorSign,
        icon = Icons.Default.Draw,
        category = ToolCategory.EDIT
    ),
    READER(
        id = "reader",
        title = "PDF Reader",
        subtitle = "Fast visual viewer",
        description = "Read, zoom, inspect metadata, print, and share your PDF documents seamlessly.",
        badge = null,
        color = ToolColorReader,
        icon = Icons.AutoMirrored.Filled.MenuBook,
        category = ToolCategory.POPULAR
    )
}

data class PdfFileItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val pageCount: Int = 0,
    val localFile: File? = null
)

data class PageItem(
    val pageIndex: Int,
    val thumbnail: Bitmap? = null,
    val rotation: Int = 0, // 0, 90, 180, 270
    val isSelected: Boolean = true
)

enum class CompressionMode(val title: String, val description: String, val scale: Float, val quality: Int) {
    EXTREME("Extreme Compression", "Smallest size, lower resolution (ideal for email)", 0.65f, 50),
    RECOMMENDED("Recommended", "Good quality, high compression (up to 70% reduction)", 0.85f, 75),
    LOW("Low Compression", "High quality, moderate compression", 1.0f, 88)
}

enum class WatermarkPosition(val title: String) {
    CENTER("Center Diagonal"),
    TOP("Top Header"),
    BOTTOM("Bottom Footer")
}

data class WatermarkConfig(
    val text: String = "CONFIDENTIAL",
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val opacity: Float = 0.35f,
    val textSize: Float = 48f,
    val colorArgb: Int = android.graphics.Color.RED,
    val angle: Float = -45f
)

enum class PageNumberPosition(val title: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_LEFT("Bottom Left"),
    TOP_RIGHT("Top Right")
}

data class PageNumberConfig(
    val format: String = "Page {n} of {total}",
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val fontSize: Float = 12f
)

data class ProcessResult(
    val success: Boolean,
    val outputFile: File? = null,
    val outputFiles: List<File> = emptyList(),
    val originalSize: Long = 0,
    val newSize: Long = 0,
    val message: String = ""
)

enum class PageOrientation(val title: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    AUTO("Auto (Match Image)")
}

enum class ImageFitMode(val title: String) {
    FIT_PAGE("Fit with Margins"),
    FILL_PAGE("Fill Full Page")
}

