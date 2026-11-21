package com.example.entertainment.pos.data.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/* Test WorkManager that runs on the calling thread. Call before building anything that enqueues work. */
fun initTestWorkManager(context: Context) {
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
    )
}

fun posSyncWorkInfos(context: Context): List<WorkInfo> =
    WorkManager.getInstance(context).getWorkInfosForUniqueWork(PosSyncWorker.UNIQUE_WORK_NAME).get()

/* WorkInfo doesn't expose backoff, so read the stored spec (internal API, test-only). */
fun storedWorkSpec(context: Context, info: WorkInfo): WorkSpec =
    WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(info.id.toString())!!
