package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gesturesJson: String, // Stored as JSON string
    val createdAt: Long = System.currentTimeMillis()
)
