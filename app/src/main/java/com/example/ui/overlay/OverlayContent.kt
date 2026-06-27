package com.example.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.data.Point
import kotlinx.serialization.json.Json
import com.example.data.ScriptData
import com.example.service.AutoClickerService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.PlainTooltip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    text: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, content = content)
    }
}

@Composable
fun WorkspaceContent(state: OverlayState) {
    val activePoints by (AutoClickerService.instance?.activePoints ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when {
                    !state.isWorkspaceVisible -> Color.Transparent
                    state.isAddingPoint -> Color.Black.copy(alpha = 0.3f)
                    state.currentMode == com.example.OverlayService.ACTION_START_SINGLE -> Color.Black.copy(alpha = 0.3f)
                    state.currentMode == com.example.OverlayService.ACTION_START_SWIPE -> Color.Black.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(state.isAddingPoint, state.currentMode) {
                if (state.currentMode == com.example.OverlayService.ACTION_START_RECORDER) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            val downTime = System.currentTimeMillis()
                            val startPt = Point(down.position.x, down.position.y)
                            if (state.recordingStartTime == 0L) {
                                state.recordingStartTime = downTime
                            }
                            
                            var upEvent: androidx.compose.ui.input.pointer.PointerInputChange? = null
                            var lastMove: androidx.compose.ui.input.pointer.PointerInputChange? = null
                            
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    if (change.pressed) {
                                        lastMove = change
                                    } else {
                                        upEvent = change
                                    }
                                }
                            } while (upEvent == null && event.changes.any { it.pressed })
                            
                            val upTime = System.currentTimeMillis()
                            val endPt = upEvent?.let { Point(it.position.x, it.position.y) } ?: lastMove?.let { Point(it.position.x, it.position.y) } ?: startPt
                            
                            val delay = downTime - state.recordingStartTime
                            val duration = upTime - downTime
                            
                            val distance = kotlin.math.hypot(endPt.x - startPt.x, endPt.y - startPt.y)
                            val gestureType = if (distance > 20) "swipe" else "tap"
                            val points = if (gestureType == "swipe") listOf(startPt, endPt) else listOf(startPt)
                            
                            if (state.isRecording) {
                                state.recordedEvents = state.recordedEvents + com.example.data.GestureEvent(
                                    type = gestureType,
                                    points = points,
                                    delayMs = delay,
                                    durationMs = duration
                                )
                            }
                            state.recordingStartTime = upTime // Next delay is relative to this up time
                        }
                    }
                } else if (state.isAddingPoint) {
                    detectTapGestures { offset ->
                        state.pendingPoints = state.pendingPoints + Point(offset.x, offset.y)
                        val actionType = state.pendingActionType
                        if ((actionType == "tap" && state.pendingPoints.size == 1) || 
                            (actionType == "swipe" && state.pendingPoints.size == 2)) {
                            state.events = state.events + com.example.data.GestureEvent(
                                type = actionType,
                                points = state.pendingPoints,
                                delayMs = state.currentDelayMs,
                                durationMs = if (actionType == "swipe") 300L else null
                            )
                            state.finishAddingPoint()
                        }
                    }
                } else if (state.currentMode == com.example.OverlayService.ACTION_START_SINGLE) {
                    detectTapGestures { offset ->
                        state.pendingPoints = listOf(Point(offset.x, offset.y))
                        state.singlePlacedPoint = Point(offset.x, offset.y)
                    }
                } else if (state.currentMode == com.example.OverlayService.ACTION_START_SWIPE) {
                    detectTapGestures { offset ->
                        if (state.swipeStep == 0) {
                            state.pendingPoints = listOf(Point(offset.x, offset.y))
                            state.swipeStep = 1
                        } else {
                            state.pendingPoints = state.pendingPoints + Point(offset.x, offset.y)
                            state.events = state.events + com.example.data.GestureEvent(
                                type = "swipe",
                                points = state.pendingPoints,
                                delayMs = state.currentDelayMs,
                                durationMs = 300L
                            )
                            state.pendingPoints = emptyList()
                            state.swipeStep = 0
                        }
                    }
                }
            }
    ) {
        // Draw markers
        for (event in state.events) {
            if (event.type == "tap" && event.points.isNotEmpty()) {
                val point = event.points[0]
                Box(
                    modifier = Modifier
                        .offset { IntOffset(point.x.roundToInt() - 24, point.y.roundToInt() - 24) }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                }
            } else if (event.type == "swipe" && event.points.size >= 2) {
                val start = event.points[0]
                val end = event.points[1]
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val arrowColor = Color.Blue.copy(alpha = 0.8f)
                    val strokeWidth = 8.dp.toPx()
                    drawLine(
                        color = arrowColor,
                        start = androidx.compose.ui.geometry.Offset(start.x, start.y),
                        end = androidx.compose.ui.geometry.Offset(end.x, end.y),
                        strokeWidth = strokeWidth
                    )
                    drawCircle(color = Color.Green, radius = 16.dp.toPx(), center = androidx.compose.ui.geometry.Offset(start.x, start.y))
                    drawCircle(color = Color.Red, radius = 16.dp.toPx(), center = androidx.compose.ui.geometry.Offset(end.x, end.y))
                }
            }
        }
        if (state.currentMode == com.example.OverlayService.ACTION_START_SINGLE) {
            val placedPoint = state.singlePlacedPoint
            if (placedPoint != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(placedPoint.x.roundToInt() - 24, placedPoint.y.roundToInt() - 24) }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                }
            } else {
                Text(
                    text = "Tap anywhere to place the tap point",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                )
            }
        }
        
        if (state.showVisualIndicators && activePoints.isNotEmpty()) {
            for (point in activePoints) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(point.x.roundToInt() - 16, point.y.roundToInt() - 16) }
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Yellow.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}

