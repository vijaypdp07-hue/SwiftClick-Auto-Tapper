package com.example.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
                if (state.isAddingPoint) {
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

                    if (state.currentMode == com.example.OverlayService.ACTION_START_BUILDER) {
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
                                    AutoClickerService.instance?.playScript(ScriptData(events = listOf(state.events.last())))
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Test Last Step")
                                }
                            }
                            TooltipIconButton(text = "Save Script", onClick = {
                                if (state.events.size > 3) {
                                    android.widget.Toast.makeText(context, "Free tier cap: Max 3 steps. Truncating.", android.widget.Toast.LENGTH_SHORT).show()
                                    // TODO: PREMIUM_GATE
                                }
                                val scriptData = ScriptData(events = state.events.take(3)) // apply cap
                                val jsonString = Json.encodeToString(scriptData)
                                val entity = com.example.data.ScriptEntity(
                                    name = "Script ${System.currentTimeMillis()}",
                                    gesturesJson = jsonString
                                )
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    com.example.data.AppDatabase.getDatabase(context).scriptDao().insertScript(entity)
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
                                    instance.playScript(ScriptData(events = events))
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
                modifier = Modifier.padding(16.dp).wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.currentScriptName ?: "Playback", fontWeight = FontWeight.Bold)
                if (playbackProgress.isNotEmpty()) {
                    Text(playbackProgress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playbackState == com.example.service.AutoClickerService.State.PLAYING) {
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
                                        instance.playScript(com.example.data.ScriptData(events = events), loop = true)
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
