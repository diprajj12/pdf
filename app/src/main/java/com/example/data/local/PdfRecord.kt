package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_records")
data class PdfRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val pageCount: Int,
    val operationType: String,
    val originalSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
