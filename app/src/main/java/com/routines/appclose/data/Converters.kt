package com.routines.appclose.data

import androidx.room.TypeConverter

/** Room אינו ממיר enums לבד — ממירים ל/מ-String לפי שם הקבוע. */
class Converters {
    @TypeConverter
    fun fromActionType(type: ActionType): String = type.name

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.valueOf(value)
}
