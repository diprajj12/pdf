package com.example.data.local

import kotlinx.coroutines.flow.Flow

class PdfRepository(private val dao: PdfRecordDao) {
    val allRecords: Flow<List<PdfRecord>> = dao.getAllRecords()

    fun search(query: String): Flow<List<PdfRecord>> = dao.searchRecords(query)

    suspend fun insert(record: PdfRecord): Long = dao.insertRecord(record)

    suspend fun delete(record: PdfRecord) = dao.deleteRecord(record)

    suspend fun deleteById(id: Long) = dao.deleteRecordById(id)

    suspend fun clearAll() = dao.clearAll()
}
