package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R

class SystemNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "NotificationMgr"

        // Channel IDs
        const val CHANNEL_BUILD = "zarp_build"
        const val CHANNEL_AGENT = "zarp_agent"
        const val CHANNEL_MESSAGE = "zarp_message"
        const val CHANNEL_VOICE = "zarp_voice"
        const val CHANNEL_SYSTEM = "zarp_system"

        // Notification IDs
        private const val ID_BUILD_FAILED = 1001
        private const val ID_BUILD_SUCCESS = 1002
        private const val ID_AGENT_COMPLETE = 2001
        private const val ID_AGENT_PROGRESS = 2002
        private const val ID_NEW_MESSAGE = 3001
        private const val ID_VOICE_ACTIVE = 4001
        private const val ID_QUICK_ACTIONS = 5001
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class NotificationAction(
        val title: String,
        val icon: Int = android.R.drawable.ic_media_play,
        val intent: PendingIntent
    )

    data class NotificationConfig(
        val channelId: String,
        val title: String,
        val message: String,
        val priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        val autoCancel: Boolean = true,
        val ongoing: Boolean = false,
        val actions: List<NotificationAction> = emptyList(),
        val largeIcon: android.graphics.Bitmap? = null,
        val sound: Uri? = null,
        val vibrate: Boolean = false,
        val category: String? = null,
        val color: Int? = null,
        val groupKey: String? = null,
        val timeoutMs: Long? = null
    )

    // ═══════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Build Channel
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_BUILD, "Build Status", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "GitHub Actions build results"
                    enableVibration(true)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                }
            )

            // Agent Channel
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_AGENT, "Agent Tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Agent task progress and completion"
                    enableVibration(false)
                }
            )

            // Message Channel
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MESSAGE, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Incoming AI responses"
                    enableVibration(true)
                }
            )

            // Voice Channel
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_VOICE, "Voice Control", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Voice input active indicator"
                    enableVibration(false)
                    setSound(null, null)
                }
            )

            // System Channel
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SYSTEM, "System", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "System status and updates"
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }

    // ═══════════════════════════════════════════
    // Build Notifications
    // ═══════════════════════════════════════════

    fun notifyBuildFailed(
        repoName: String,
        repoUrl: String,
        errorCount: Int,
        firstError: String,
        fixIntent: PendingIntent,
        viewIntent: PendingIntent
    ) {
        show(NotificationConfig(
            channelId = CHANNEL_BUILD,
            title = "❌ Build Failed — $repoName",
            message = "$errorCount error(s): ${firstError.take(100)}...",
            priority = NotificationCompat.PRIORITY_HIGH,
            autoCancel = true,
            vibrate = true,
            category = NotificationCompat.CATEGORY_ERROR,
            actions = listOf(
                NotificationAction("🔧 Fix & Rebuild", android.R.drawable.ic_menu_manage, fixIntent),
                NotificationAction("📁 View Repo", android.R.drawable.ic_menu_view, viewIntent)
            )
        ), ID_BUILD_FAILED)
    }

    fun notifyBuildSuccess(repoName: String, repoUrl: String, viewIntent: PendingIntent) {
        show(NotificationConfig(
            channelId = CHANNEL_BUILD,
            title = "✅ Build Passed — $repoName",
            message = "All checks passed. Tap to view the repository.",
            priority = NotificationCompat.PRIORITY_DEFAULT,
            autoCancel = true,
            category = NotificationCompat.CATEGORY_STATUS,
            actions = listOf(
                NotificationAction("📁 View Repo", android.R.drawable.ic_menu_view, viewIntent)
            )
        ), ID_BUILD_SUCCESS)
    }

    // ═══════════════════════════════════════════
    // Agent Notifications
    // ═══════════════════════════════════════════

    fun notifyAgentComplete(
        taskSummary: String,
        repoUrl: String?,
        filesCreated: Int,
        viewIntent: PendingIntent
    ) {
        show(NotificationConfig(
            channelId = CHANNEL_AGENT,
            title = "🤖 Agent Task Complete",
            message = "$filesCreated files created. ${taskSummary.take(100)}",
            priority = NotificationCompat.PRIORITY_DEFAULT,
            autoCancel = true,
            category = NotificationCompat.CATEGORY_STATUS,
            actions = if (repoUrl != null) {
                listOf(NotificationAction("📁 View Repo", android.R.drawable.ic_menu_view, viewIntent))
            } else emptyList()
        ), ID_AGENT_COMPLETE)
    }

    fun notifyAgentProgress(
        stepDescription: String,
        currentStep: Int,
        totalSteps: Int
    ) {
        show(NotificationConfig(
            channelId = CHANNEL_AGENT,
            title = "🤖 Agent Working...",
            message = "Step $currentStep/$totalSteps: $stepDescription",
            priority = NotificationCompat.PRIORITY_LOW,
            ongoing = true,
            autoCancel = false
        ), ID_AGENT_PROGRESS)
    }

    fun dismissAgentProgress() {
        cancel(ID_AGENT_PROGRESS)
    }

    // ═══════════════════════════════════════════
    // Message Notifications
    // ═══════════════════════════════════════════

    fun notifyNewMessage(
        message: String,
        conversationTitle: String,
        replyIntent: PendingIntent? = null,
        viewIntent: PendingIntent
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💬 $conversationTitle")
            .setContentText(message.take(200))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(viewIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        // Inline reply
        if (replyIntent != null) {
            val remoteInput = RemoteInput.Builder("reply_text")
                .setLabel("Reply to Zarp...")
                .build()

            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Reply",
                    replyIntent
                ).addRemoteInput(remoteInput).build()
            )
        }

        NotificationManagerCompat.from(context).notify(ID_NEW_MESSAGE, builder.build())
    }

    // ═══════════════════════════════════════════
    // Voice Notification
    // ═══════════════════════════════════════════

    fun notifyVoiceActive(stopIntent: PendingIntent) {
        show(NotificationConfig(
            channelId = CHANNEL_VOICE,
            title = "🎤 Listening...",
            message = "Zarp is listening. Tap to stop.",
            priority = NotificationCompat.PRIORITY_LOW,
            ongoing = true,
            autoCancel = false,
            actions = listOf(
                NotificationAction("⏹ Stop", android.R.drawable.ic_media_pause, stopIntent)
            )
        ), ID_VOICE_ACTIVE)
    }

    fun dismissVoiceActive() {
        cancel(ID_VOICE_ACTIVE)
    }

    // ═══════════════════════════════════════════
    // Quick Actions (Persistent Notification)
    // ═══════════════════════════════════════════

    fun showQuickActions(
        newChatIntent: PendingIntent,
        voiceIntent: PendingIntent,
        agentIntent: PendingIntent
    ) {
        show(NotificationConfig(
            channelId = CHANNEL_SYSTEM,
            title = "Zarp",
            message = "Quick actions",
            priority = NotificationCompat.PRIORITY_MIN,
            ongoing = true,
            autoCancel = false,
            actions = listOf(
                NotificationAction("💬 Chat", android.R.drawable.ic_dialog_dialer, newChatIntent),
                NotificationAction("🎤 Voice", android.R.drawable.ic_btn_speak_now, voiceIntent),
                NotificationAction("🤖 Agent", android.R.drawable.ic_menu_manage, agentIntent)
            )
        ), ID_QUICK_ACTIONS)
    }

    // ═══════════════════════════════════════════
    // Core Methods
    // ═══════════════════════════════════════════

    private fun show(config: NotificationConfig, id: Int) {
        try {
            val builder = NotificationCompat.Builder(context, config.channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(config.title)
                .setContentText(config.message)
                .setPriority(config.priority)
                .setAutoCancel(config.autoCancel)
                .setOngoing(config.ongoing)
                .setStyle(NotificationCompat.BigTextStyle().bigText(config.message))

            if (config.largeIcon != null) {
                builder.setLargeIcon(config.largeIcon)
            }

            if (config.sound != null) {
                builder.setSound(config.sound)
            }

            if (config.vibrate) {
                builder.setVibrate(longArrayOf(0, 300, 200, 300))
            }

            if (config.category != null) {
                builder.setCategory(config.category)
            }

            if (config.color != null) {
                builder.setColor(config.color)
            }

            if (config.groupKey != null) {
                builder.setGroup(config.groupKey)
            }

            // Add actions
            config.actions.forEach { action ->
                builder.addAction(action.icon, action.title, action.intent)
            }

            // Build and show
            val notification = builder.build()

            // Timeout handling
            if (config.timeoutMs != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    cancel(id)
                }, config.timeoutMs)
            }

            NotificationManagerCompat.from(context).notify(id, notification)
            Log.d(TAG, "🔔 Notification shown: ${config.title}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show notification", e)
        }
    }

    fun cancel(id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
            Log.d(TAG, "🔕 Notification cancelled: $id")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel notification", e)
        }
    }

    fun cancelAll() {
        try {
            NotificationManagerCompat.from(context).cancelAll()
            Log.d(TAG, "🔕 All notifications cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel all notifications", e)
        }
    }
}
