package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "certificates")
data class Certificate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // STCW, Medical, Flag State, GMDSS, Radio, Other, etc.
    val issueDate: Long,  // Timestamp
    val expiryDate: Long, // Timestamp
    val certNumber: String? = null,
    val attachmentPath: String? = null, // Local file system path to attached photo/PDF
    val isMandatory: Boolean = true,
    val folderName: String? = null
)
