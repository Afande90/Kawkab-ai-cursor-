package app.kawkab.android.domain.harness

import app.kawkab.android.domain.models.ChatMessage

/**
 * Older images travel in the prompt as a short text note. The current image is left alone.
 */
object ImageMemory {
    /**
     * Caps [description] at about one hundred tokens. This does not read image bytes.
     */
    fun remember(description: String): String {
        val words = description.trim().ifBlank { EARLIER }
        val cap = TOKEN_CAP * CHARS_PER_TOKEN
        return if (words.length <= cap) words else words.take(cap)
    }

    /**
     * Drops the image file from every attached message except the latest one.
     * Those older turns keep a text note instead.
     */
    fun olderAsText(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return messages
        val lastImage = messages.indexOfLast { it.attachment != null }
        if (lastImage < 0) return messages
        return messages.mapIndexed { index, message ->
            if (index == lastImage || message.attachment == null) {
                message
            } else {
                message.copy(content = remember(message.content), attachment = null)
            }
        }
    }

    private const val TOKEN_CAP: Int = 100
    private const val CHARS_PER_TOKEN: Int = 4
    private const val EARLIER: String = "An earlier image."
}
