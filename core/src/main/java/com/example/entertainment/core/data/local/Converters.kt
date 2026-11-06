package com.example.entertainment.core.data.local

import androidx.room.TypeConverter

/**
 * Room only knows how to persist primitive columns, so a List<Int> (e.g. genre ids)
 * needs an explicit conversion to/from a single String column.
 */
class Converters {

    @TypeConverter
    fun fromIntList(value: List<Int>): String = value.joinToString(separator = ",")

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
}
