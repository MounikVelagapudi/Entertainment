package com.example.entertainment.pos.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.entertainment.pos.data.OfflineFirstPosRepository
import com.example.entertainment.pos.data.inMemoryPosDatabase
import com.example.entertainment.pos.data.remote.MockPosInterceptor
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.RecordingInterceptor
import com.example.entertainment.pos.fakes.posApi
import com.example.entertainment.pos.testdata.aMockBackend
import io.mockk.mockk
import kotlin.random.Random

/* Worker over a real in-memory Room db and the mock POS server; payments are faked.
   The repository shares the db so tests can seed sales the normal way. Call close() in @After. */
class PosSyncWorkerBuilder(
    private val context: Context = ApplicationProvider.getApplicationContext(),
) {

    private val db = inMemoryPosDatabase(context)
    val dao = db.transactionDao()
    val repository = OfflineFirstPosRepository(dao, mockk(relaxed = true))
    val backend = aMockBackend()
    val recorder = RecordingInterceptor()
    val payments = FakePaymentProcessor()

    private val api = posApi(recorder, MockPosInterceptor(backend, Random(7)))

    /* attempt = WorkManager's runAttemptCount, 0 on the first run. */
    fun build(attempt: Int = 0) = TestListenableWorkerBuilder<PosSyncWorker>(context)
        .setRunAttemptCount(attempt)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                PosSyncWorker(appContext, workerParameters, dao, api, payments)
        })
        .build()

    fun close() = db.close()
}
