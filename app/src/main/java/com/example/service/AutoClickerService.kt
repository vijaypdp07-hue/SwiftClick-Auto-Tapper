package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import com.example.data.ScriptData

class AutoClickerService : AccessibilityService() {

    companion object {
        var instance: AutoClickerService? = null
            private set
        private const val NOTIFICATION_CHANNEL_ID = "autoclicker_playback"
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackJob: Job? = null
    enum class State { IDLE, PLAYING, PAUSED }
    private val _playbackState = kotlinx.coroutines.flow.MutableStateFlow(State.IDLE)
    val playbackState: kotlinx.coroutines.flow.StateFlow<State> = _playbackState.asStateFlow()
    
    private val pauseChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun pauseScript() {
        if (_playbackState.value == State.PLAYING) {
            _playbackState.value = State.PAUSED
        }
    }

    fun resumeScript() {
        if (_playbackState.value == State.PAUSED) {
            _playbackState.value = State.PLAYING
            pauseChannel.trySend(Unit)
        }
    }

    private suspend fun checkPause() {
        if (_playbackState.value == State.PAUSED) {
            pauseChannel.receive()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Script Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when a script is actively running"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val _playbackProgress = kotlinx.coroutines.flow.MutableStateFlow("")
    val playbackProgress: kotlinx.coroutines.flow.StateFlow<String> = _playbackProgress.asStateFlow()

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Auto Clicker")
            .setContentText("Script is playing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateForegroundNotification(progressText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Auto Clicker")
            .setContentText("Playing: $progressText")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopScript()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, we only dispatch gestures
    }

    override fun onInterrupt() {
        stopScript()
    }

    fun playScript(scriptData: ScriptData, loop: Boolean = false, count: Int = 0) {
        if (_playbackState.value != State.IDLE) return
        _playbackState.value = State.PLAYING
        _playbackProgress.value = "Starting..."
        startForegroundNotification()
        playbackJob = serviceScope.launch {
            var iterations = 0
            do {
                var stepIndex = 1
                val totalSteps = scriptData.events.size
                for (event in scriptData.events) {
                    val progressText = if (loop) "Loop ${iterations + 1} / ∞, Step $stepIndex / $totalSteps" else "Loop ${iterations + 1} / $count, Step $stepIndex / $totalSteps"
                    _playbackProgress.value = progressText
                    updateForegroundNotification(progressText)
                    checkPause()
                    if (!isActive) break
                    var currentDelay = event.delayMs
                    event.jitterMs?.let { jitter ->
                        if (jitter > 0) {
                            val range = -jitter..jitter
                            currentDelay += range.random()
                            if (currentDelay < 0) currentDelay = 0
                        }
                    }
                    var remainingDelay = currentDelay
                    while (remainingDelay > 0) {
                        checkPause()
                        if (!isActive) break
                        val step = minOf(50L, remainingDelay)
                        delay(step)
                        remainingDelay -= step
                    }
                    checkPause()
                    if (!isActive) break
                    dispatchGestureEvent(event)
                    stepIndex++
                }
                iterations++
            } while ((loop || (count > 0 && iterations < count)) && isActive)
            _playbackState.value = State.IDLE
            _playbackProgress.value = ""
            stopForegroundNotification()
        }
    }

    fun stopScript() {
        playbackJob?.cancel()
        playbackJob = null
        _playbackState.value = State.IDLE
        _playbackProgress.value = ""
        stopForegroundNotification()
    }

    private suspend fun dispatchGestureEvent(event: com.example.data.GestureEvent) {
        android.util.Log.d("AutoClicker", "Dispatching event: $event")
        var success = false
        if (event.type == "tap" && event.points.isNotEmpty()) {
            val point = event.points[0]
            val path = Path().apply {
                moveTo(point.x, point.y)
            }
            // Use 50ms duration, ensure > 0
            val stroke = StrokeDescription(path, 0, 50)
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            success = dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    android.util.Log.d("AutoClicker", "Tap completed successfully")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    android.util.Log.d("AutoClicker", "Tap cancelled")
                }
            }, null)
            android.util.Log.d("AutoClicker", "dispatchGesture (tap) returned: $success")
            delay(50)
        } else if (event.type == "swipe" && event.points.size >= 2) {
            val start = event.points[0]
            val end = event.points[1]
            val duration = event.durationMs ?: 300L
            val path = Path().apply {
                moveTo(start.x, start.y)
                lineTo(end.x, end.y)
            }
            val stroke = StrokeDescription(path, 0, duration)
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            success = dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    android.util.Log.d("AutoClicker", "Swipe completed successfully")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    android.util.Log.d("AutoClicker", "Swipe cancelled")
                }
            }, null)
            android.util.Log.d("AutoClicker", "dispatchGesture (swipe) returned: $success")
            delay(duration) // Wait for swipe to finish before next step
        }
    }
}
