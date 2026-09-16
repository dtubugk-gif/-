package il.rikavon.feature.mascot.talk

import il.rikavon.core.data.repo.AiSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Which brain answers: the AI one when the user has entered a key, the offline script otherwise. */
@Singleton
class ConversationEngines @Inject constructor(private val ai: AiSettingsRepository) {
    /** A fresh engine for one conversation (a call, or the talk screen); each keeps its own short memory. */
    suspend fun create(): ConversationEngine {
        val key = ai.current() ?: return ScriptedConversation()
        return ClaudeConversation(ClaudeBrain(key))
    }
}
