package il.rikavon.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import il.rikavon.core.ui.R

private const val MINUTES_PER_HOUR = 60

@Composable
fun formatMinutes(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.core_ui_minutes_short, rest)
        rest == 0 -> stringResource(R.string.core_ui_hours_short, hours)
        else -> stringResource(R.string.core_ui_hours_minutes_short, hours, rest)
    }
}

fun formatClock(minuteOfDay: Int): String {
    val h = (minuteOfDay / MINUTES_PER_HOUR) % HOURS_PER_DAY
    val m = minuteOfDay % MINUTES_PER_HOUR
    return "%02d:%02d".format(h, m)
}

private const val HOURS_PER_DAY = 24
