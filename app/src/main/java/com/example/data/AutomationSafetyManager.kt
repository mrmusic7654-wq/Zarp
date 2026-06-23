package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        private const val ACTION_PAUSE = "com.example.AUTOMATION_PAUSE"
        private const val ACTION_RESUME = "com.example.AUTOMATION_RESUME"
        private const val ACTION_CANCEL = "com.example.AUTOMATION_CANCEL"
        private const val ACTION_SKIP = "com.example.AUTOMATION_SKIP_STEP"
        private const val AUTO_RESUME_DELAY_MS = 30000L
    }

    // ═══════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════

    data class AutomationState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val currentStep: Int = 0,
        val totalSteps: Int = 0,
        val description: String = "",
        val startTimeMs: Long = 0L,
        val pausedAtMs: Long = 0L,
        val completedSteps: Int = 0,
        val failedSteps: Int = 0
    ) {
        val elapsedMs: Long get() = if (startTimeMs == 0L) 0L else System.currentTimeMillis() - startTimeMs
        val progressPercent: Int get() = if (totalSteps == 0) 0 else (currentStep * 100) / totalSteps
    }

    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    // ═══════════════════════════════════════════
    // Callbacks
    // ═══════════════════════════════════════════

    var onPauseCallback: (() -> Unit)? = null
    var onResumeCallback: (() -> Unit)? = null
    var onCancelCallback: (() -> Unit)? = null
    var onSkipStepCallback: (() -> Unit)? = null

    // ═══════════════════════════════════════════
    // Auto‑resume handler
    // ═══════════════════════════════════════════

    private val handler = Handler(Looper.getMainLooper())
    private var autoResumeRunnable: Runnable? = null

    // ═══════════════════════════════════════════
    // Broadcast Receiver
    // ═══════════════════════════════════════════

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE -> pauseAutomation()
                ACTION_RESUME -> resumeAutomation()
                ACTION_CANCEL -> cancelAutomation()
                ACTION_SKIP -> skipCurrentStep()
            }
        }
    }

    init {
        createNotificationChannel()
        context.registerReceiver(actionReceiver, IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
            addAction(ACTION_CANCEL)
            addAction(ACTION_SKIP)
        }, Context.RECEIVER_NOT_EXPORTED)
    }

    // ═══════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════

    fun startMonitoring(
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
        onCancel: () -> Unit = {},
        onSkipStep: () -> Unit = {}
    ) {
        onPauseCallback = onPause
        onResumeCallback = onResume
        onCancelCallback = onCancel
        onSkipStepCallback = onSkipStep
        _state.value = AutomationState(
            isRunning = true,
            isPaused = false,
            startTimeMs = System.currentTimeMillis()
        )
        showRunningNotification()
        Log.d(TAG, "🛡️ Safety monitoring started")
    }

    fun updateProgress(currentStep: Int, totalSteps: Int, description: String, completedSteps: Int = 0, failedSteps: Int = 0) {
        _state.value = _state.value.copy(
            currentStep = currentStep,
            totalSteps = totalSteps,
            description = description,
            completedSteps = completedSteps,
            failedSteps = failedSteps
        )
        updateNotification()
    }

    fun pauseAutomation() {
        if (!_state.value.isRunning) return
        _state.value = _state.value.copy(isPaused = true, pausedAtMs = System.currentTimeMillis())
        showPausedNotification()
        onPauseCallback?.invoke()

        // Schedule auto‑resume after 30 seconds if user doesn't respond
        scheduleAutoResume()
        Log.d(TAG, "⏸️ Automation paused")
    }

    fun resumeAutomation() {
        if (!_state.value.isRunning) return
        cancelAutoResume()
        _state.value = _state.value.copy(isPaused = false)
        showRunningNotification()
        onResumeCallback?.invoke()
        Log.d(TAG, "▶️ Automation resumed")
    }

    fun cancelAutomation() {
        cancelAutoResume()
        _state.value = AutomationState()
        cancelNotification()
        onCancelCallback?.invoke()
        Log.d(TAG, "⏹️ Automation cancelled")
    }

    fun skipCurrentStep() {
        if (!_state.value.isRunning) return
        onSkipStepCallback?.invoke()
        Log.d(TAG, "⏭️ Skipping current step")
    }

    fun stopMonitoring() {
        cancelAutoResume()
        onPauseCallback = null
        onResumeCallback = null
        onCancelCallback = null
        onSkipStepCallback = null
        _state.value = AutomationState()
        cancelNotification()
        Log.d(TAG, "🛡️ Safety monitoring stopped")
    }

    fun cleanup() {
        stopMonitoring()
        try { context.unregisterReceiver(actionReceiver) } catch (e: Exception) {}
    }

    // ═══════════════════════════════════════════
    // Auto‑resume logic
    // ═══════════════════════════════════════════

    private fun scheduleAutoResume() {
        cancelAutoResume()
        autoResumeRunnable = Runnable {
            Log.d(TAG, "⏰ Auto‑resume triggered after ${AUTO_RESUME_DELAY_MS}ms")
            resumeAutomation()
        }
        handler.postDelayed(autoResumeRunnable!!, AUTO_RESUME_DELAY_MS)
    }

    private fun cancelAutoResume() {
        autoResumeRunnable?.let { handler.removeCallbacks(it) }
        autoResumeRunnable = null
    }

    // ═══════════════════════════════════════════
    // Notifications
    // ═══════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Automation Safety", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Pause, resume, skip, or cancel automation"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showRunningNotification() {
        val state = _state.value
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎮 Automation Running")
            .setContentText("Step ${state.currentStep}/${state.totalSteps}: ${state.description.take(40)}")
            .setSubText("${state.progressPercent}% complete • Tap to control")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createOpenAppIntent())
            .addAction(android.R.drawable.ic_media_pause, "⏸️ Pause", createPendingIntent(ACTION_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "⏭️ Skip", createPendingIntent(ACTION_SKIP))
            .addAction(android.R.drawable.ic_delete, "⏹️ Cancel", createPendingIntent(ACTION_CANCEL))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showPausedNotification() {
        val state = _state.value
        val pausedDuration = System.currentTimeMillis() - state.pausedAtMs
        val pausedSeconds = pausedDuration / 1000

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("⏸️ Automation Paused")
            .setContentText("Paused for ${pausedSeconds}s • Step ${state.currentStep}/${state.totalSteps}")
            .setSubText("Auto‑resume in ${(AUTO_RESUME_DELAY_MS - pausedDuration) / 1000}s")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createOpenAppIntent())
            .addAction(android.R.drawable.ic_media_play, "▶️ Resume", createPendingIntent(ACTION_RESUME))
            .addAction(android.R.drawable.ic_media_next, "⏭️ Skip", createPendingIntent(ACTION_SKIP))
            .addAction(android.R.drawable.ic_delete, "⏹️ Cancel", createPendingIntent(ACTION_CANCEL))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        if (_state.value.isPaused) showPausedNotification() else showRunningNotification()
    }

    private fun cancelNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action)
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
