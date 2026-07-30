package com.example.entertainment.home

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaResultsCache @Inject constructor() {
    private val cache = ConcurrentHashMap<Int, MediaResults>()

    // stash every popular-movie, so that while navigating to the next screen, we only pass the ID through navigation and dont need to pass the whole object
    fun putAll(items: List<MediaResults>) {
        items.forEach { cache[it.id] = it }
    }

    // On the second screen (details screen) we fetch the results based on ID, this will eliminate sending huge objects across the navigation
    fun get(id: Int): MediaResults? = cache[id]
}
