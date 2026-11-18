package com.example.entertainment

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.entertainment.push.PushNotifications
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EntertainmentApplication : Application(), Configuration.Provider {

    // Hilt's own worker factory -- without handing this to WorkManager,
    // it'd fall back to its no-arg default factory and crash trying to
    // construct a Hilt-injected worker like HomeSyncWorker.
    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        /* Before any push can arrive -- posting to a missing channel is silently dropped on 26+. */
        PushNotifications.createChannel(this)

        /* onNewToken only fires when the token changes, so log it on every debug launch too. */
        if (BuildConfig.DEBUG) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { Log.d("PushService", "FCM token: $it") }
        }
    }
}
