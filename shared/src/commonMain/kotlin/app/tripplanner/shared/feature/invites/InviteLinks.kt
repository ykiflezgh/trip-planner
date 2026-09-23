package app.tripplanner.shared.feature.invites

/**
 * Invite link shapes (design §8.2): `https://<hosting-host>/join/{code}` is the shareable
 * App Link / Universal Link; `tripplanner://join/{code}` is the custom-scheme fallback the
 * landing page uses where Universal Links are not yet configured.
 */
object InviteLinks {
    const val CUSTOM_SCHEME = "tripplanner"
    private val codeChars = Regex("^[A-Za-z0-9_-]{4,64}$")

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun joinUrl(host: String, code: String): String = "https://$host/join/$code"

    /**
     * Extracts the invite code from a join link, or `null` for anything else.
     *
     * Complexity:
     * - **Time:** O(N) for an N-character URL.
     * - **Space:** O(1).
     */
    fun parseJoinCode(url: String): String? {
        val trimmed = url.trim()
        val path = when {
            trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) ->
                trimmed.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
            trimmed.startsWith("$CUSTOM_SCHEME://", ignoreCase = true) -> trimmed.substringAfter("://")
            else -> return null
        }
        val clean = path.substringBefore('?').substringBefore('#').trimEnd('/')
        val parts = clean.split('/').filter { it.isNotEmpty() }
        if (parts.size != 2 || !parts[0].equals("join", ignoreCase = true)) return null
        return parts[1].takeIf { codeChars.matches(it) }
    }

    /**
     * Accepts what a user might paste under "Join a trip": a full link or a bare code.
     *
     * Complexity:
     * - **Time:** O(N).
     * - **Space:** O(1).
     */
    fun normalizeCodeInput(input: String): String? {
        val t = input.trim()
        return parseJoinCode(t) ?: t.takeIf { codeChars.matches(it) }
    }
}
