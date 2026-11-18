package com.example.entertainment.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/* Separate from core's AppDatabase on purpose: that one is a disposable cache with a
   destructive migration fallback, these are unsynced sales. */
@Database(
    entities = [TransactionEntity::class, TransactionLineEntity::class, TransactionEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
