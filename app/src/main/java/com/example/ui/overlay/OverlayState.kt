package com.example.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.Point
import com.example.data.GestureEvent

class OverlayState {
    var singlePlacedPoint by mutableStateOf<Point?>(null)
    var isAddingSwipe by mutableStateOf(false)
    var swipeStep by mutableStateOf(0)
    var isWorkspaceVisible by mutableStateOf(true)
    
    var currentMode by mutableStateOf("")
    var scriptJson by mutableStateOf<String?>(null)
    var currentScriptName by mutableStateOf<String?>(null)
    
    // Single Point Config
    var singleIntervalMs by mutableStateOf(1000L)
    var singleJitterMs by mutableStateOf(0L)
    var singleTapCount by mutableStateOf(0) // 0 = infinite
    
    // For manual builder
    var isAddingPoint by mutableStateOf(false)
        private set
        
    var pendingActionType by mutableStateOf<String?>(null)
    var pendingPoints by mutableStateOf(listOf<Point>())
    
    fun startAddingPoint(actionType: String) {
        pendingActionType = actionType
        isAddingPoint = true
        pendingPoints = emptyList()
        swipeStep = 0
        onInterceptToggles?.invoke(true)
    }
    
    fun finishAddingPoint() {
        isAddingPoint = false
        onInterceptToggles?.invoke(false)
    }
        
    var events by mutableStateOf(listOf<GestureEvent>())
    var currentDelayMs by mutableStateOf(1000L)
    
    var onInterceptToggles: ((Boolean) -> Unit)? = null
}
