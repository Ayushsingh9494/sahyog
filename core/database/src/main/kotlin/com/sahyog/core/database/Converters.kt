package com.sahyog.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

internal class Converters {
    // ASCII unit separator: never typed by users, so it can't collide with list values.
    @TypeConverter
    fun fromStringList(values: List<String>): String = values.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    // Dates as epoch days and instants as epoch millis: compact, sortable, zone-independent.
    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
