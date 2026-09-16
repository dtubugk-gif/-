package il.rikavon.core.data.security

import java.security.MessageDigest

/**
 * The settings lock. Only a salted SHA-256 of the PIN is stored, so a backup or a database dump never
 * reveals it; it is not exported at all. This is a speed bump against one's own weaker moments, not a
 * security boundary against someone with the unlocked phone.
 */
object PinVerifier {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun hash(pin: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest((SALT + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun matches(pin: String, hash: String?): Boolean = hash != null && hash(pin) == hash

    private const val SALT = "rikavon:pin:"
}
