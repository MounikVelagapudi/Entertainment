package com.example.entertainment.home

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaResultsCache @Inject constructor() {
    private val cache = ConcurrentHashMap<Int, MediaResults>()

    fun putAll(items: List<MediaResults>) {
        items.forEach { cache[it.id] = it }
    }

    fun get(id: Int): MediaResults? = cache[id]
}
