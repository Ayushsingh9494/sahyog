package com.sahyog.core.database

import androidx.room.TypeConverter

internal class Converters {
    // ASCII unit separator: never typed by users, so it can't collide with list values.
    @TypeConverter
    fun fromStringList(values: List<String>): String = values.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
