package com.example.entertainment.pos.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PosSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager get() = WorkManager.getInstance(context)

    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<PosSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /* Not KEEP -- a run already mid-flight has read its PENDING list and would miss
           this sale. APPEND queues one more pass behind it; extra passes are no-ops. */
        workManager.enqueueUniqueWork(PosSyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** e.g. "ENQUEUED (attempt 2)" for the newest run in the chain, for Sync Lab. */
    fun observeStatus(): Flow<String> =
        workManager.getWorkInfosForUniqueWorkFlow(PosSyncWorker.UNIQUE_WORK_NAME).map { infos ->
            val latest = infos.lastOrNull() ?: return@map "No sync scheduled yet"
            val attempt = if (latest.runAttemptCount > 0) " (attempt ${latest.runAttemptCount + 1})" else ""
            val queued = infos.count { !it.state.isFinished }
            "${latest.state}$attempt · $queued in chain"
        }

    private companion object {
        const val BACKOFF_SECONDS = 10L
    }
}
