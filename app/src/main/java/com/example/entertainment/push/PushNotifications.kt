package com.example.entertainment.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.entertainment.MainActivity
import com.example.entertainment.R

/**
 * Builds and posts the notifications we draw ourselves -- i.e. everything that
 * reaches [PushService.onMessageReceived]. Notification-type pushes that arrive
 * while the app is backgrounded never get here; the system draws those.
 */
object PushNotifications {

    const val CHANNEL_ID = "general"

    /* Same key the backend puts in the FCM `data` block, so system-drawn
       notifications (which copy `data` into the launch intent) land the same way. */
    const val EXTRA_MEDIA_ID = "mediaId"
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    /* No-op if it already exists, so safe to call on every app start. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String?, mediaId: String?) {
        if (!canPost(context)) return

        val notificationId = mediaId?.hashCode() ?: System.currentTimeMillis().toInt()

        /* Tap -> MainActivity. SINGLE_TOP|CLEAR_TOP reuses the running activity
           (onNewIntent) instead of stacking a second copy. */
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MEDIA_ID, mediaId)
        }
        /* requestCode has to differ per notification, otherwise UPDATE_CURRENT
           makes them all share one PendingIntent and the last mediaId wins. */
        val tapPending = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /* Action button -> broadcast, so it's handled without opening the app. */
        val notInterestedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOT_INTERESTED
            putExtra(EXTRA_MEDIA_ID, mediaId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val notInterestedPending = PendingIntent.getBroadcast(
            context,
            notificationId,
            notInterestedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapPending)
            .addAction(0, "Not interested", notInterestedPending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /* 13+ needs the runtime grant; below that the user can still block us in settings. */
    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
