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

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Auto Clicker")
            .setContentText("Script is playing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
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
        startForegroundNotification()
        playbackJob = serviceScope.launch {
            var iterations = 0
            do {
                for (event in scriptData.events) {
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
                }
                iterations++
            } while ((loop || (count > 0 && iterations < count)) && isActive)
            _playbackState.value = State.IDLE
            stopForegroundNotification()
        }
    }

    fun stopScript() {
        playbackJob?.cancel()
        playbackJob = null
        _playbackState.value = State.IDLE
        stopForegroundNotification()
    }

    private suspend fun dispatchGestureEvent(event: com.example.data.GestureEvent) {
        if (event.type == "tap" && event.points.isNotEmpty()) {
            val point = event.points[0]
            val path = Path().apply {
                moveTo(point.x, point.y)
            }
            val stroke = StrokeDescription(path, 0, 50)
            val builder = GestureDescription.Builder()
            builder.addStroke(stroke)
            dispatchGesture(builder.build(), null, null)
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
            dispatchGesture(builder.build(), null, null)
            delay(duration) // Wait for swipe to finish before next step
        }
    }
}
