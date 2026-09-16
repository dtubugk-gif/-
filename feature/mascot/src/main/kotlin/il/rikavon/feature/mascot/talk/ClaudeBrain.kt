package il.rikavon.feature.mascot.talk

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * [PetBrain] on the Claude API with the user's own key. A fast model, because the answer is spoken inside a
 * conversational pause on a phone call; a short timeout and a single retry for the same reason. Any error,
 * refusal or empty answer is a null, and the engine falls back to the script.
 */
class ClaudeBrain(apiKey: String, private val model: String = MODEL) : PetBrain {
    private val client: AnthropicClient =
        AnthropicOkHttpClient
            .builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .maxRetries(RETRIES)
            .build()

    override suspend fun ask(system: String, turns: List<Turn>): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val params =
                    MessageCreateParams
                        .builder()
                        .model(model)
                        .maxTokens(MAX_TOKENS)
                        .system(system)
                turns.forEach { turn ->
                    if (turn.fromPet) params.addAssistantMessage(turn.text) else params.addUserMessage(turn.text)
                }
                val message = client.messages().create(params.build())
                if (message.stopReason().orElse(null) == StopReason.REFUSAL) return@runCatching null
                message
                    .content()
                    .mapNotNull { block -> block.text().orElse(null)?.text() }
                    .joinToString(" ")
                    .ifBlank { null }
            }.getOrNull()
        }

    companion object {
        /** The fastest current model: the reply has to land before the pause on the line feels wrong. */
        const val MODEL = "claude-haiku-4-5"
        private const val MAX_TOKENS = 200L
        private const val TIMEOUT_SECONDS = 8L
        private const val RETRIES = 1
    }
}
