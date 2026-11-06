package com.example.entertainment.push

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.presentation.presentation.sync.HomeSyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Entry point for FCM. Play Services gets the push and starts this service,
 * even when the app process is dead.
 *
 * - data-only push: always delivered here, foreground or background.
 * - notification push: here only while foregrounded; otherwise the system
 *   draws it and this never runs.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        /* No backend yet -- grab it from logcat for console test sends. */
        Log.d(TAG, "FCM token: $token")
    }

    /* Runs on a background thread, but only gets ~10s before the process can be killed. */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        if (data[KEY_TYPE] == TYPE_SYNC) {
            enqueueSync()
            return
        }

        val title = message.notification?.title ?: data[KEY_TITLE] ?: return
        val body = message.notification?.body ?: data[KEY_BODY]
        PushNotifications.show(this, title, body, data[PushNotifications.EXTRA_MEDIA_ID])
    }

    /* Silent push -> hand off to WorkManager, which outlives this service. */
    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<HomeSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniqueWork(PUSH_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val TAG = "PushService"
        const val KEY_TYPE = "type"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val TYPE_SYNC = "sync"
        const val PUSH_SYNC_WORK_NAME = "push_home_sync"
    }
}
