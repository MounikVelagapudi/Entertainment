package com.example.entertainment.core.data.local

import android.content.Context
import androidx.room.Room
import com.example.entertainment.core.data.local.AppDatabaseTopRated
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live AppDatabase instance instead of handing it out as a frozen
 * singleton. SQLite corruption is the one Room failure with an actual fix --
 * close the broken file, delete it, open a fresh one -- but that only works if
 * something is still holding a mutable reference to swap. Callers read
 * [current] to get whatever instance is live right now, and call [recreate]
 * once they've caught a corruption error to get a working one back.
 */
@Singleton
class AppDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    var current: AppDatabaseTopRated = buildDatabase()
        private set

    /* Close-delete-rebuild isn't atomic, so guard against two callers racing the swap. */
    @Synchronized
    fun recreate(): AppDatabaseTopRated {
        current.close()
        context.deleteDatabase(DB_NAME)
        return buildDatabase().also { current = it }
    }

    /* Cache-only data -- just wipe and recreate on a schema bump instead of crashing. */
    private fun buildDatabase(): AppDatabaseTopRated =
        Room.databaseBuilder(context, AppDatabaseTopRated::class.java, DB_NAME)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

    private companion object {
        const val DB_NAME = "entertainment.db"
    }
}
