package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.overlay.ControlPanelContent
import com.example.ui.overlay.PlaybackPanel
import com.example.ui.overlay.WorkspaceContent
import com.example.data.ScriptEntity
import com.example.ui.overlay.OverlayState

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_START_SINGLE = "START_SINGLE"
        const val ACTION_START_MULTI = "START_MULTI"
        const val ACTION_START_SWIPE = "START_SWIPE"
        const val ACTION_START_BUILDER = "START_BUILDER"
        const val ACTION_START_PLAYBACK = "START_PLAYBACK"
        
        const val ACTION_START_RECORDER = "START_RECORDER"
        
        const val EXTRA_SCRIPT_JSON = "EXTRA_SCRIPT_JSON"
        const val EXTRA_SCRIPT_NAME = "EXTRA_SCRIPT_NAME"
        const val EXTRA_IS_PREMIUM = "EXTRA_IS_PREMIUM"

        fun startSinglePointMode(context: Context, isPremium: Boolean = false) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_START_SINGLE).putExtra(EXTRA_IS_PREMIUM, isPremium))
        }
        fun startMultiPointMode(context: Context, isPremium: Boolean = false) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_START_MULTI).putExtra(EXTRA_IS_PREMIUM, isPremium))
        }
        fun startSwipeMode(context: Context, isPremium: Boolean = false) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_START_SWIPE).putExtra(EXTRA_IS_PREMIUM, isPremium))
        }
        fun startManualBuilder(context: Context, isPremium: Boolean = false) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_START_BUILDER).putExtra(EXTRA_IS_PREMIUM, isPremium))
        }
        fun startRecorder(context: Context, isPremium: Boolean = false) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_START_RECORDER).putExtra(EXTRA_IS_PREMIUM, isPremium))
        }
        fun startScriptPlayback(context: Context, script: ScriptEntity, isPremium: Boolean = false) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START_PLAYBACK
                putExtra(EXTRA_SCRIPT_JSON, script.gesturesJson)
                putExtra(EXTRA_SCRIPT_NAME, script.name)
                putExtra(EXTRA_IS_PREMIUM, isPremium)
            }
            context.startService(intent) 
        }
    }

    private lateinit var windowManager: WindowManager
    private var controlView: ComposeView? = null
    private var workspaceView: ComposeView? = null
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    
    private val overlayState = OverlayState()

    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        overlayState.onInterceptToggles = { intercept ->
            overlayState.isWorkspaceVisible = intercept
            updateWorkspaceTouchable(intercept)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        val action = intent?.action ?: ACTION_START_SINGLE
        overlayState.currentMode = action
        overlayState.scriptJson = intent?.getStringExtra(EXTRA_SCRIPT_JSON)
        overlayState.currentScriptName = intent?.getStringExtra(EXTRA_SCRIPT_NAME)
        overlayState.isPremium = intent?.getBooleanExtra(EXTRA_IS_PREMIUM, false) ?: false
        showOverlays()
        
        return START_NOT_STICKY
    }

    private fun showOverlays() {
        removeOverlays()
        
        // 1. Workspace View (Full screen, usually not touchable)
        workspaceView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                WorkspaceContent(overlayState)
            }
        }
        val workspaceParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(workspaceView, workspaceParams)
        
        // 2. Control View (Wrap content, draggable, always touchable)
        controlView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                if (overlayState.currentMode == ACTION_START_PLAYBACK) {
                    PlaybackPanel(
                        state = overlayState,
                        onClose = { stopSelf() },
                        onDrag = { dx, dy ->
                            val params = layoutParams as WindowManager.LayoutParams
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(this, params)
                        },
                        onDragEnd = {
                            val params = layoutParams as WindowManager.LayoutParams
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val center = params.x + width / 2
                            if (center < screenWidth / 2) {
                                params.x = 0
                            } else {
                                params.x = screenWidth - width
                            }
                            windowManager.updateViewLayout(this, params)
                        }
                    )
                } else {
                    ControlPanelContent(
                        state = overlayState,
                        onClose = { stopSelf() },
                        onDrag = { dx, dy ->
                            val params = layoutParams as WindowManager.LayoutParams
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(this, params)
                        },
                        onDragEnd = {
                            val params = layoutParams as WindowManager.LayoutParams
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val center = params.x + width / 2
                            if (center < screenWidth / 2) {
                                params.x = 0
                            } else {
                                params.x = screenWidth - width
                            }
                            windowManager.updateViewLayout(this, params)
                        }
                    )
                }
            }
        }
        val controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        windowManager.addView(controlView, controlParams)
    }
    
    private fun updateWorkspaceTouchable(touchable: Boolean) {
        val workspace = workspaceView ?: return
        val wParams = workspace.layoutParams as WindowManager.LayoutParams
        if (touchable) {
            wParams.flags = wParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            wParams.flags = wParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(workspace, wParams)

        val control = controlView ?: return
        val cParams = control.layoutParams as WindowManager.LayoutParams
        if (touchable) {
            cParams.flags = cParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            cParams.flags = cParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager.updateViewLayout(control, cParams)
    }

    private fun removeOverlays() {
        workspaceView?.let { windowManager.removeView(it) }
        controlView?.let { windowManager.removeView(it) }
        workspaceView = null
        controlView = null
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        removeOverlays()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
