package com.routines.appclose.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** סוגי הפעולות שאפשר להפעיל כשיוצאים מאפליקציה. */
enum class ActionType {
    SOUND_MODE,      // intValue: 0 שקט, 1 רטט, 2 צלצול
    MEDIA_VOLUME,    // intValue: אחוז עוצמת מדיה 0..100
    DND,             // intValue: 1 הפעלת "נא לא להפריע", 0 כיבוי
    OPEN_APP,        // stringValue: package של האפליקציה לפתיחה
    NOTIFY,          // stringValue: טקסט התזכורת
    BRIGHTNESS,      // intValue: אחוז בהירות 0..100
    WIFI_PANEL,      // פתיחת פאנל ה-Wi-Fi של המערכת
}

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val watchedPackage: String,
    val watchedAppLabel: String,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "actions",
    foreignKeys = [ForeignKey(
        entity = Rule::class,
        parentColumns = ["id"],
        childColumns = ["ruleId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ruleId")],
)
data class RuleAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val type: ActionType,
    val intValue: Int? = null,
    val stringValue: String? = null,
)

@Entity(tableName = "trigger_log")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleName: String,
    val appLabel: String,
    val timestamp: Long,
)

data class RuleWithActions(
    @Embedded val rule: Rule,
    @Relation(parentColumn = "id", entityColumn = "ruleId")
    val actions: List<RuleAction>,
)
