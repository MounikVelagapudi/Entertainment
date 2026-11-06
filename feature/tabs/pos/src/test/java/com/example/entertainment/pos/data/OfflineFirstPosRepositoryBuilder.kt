package com.example.entertainment.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.entertainment.pos.data.local.PosDatabase
import com.example.entertainment.pos.data.local.TransactionDao
import com.example.entertainment.pos.data.sync.PosSyncScheduler
import io.mockk.mockk

/* Real repository over an in-memory Room db; only the WorkManager scheduler is mocked.
   Call close() in @After. */
class OfflineFirstPosRepositoryBuilder(
    context: Context = ApplicationProvider.getApplicationContext(),
) {

    val db: PosDatabase = inMemoryPosDatabase(context)
    val dao: TransactionDao = db.transactionDao()
    val scheduler = mockk<PosSyncScheduler>(relaxed = true)

    fun build() = OfflineFirstPosRepository(dao, scheduler)

    fun close() = db.close()
}

fun inMemoryPosDatabase(context: Context): PosDatabase =
    Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
        .allowMainThreadQueries()
        .build()
