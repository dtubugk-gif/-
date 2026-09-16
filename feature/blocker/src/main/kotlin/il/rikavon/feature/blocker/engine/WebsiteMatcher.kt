package il.rikavon.feature.blocker.engine

/**
 * Turns what a browser's address bar shows into a host and matches it against the block list. Pure, so the
 * accessibility service does nothing clever on its own thread.
 */
object WebsiteMatcher {
    /** "https://m.instagram.com/reels?x=1" → "m.instagram.com"; a search query or empty bar → null. */
    fun host(addressBar: CharSequence?): String? {
        val raw = addressBar?.toString()?.trim()?.lowercase() ?: return null
        if (raw.isEmpty() || ' ' in raw) return null
        val afterScheme = raw.substringAfter("://", raw)
        val host =
            afterScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore(':')
        return host.takeIf { '.' in it && it.none { c -> c.isWhitespace() } }
    }

    /** The blocked domain that [host] is, or sits under; null when the page is allowed. */
    fun blockedBy(host: String?, blocked: Set<String>): String? {
        val h = host?.removePrefix("www.") ?: return null
        return blocked.firstOrNull { domain -> h == domain || h.endsWith(".$domain") }
    }
}
