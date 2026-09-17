package il.rikavon.ui.settings

import il.rikavon.core.data.repo.CloudKey

/** A key pasted into the wrong dialog: the two providers' keys are easy to tell apart by their prefix. */
enum class KeyWarning {
    /** An Anthropic key where OpenAI's is wanted. */
    ANTHROPIC_FOR_VOICE,

    /** Some other provider's key where Anthropic's is wanted. */
    NOT_ANTHROPIC_FOR_BRAIN,

    ;

    companion object {
        private const val ANTHROPIC_PREFIX = "sk-ant-"

        /** Whether [key] plainly belongs to the other provider; null when it may well be right (or is empty). */
        fun of(kind: CloudKey, key: String): KeyWarning? {
            val clean = key.trim()
            if (clean.isEmpty()) return null
            val anthropic = clean.startsWith(ANTHROPIC_PREFIX)
            return when (kind) {
                CloudKey.BRAIN -> NOT_ANTHROPIC_FOR_BRAIN.takeUnless { anthropic }
                CloudKey.VOICE -> ANTHROPIC_FOR_VOICE.takeIf { anthropic }
            }
        }
    }
}
