package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutomationSafetyManager(private val context: Context) {

    companion object {
        private const val TAG = "AutoSafety"
        private const val CHANNEL_ID = "zarp_automation_safety"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_RESUME = "com.example.AUTOMATION_RESUME"
        private const val ACTION_CANCEL = "com.example.AUTOMATION_CANCEL"
        private const val TRIPLE_TAP_WINDOW_MS = 800L
        private const val OVERLAY_ALPHA = 0.02f
    }

    data class AutomationState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val currentStep: Int = 0,
        val totalSteps: Int = 0,
        val description: String = ""
    )

    // ═══════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════

    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    private var onResumeCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var resumeJob: kotlinx.coroutines.Job? = null

    // ═══════════════════════════════════════════
    // Triple‑Tap Overlay (Invisible)
    // ═══════════════════════════════════════════

    private var safetyOverlay: View? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private var lastTapTime = 0L
    private var overlayDismissRunnable: Runnable? = null

    // ═══════════════════════════════════════════
    // Broadcast Receiver for Notification Actions
    // ═══════════════════════════════════════════

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_RESUME -> {
                    Log.d(TAG, "▶️ Resume broadcast received")
                    resumeAutomation()
                }
                ACTION_CANCEL -> {
                    Log.d(TAG, "⏹️ Cancel broadcast received")
                    cancelAutomation()
                }
            }
        }
    }

    init {
        createNotificationChannel()
        context.registerReceiver(actionReceiver, IntentFilter().apply {
            addAction(ACTION_RESUME)
            addAction(ACTION_CANCEL)
        }, Context.RECEIVER_NOT_EXPORTED)
    }

    // ═══════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════

    fun startMonitoring(
        onResume: () -> Unit,
        onCancel: () -> Unit
    ) {
        onResumeCallback = onResume
        onCancelCallback = onCancel
        _state.value = AutomationState(isRunning = true, isPaused = false)
        showInvisibleOverlay()
        showRunningNotification()
        Log.d(TAG, "🛡️ Safety monitoring started")
    }

    fun updateProgress(currentStep: Int, totalSteps: Int, description: String) {
        _state.value = _state.value.copy(currentStep = currentStep, totalSteps = totalSteps, description = description)
        updateNotification()
    }

    fun stopMonitoring() {
        onResumeCallback = null
        onCancelCallback = null
        _state.value = AutomationState()
        hideInvisibleOverlay()
        cancelNotification()
        Log.d(TAG, "🛡️ Safety monitoring stopped")
    }

    fun cleanup() {
        stopMonitoring()
        try { context.unregisterReceiver(actionReceiver) } catch (e: Exception) {}
    }

    // ═══════════════════════════════════════════
    // Invisible Overlay (Triple‑Tap Detection)
    // ═══════════════════════════════════════════

    private fun showInvisibleOverlay() {
        if (safetyOverlay != null) return

        safetyOverlay = View(context).apply {
            setBackgroundColor(0x05FFFFFF.toInt())
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < TRIPLE_TAP_WINDOW_MS) {
                        tapCount++
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now

                    if (tapCount >= 3) {
                        tapCount = 0
                        handler.post { handleTripleTap() }
                    }

                    overlayDismissRunnable?.let { handler.removeCallbacks(it) }
                    overlayDismissRunnable = Runnable {
                        tapCount = 0
                    }
                    handler.postDelayed(overlayDismissRunnable!!, TRIPLE_TAP_WINDOW_MS * 2)
                }
                false // Pass through — does NOT block touch
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // ← Passes touches through
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,  // ← Catches edge touches
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = OVERLAY_ALPHA
        }

        windowManager.addView(safetyOverlay, params)
        Log.d(TAG, "👻 Invisible overlay added (triple‑tap to pause)")
    }

    private fun hideInvisibleOverlay() {
        safetyOverlay?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        safetyOverlay = null
        Log.d(TAG, "👻 Invisible overlay removed")
    }

    private fun handleTripleTap() {
        if (_state.value.isPaused) {
            resumeAutomation()
        } else {
            pauseAutomation()
        }
    }

    // ═══════════════════════════════════════════
    // Pause / Resume / Cancel
    // ═══════════════════════════════════════════

    private fun pauseAutomation() {
        _state.value = _state.value.copy(isPaused = true)
        showPausedNotification()
        Log.d(TAG, "⏸️ Automation paused by triple‑tap")
    }

    private fun resumeAutomation() {
        _state.value = _state.value.copy(isPaused = false)
        showRunningNotification()
        onResumeCallback?.invoke()
        Log.d(TAG, "▶️ Automation resumed")
    }

    private fun cancelAutomation() {
        _state.value = AutomationState()
        hideInvisibleOverlay()
        cancelNotification()
        onCancelCallback?.invoke()
        Log.d(TAG, "⏹️ Automation cancelled")
    }

    // ═══════════════════════════════════════════
    // Notifications
    // ═══════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Automation Safety",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows pause/resume controls during automation"
                setSound(null, null)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showRunningNotification() {
        val notification = buildNotification(
            title = "🎮 Automation Running",
            text = "Step ${_state.value.currentStep}/${_state.value.totalSteps}: ${_state.value.description.take(50)}",
            ongoing = true,
            actions = listOf(
                NotificationAction("⏸️ Pause", ACTION_RESUME) { resumeAutomation() }
            )
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showPausedNotification() {
        val resumeIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getBroadcast(
            context, 1, Intent(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("⏸️ Automation Paused")
            .setContentText("Tap Resume to continue or Cancel to stop")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, "▶️ Resume", resumeIntent)
            .addAction(android.R.drawable.ic_delete, "⏹️ Cancel", cancelIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        if (_state.value.isPaused) {
            showPausedNotification()
        } else {
            showRunningNotification()
        }
    }

    private fun cancelNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        actions: List<NotificationAction> = emptyList()
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        actions.forEach { action ->
            val intent = PendingIntent.getBroadcast(
                context, action.title.hashCode(), Intent(action.action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, action.title, intent)
        }

        return builder.build()
    }

    data class NotificationAction(
        val title: String,
        val action: String,
        val callback: () -> Unit
    )
}
