package com.example.entertainment.core.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Shape every offline-first feature Dao (TopRated, Popular, ...) needs to expose
 * for the RAM-fallback + corruption-recovery machinery to operate on it
 * generically, without knowing the concrete Entity type or the real @Query behind
 * each method. Implement this on the actual Room @Dao interface -- Room is fine
 * with a Dao overriding an unannotated base interface method as long as the
 * override itself carries the real annotation.
 */
interface OfflineCacheDao<Entity> {
    fun observeAll(): Flow<List<Entity>>
    suspend fun findById(id: Int): Entity?
    suspend fun addDataToCache(entities: List<Entity>)
    suspend fun count(): Int
}
