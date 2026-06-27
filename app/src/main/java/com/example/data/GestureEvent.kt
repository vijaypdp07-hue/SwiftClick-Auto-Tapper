package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class GestureEvent(
    val type: String, // "tap" or "swipe"
    val points: List<Point>, // 1 for tap, 2 for swipe (start, end)
    val delayMs: Long,
    val durationMs: Long? = null,
    val jitterMs: Long? = null
)

@Serializable
data class Point(
    val x: Float,
    val y: Float
)

@Serializable
data class ScriptData(
    val name: String = "Untitled Script",
    val createdDate: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val events: List<GestureEvent>
)