@Composable
fun ControlPanelContent(
    state: OverlayState,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    var panelWidth by remember { mutableStateOf(240.dp) }
    var panelHeight by remember { mutableStateOf(350.dp) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val playbackState by (com.example.service.AutoClickerService.instance?.playbackState ?: kotlinx.coroutines.flow.MutableStateFlow(com.example.service.AutoClickerService.State.IDLE)).collectAsState()
    val playbackProgress by (com.example.service.AutoClickerService.instance?.playbackProgress ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()

    Card(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        if (!isExpanded) {
            TooltipIconButton(text = "Expand Panel", onClick = { isExpanded = true }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Expand") // Placeholder icon
            }
        } else {
            Box(modifier = Modifier.size(panelWidth, panelHeight)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp, end = 24.dp) // padding for resize handle
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val title = when (state.currentMode) {
                        com.example.OverlayService.ACTION_START_PLAYBACK -> "Playback"
                        com.example.OverlayService.ACTION_START_SINGLE -> "Single Tap"
                        com.example.OverlayService.ACTION_START_RECORDER -> "Macro Recorder"
                        else -> "Builder Controls"
                    }
                    Text(title, fontWeight = FontWeight.Bold)
                    
                    if (playbackState != com.example.service.AutoClickerService.State.IDLE) {
                        Text(
                            text = when (playbackState) {
                                com.example.service.AutoClickerService.State.PLAYING -> "Playing..."
                                com.example.service.AutoClickerService.State.PAUSED -> "Paused"
                                else -> "Ready"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (playbackProgress.isNotEmpty()) {
                            Text(playbackProgress, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    if (state.currentMode == com.example.OverlayService.ACTION_START_RECORDER) {
                        if (state.isRecording) {
                            Text("Recording in progress...", color = Color.Red)
                            Text("Events captured: ${state.recordedEvents.size}")
                            Button(onClick = {
                                state.isRecording = false
                                state.onInterceptToggles?.invoke(false)
                            }) {
                                Text("Stop Recording")
                            }
                        } else {
                            if (state.recordedEvents.isNotEmpty()) {
                                Text("Recorded ${state.recordedEvents.size} events")
                                Button(onClick = {
                                    val scriptData = ScriptData(events = state.recordedEvents)
                                    val jsonString = Json.encodeToString(scriptData)
                                    val entity = com.example.data.ScriptEntity(
                                        name = "Recorded Script ${System.currentTimeMillis()}",
                                        gesturesJson = jsonString
                                    )
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        com.example.data.AppDatabase.getDatabase(context).scriptDao().insertScript(entity)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "Script Saved!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text("Save Script")
                                }
                            }
                            Button(onClick = {
                                state.recordedEvents = emptyList()
                                state.recordingStartTime = 0L
                                state.isRecording = true
                                state.onInterceptToggles?.invoke(true)
                            }) {
                                Text("Start Recording")
                            }
                        }
                    }
                    
                    if (state.currentMode == com.example.OverlayService.ACTION_START_SINGLE) {
                        OutlinedTextField(
                            value = state.singleIntervalMs.toString(),
                            onValueChange = { state.singleIntervalMs = it.toLongOrNull() ?: 0L },
                            label = { Text("Interval (ms)") },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                        OutlinedTextField(
                            value = state.singleTapCount.toString(),
                            onValueChange = { state.singleTapCount = it.toIntOrNull() ?: 0 },
                            label = { Text("Count (0=inf)") },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                        OutlinedTextField(
                            value = state.singleJitterMs.toString(),
                            onValueChange = { state.singleJitterMs = it.toLongOrNull() ?: 0L },
                            label = { Text("Jitter (±ms)") },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                        if (state.singlePlacedPoint != null) {
                            Text(
                                text = "Point placed at (${state.singlePlacedPoint!!.x.toInt()}, ${state.singlePlacedPoint!!.y.toInt()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = {
                                state.singlePlacedPoint = null
                                android.widget.Toast.makeText(context, "Tap on screen to place point", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        ) {
                            Text("Place Tap Point")
                        }
                    }

                    if (state.currentMode == com.example.OverlayService.ACTION_START_BUILDER || state.currentMode == com.example.OverlayService.ACTION_START_MULTI) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Simultaneous:", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.scriptMode == "simultaneous",
                                onCheckedChange = { state.scriptMode = if (it) "simultaneous" else "sequential" }
                            )
                        }
                        Row {
                            TooltipIconButton(text = "Add Tap", onClick = { state.startAddingPoint("tap") }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Tap")
                            }
                            TooltipIconButton(text = "Add Swipe", onClick = { state.startAddingPoint("swipe") }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Swipe")
                            }
                        }
                        OutlinedTextField(
                            value = state.currentDelayMs.toString(),
                            onValueChange = { state.currentDelayMs = it.toLongOrNull() ?: 0L },
                            label = { Text("Delay (ms)") },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TooltipIconButton(text = "Undo", onClick = {
                                if (state.events.isNotEmpty()) {
                                    state.events = state.events.dropLast(1)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                            }
                            if (state.events.isNotEmpty()) {
                                TooltipIconButton(text = "Test Last Step", onClick = {
                                    AutoClickerService.instance?.playScript(ScriptData(events = listOf(state.events.last()), mode = state.scriptMode))
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Test Last Step")
                                }
                            }
                            TooltipIconButton(text = "Save Script", onClick = {
                                val finalEvents = if (!state.isPremium && state.events.size > 3) {
                                    android.widget.Toast.makeText(context, "Free tier cap: Max 3 steps saved.", android.widget.Toast.LENGTH_SHORT).show()
                                    state.events.take(3)
                                } else {
                                    state.events
                                }
                                val scriptData = ScriptData(events = finalEvents, mode = state.scriptMode)
                                val jsonString = Json.encodeToString(scriptData)
                                val entity = com.example.data.ScriptEntity(
                                    name = "Script ${System.currentTimeMillis()}",
                                    gesturesJson = jsonString
                                )
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    com.example.data.AppDatabase.getDatabase(context).scriptDao().insertScript(entity)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Saved!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    }
                    
                    Row {
                        TooltipIconButton(text = "Play", onClick = { 
                            val instance = AutoClickerService.instance
                            if (instance == null) {
                                android.util.Log.e("AutoClicker", "AccessibilityService instance is null! Cannot play.")
                                return@TooltipIconButton
                            }
                            if (state.currentMode == com.example.OverlayService.ACTION_START_SINGLE) {
                                val pt = state.singlePlacedPoint
                                if (pt == null) {
                                    android.widget.Toast.makeText(context, "Tap on screen to place a point first!", android.widget.Toast.LENGTH_SHORT).show()
                                    return@TooltipIconButton
                                }
                                val event = com.example.data.GestureEvent("tap", listOf(pt), delayMs = state.singleIntervalMs, jitterMs = state.singleJitterMs)
                                instance.playScript(ScriptData(events = listOf(event)), loop = state.singleTapCount == 0, count = state.singleTapCount)
                            } else {
                                val eventsToPlay = if (state.currentMode == com.example.OverlayService.ACTION_START_PLAYBACK) {
                                    state.scriptJson?.let { jsonString ->
                                        try {
                                            Json.decodeFromString<ScriptData>(jsonString).events
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                } else {
                                    state.events
                                }
                                
                                eventsToPlay?.let { events ->
                                    instance.playScript(ScriptData(events = events, mode = state.scriptMode))
                                }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        TooltipIconButton(text = "Stop", onClick = { 
                            AutoClickerService.instance?.stopScript()
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                    }
                    
                    TooltipIconButton(text = "Collapse Panel", onClick = { isExpanded = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Collapse")
                    }
                    TooltipIconButton(text = "Close Overlay", onClick = {
                        AutoClickerService.instance?.stopScript()
                        onClose()
                    }) {
                        Text("Exit", color = MaterialTheme.colorScheme.error)
                    }
                }
                
                // Resize Handle
                Icon(
                    imageVector = Icons.Default.DragHandle, // Using DragHandle as resize handle
                    contentDescription = "Resize",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                val newWidth = (panelWidth + dragAmount.x.dp).coerceIn(200.dp, 400.dp)
                                val newHeight = (panelHeight + dragAmount.y.dp).coerceIn(200.dp, 600.dp)
                                panelWidth = newWidth
                                panelHeight = newHeight
                            }
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackPanel(
    state: OverlayState,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    var loopCount by remember { mutableStateOf(0) } // 0 = infinite
    var startDelay by remember { mutableStateOf(0) } // seconds
    val playbackState by (com.example.service.AutoClickerService.instance?.playbackState ?: kotlinx.coroutines.flow.MutableStateFlow(com.example.service.AutoClickerService.State.IDLE)).collectAsState()
    val playbackProgress by (com.example.service.AutoClickerService.instance?.playbackProgress ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()

    Card(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .wrapContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        if (!isExpanded) {
            TooltipIconButton(text = "Expand Playback", onClick = { isExpanded = true }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Expand Playback")
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp).width(200.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.currentScriptName ?: "Playback", fontWeight = FontWeight.Bold)
                if (playbackProgress.isNotEmpty()) {
                    Text(playbackProgress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                
                if (playbackState == com.example.service.AutoClickerService.State.IDLE) {
                    OutlinedTextField(
                        value = loopCount.toString(),
                        onValueChange = { loopCount = it.toIntOrNull() ?: 0 },
                        label = { Text("Loops (0=inf)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = startDelay.toString(),
                        onValueChange = { startDelay = it.toIntOrNull() ?: 0 },
                        label = { Text("Start Delay (sec)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Visuals:", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = state.showVisualIndicators,
                            onCheckedChange = { state.showVisualIndicators = it }
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playbackState == com.example.service.AutoClickerService.State.PLAYING || playbackState == com.example.service.AutoClickerService.State.STARTING) {
                        TooltipIconButton(text = "Pause", onClick = { com.example.service.AutoClickerService.instance?.pauseScript() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                    } else {
                        TooltipIconButton(text = "Play", onClick = { 
                            val instance = com.example.service.AutoClickerService.instance
                            if (instance == null) {
                                android.util.Log.e("AutoClicker", "AccessibilityService instance is null! Cannot play.")
                            } else {
                                if (playbackState == com.example.service.AutoClickerService.State.PAUSED) {
                                    instance.resumeScript()
                                } else {
                                    val eventsToPlay = state.scriptJson?.let { jsonString ->
                                        try {
                                            kotlinx.serialization.json.Json.decodeFromString<com.example.data.ScriptData>(jsonString).events
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    eventsToPlay?.let { events ->
                                        instance.playScript(
                                            com.example.data.ScriptData(events = events), 
                                            loop = loopCount == 0, 
                                            count = loopCount,
                                            startDelaySec = startDelay
                                        )
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                    }
                    
                    TooltipIconButton(text = "Stop", onClick = { 
                        com.example.service.AutoClickerService.instance?.stopScript()
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                Row {
                    TooltipIconButton(text = "Collapse", onClick = { isExpanded = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Collapse")
                    }
                    TooltipIconButton(text = "Exit", onClick = {
                        com.example.service.AutoClickerService.instance?.stopScript()
                        onClose()
                    }) {
                        Text("Exit", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
