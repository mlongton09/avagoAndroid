package com.avago.core.data.db.converters

import androidx.room.TypeConverter

class Converters {

    // Long (epoch ms) ↔ Long? — Room handles Long natively; these converters handle nullable Long.
    // Boolean ↔ Int (0/1)

    @TypeConverter
    fun fromBooleanToInt(value: Boolean): Int = if (value) 1 else 0

    @TypeConverter
    fun fromIntToBoolean(value: Int): Boolean = value != 0
}
