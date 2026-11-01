package com.example.entertainment.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles notification action buttons without bringing the app up.
 * onReceive runs on the main thread -- anything slow goes to WorkManager.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NOT_INTERESTED -> {
                val mediaId = intent.getStringExtra(PushNotifications.EXTRA_MEDIA_ID)
                /* TODO persist once there's somewhere to put it. */
                Log.d(TAG, "Not interested in $mediaId")

                /* Action buttons don't auto-cancel like a body tap does. */
                val notificationId = intent.getIntExtra(PushNotifications.EXTRA_NOTIFICATION_ID, -1)
                if (notificationId != -1) PushNotifications.cancel(context, notificationId)
            }
        }
    }

    companion object {
        const val ACTION_NOT_INTERESTED = "com.example.entertainment.push.NOT_INTERESTED"
        private const val TAG = "NotificationAction"
    }
}
