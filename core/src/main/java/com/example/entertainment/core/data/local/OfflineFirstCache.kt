package com.example.entertainment.core.data.local

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.util.Log
import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.TRPError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Shared offline-first plumbing, pulled out of TopRatedRepoImpl once Popular
 * needed the same shape: Room as the primary store, an in-memory buffer as the
 * fail-safe once [StorageHealthMonitor] is flagged compromised, and one
 * self-heal attempt (recreate the db, retry) on a corrupt read/write before
 * anything falls back to the buffer. Each feature repo supplies its own
 * [OfflineCacheDao] accessor and Entity<->Domain mapping; this class owns the
 * RAM buffer and the recovery sequence so that plumbing isn't hand-rolled again
 * per feature.
 *
 * The feature repo still owns the network call -- persist() expects to be
 * handed an already-fetched list, and getById()/observe() never touch the
 * network themselves.
 */
class OfflineFirstCache<Entity, Domain>(
    private val appDatabaseProvider: AppDatabaseProvider,
    private val storageHealthMonitor: StorageHealthMonitor,
    private val ioDispatcher: CoroutineDispatcher,
    private val daoOf: (AppDatabaseTopRated) -> OfflineCacheDao<Entity>,
    private val toDomain: (Entity) -> Domain,
    private val toEntity: (Domain, sortOrder: Int) -> Entity,
    private val domainId: (Domain) -> Int,
) {

    private val ramBackupBuffer = MutableStateFlow<List<Domain>>(emptyList())

    /* Set by the repo on a failed network call; persist() clears it on any successful write. */
    val lastError = MutableStateFlow<TRPError?>(null)

    fun observe(): Flow<Resource<List<Domain>>> =
        storageHealthMonitor.compromised
            .flatMapLatest { compromised ->
                val source: Flow<List<Domain>> = if (compromised) {
                    ramBackupBuffer
                } else {
                    observeRoom(dao())
                        .catch { throwable ->
                            /* Only corruption gets a retry -- other failures (full disk, locked
                               file) wouldn't be fixed by wiping the file. */
                            val recoveredDao = (throwable as? SQLiteDatabaseCorruptException)
                                ?.let { recoverFromCorruption() }

                            if (recoveredDao != null) {
                                emitAll(observeRoom(recoveredDao))
                            } else {
                                storageHealthMonitor.reportFailure(throwable)
                                emitAll(ramBackupBuffer)
                            }
                        }
                }
                source.combine(lastError) { items, error ->
                    when {
                        /* Show cached data even if the latest refresh failed -- that's the point of offline-first. */
                        items.isNotEmpty() -> Resource.Success(items)
                        error != null -> Resource.Error(error.toString())
                        else -> Resource.Loading
                    }
                }
            }
            .flowOn(ioDispatcher)

    /* Call only after a successful fetch -- on failure the repo just sets lastError
       and leaves the cache untouched. */
    suspend fun persist(items: List<Domain>) {
        if (!storageHealthMonitor.isCompromised) {
            try {
                writeToRoom(dao(), items)
            } catch (e: SQLiteException) {
                val recoveredDao = (e as? SQLiteDatabaseCorruptException)
                    ?.let { recoverFromCorruption() }

                if (recoveredDao != null) {
                    writeToRoom(recoveredDao, items)
                } else {
                    storageHealthMonitor.reportFailure(e)
                }
            }
        }

        /* Room was already compromised, or the write above just tripped it -- either way
           the data still needs to reach the user via the RAM buffer. */
        if (storageHealthMonitor.isCompromised) {
            ramBackupBuffer.value = items
        }

        lastError.value = null
    }

    suspend fun getById(id: Int): Domain? = withContext(ioDispatcher) {
        if (storageHealthMonitor.isCompromised) {
            ramBackupBuffer.value.find { domainId(it) == id }
        } else {
            try {
                dao().findById(id)?.let(toDomain)
            } catch (e: SQLiteException) {
                val recoveredDao = (e as? SQLiteDatabaseCorruptException)
                    ?.let { recoverFromCorruption() }

                if (recoveredDao != null) {
                    /* No match on the fresh db is a valid answer, not a reason to fall through to RAM. */
                    recoveredDao.findById(id)?.let(toDomain)
                } else {
                    storageHealthMonitor.reportFailure(e)
                    ramBackupBuffer.value.find { domainId(it) == id }
                }
            }
        }
    }

    /* Whatever db instance is live right now -- may get swapped out by recoverFromCorruption(). */
    private fun dao(): OfflineCacheDao<Entity> = daoOf(appDatabaseProvider.current)

    private fun observeRoom(dao: OfflineCacheDao<Entity>): Flow<List<Domain>> =
        dao.observeAll().map { entities -> entities.map(toDomain) }

    private suspend fun writeToRoom(dao: OfflineCacheDao<Entity>, items: List<Domain>) =
        withContext(ioDispatcher) {
            val offsetRecordsCount = dao.count()
            dao.addDataToCache(items.mapIndexed { index, item ->
                toEntity(
                    item,
                    offsetRecordsCount + index
                )
            })
        }

    /* Clears and recreates the DB, no retry loop -- either the fresh file works or Room's unusable right now. */
    private fun recoverFromCorruption(): OfflineCacheDao<Entity>? = try {
        daoOf(appDatabaseProvider.recreate())
    } catch (e: Exception) {
        Log.w(TAG, "Room recreate after corruption also failed, staying on RAM fallback", e)
        null
    }

    private companion object {
        const val TAG = "OfflineFirstCache"
    }
}
