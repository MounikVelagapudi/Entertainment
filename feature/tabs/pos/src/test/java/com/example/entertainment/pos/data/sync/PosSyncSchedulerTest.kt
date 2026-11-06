package com.example.entertainment.pos.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosSyncSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: PosSyncScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        scheduler = PosSyncScheduler(context)
    }

    private fun infos() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PosSyncWorker.UNIQUE_WORK_NAME).get()

    @Test
    fun `given nothing scheduled then status says so`() = runTest {
        assertEquals("No sync scheduled yet", scheduler.observeStatus().first())
    }

    @Test
    fun `given a sync request then unique work waits on a network connection`() {
        scheduler.requestSync()

        val info = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun `given a sync request then it backs off exponentially`() {
        scheduler.requestSync()

        // WorkInfo doesn't expose backoff, so read the stored spec (internal API, test-only)
        val id = infos().single().id.toString()
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(id)!!
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(10_000L, spec.backoffDelayDuration)
    }

    @Test
    fun `given repeated requests then they append to one chain instead of being dropped`() {
        repeat(3) { scheduler.requestSync() }

        val infos = infos()
        assertEquals(3, infos.size)
        assertTrue(infos.none { it.state.isFinished })
    }

    @Test
    fun `given queued work then status shows the latest state and chain length`() = runTest {
        scheduler.requestSync()
        scheduler.requestSync()

        val status = scheduler.observeStatus().first { it != "No sync scheduled yet" }
        assertTrue(status, status.startsWith("ENQUEUED") || status.startsWith("BLOCKED"))
        assertTrue(status, status.endsWith("2 in chain"))
    }
}
