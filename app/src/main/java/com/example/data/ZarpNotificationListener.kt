package com.example.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class ZarpNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        var instance: ZarpNotificationListener? = null
            private set
        var onNotificationReceived: ((StatusBarNotification) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "🔔 Notification listener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            onNotificationReceived?.invoke(it)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
