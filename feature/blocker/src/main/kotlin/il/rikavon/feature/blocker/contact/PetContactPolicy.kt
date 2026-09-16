package il.rikavon.feature.blocker.contact

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.DayUsageSnapshot
import java.time.LocalDate

enum class MessageKind { NEAR_LIMIT, AT_LIMIT }

enum class CallReason { NEAR_LIMIT, REPEATED_BLOCKS }

/** One thing the pet wants to say, and how loudly. */
sealed interface PetContact {
    val packageName: String
    val minutesLeft: Int

    data class Message(override val packageName: String, val kind: MessageKind, override val minutesLeft: Int) :
        PetContact

    data class Call(override val packageName: String, val reason: CallReason, override val minutesLeft: Int) :
        PetContact
}

/**
 * When the pet reaches out. Pure and stateful only for de-duplication: every message or call fires once per
 * app per day, and the slate is wiped when the snapshot's date changes.
 *
 *  - message at 80 % of a limit, and again when the limit is reached
 *  - call at 95 % while that app is on screen (the user is scrolling towards the wall)
 *  - call on every third block of the same app in a day (the user keeps coming back)
 */
class PetContactPolicy {
    private var day: LocalDate? = null
    private val sent = mutableSetOf<String>()

    fun evaluate(snapshot: DayUsageSnapshot, limits: List<AppLimit>, blockCounts: Map<String, Int>): List<PetContact> {
        if (day != snapshot.date) {
            day = snapshot.date
            sent.clear()
        }
        val out = mutableListOf<PetContact>()
        for (limit in limits.filter { it.enabled && !it.fullBlock }) {
            val pkg = limit.packageName
            val used = snapshot.usageOf(pkg).minutes
            val ratio = used.toFloat() / limit.limitMinutes.coerceAtLeast(1)
            val left = (limit.limitMinutes - used).coerceAtLeast(0)
            when {
                ratio >= 1f -> once(pkg, "limit") { out += PetContact.Message(pkg, MessageKind.AT_LIMIT, 0) }
                ratio >= NEAR_RATIO ->
                    once(
                        pkg,
                        "near",
                    ) { out += PetContact.Message(pkg, MessageKind.NEAR_LIMIT, left) }
            }
            if (ratio >= CALL_RATIO && ratio < 1f && snapshot.foregroundPackage == pkg) {
                once(pkg, "call-near") { out += PetContact.Call(pkg, CallReason.NEAR_LIMIT, left) }
            }
        }
        for ((pkg, count) in blockCounts) {
            if (count > 0 && count % REPEATED_BLOCKS == 0) {
                once(pkg, "call-blocks-$count") { out += PetContact.Call(pkg, CallReason.REPEATED_BLOCKS, 0) }
            }
        }
        return out
    }

    private inline fun once(packageName: String, key: String, block: () -> Unit) {
        if (sent.add("$packageName:$key")) block()
    }

    companion object {
        const val NEAR_RATIO = 0.8f
        const val CALL_RATIO = 0.95f
        const val REPEATED_BLOCKS = 3
    }
}
