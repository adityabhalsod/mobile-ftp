package com.mobileftp.util

/**
 * Pure validation rules for every editable configuration field in the app
 * (FTP server config and FTP client connection profiles).
 *
 * Each `validateX` function returns `null` when the value is valid or a short,
 * user-facing error message otherwise. Keeping the rules here (instead of
 * inline in composables) means the UI and the ViewModels enforce exactly the
 * same rules, and they can be unit tested without Android.
 */
object ConfigValidators {

    const val MIN_PORT: Int = 1
    const val MAX_PORT: Int = 65535

    /** Android apps cannot bind privileged ports (< 1024) without root. */
    const val MIN_UNPRIVILEGED_PORT: Int = 1024

    const val MIN_CHUNKS: Int = 1
    const val MAX_CHUNKS: Int = 32

    const val MAX_PROFILE_NAME: Int = 64
    const val MAX_USERNAME: Int = 64
    const val MAX_PASSWORD: Int = 128
    const val MIN_SERVER_PASSWORD: Int = 3

    private val HOSTNAME_LABEL = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val IPV6 = Regex("^[0-9A-Fa-f:.]+$")

    // ---------------------------------------------------------------------
    // Shared
    // ---------------------------------------------------------------------

    /** Validates a port typed as text. [min] lets the server require >= 1024. */
    fun validatePort(text: String, min: Int = MIN_PORT, label: String = "Port"): String? {
        if (text.isBlank()) return "$label is required"
        val value = text.trim().toIntOrNull() ?: return "$label must be a number"
        if (value < min || value > MAX_PORT) return "$label must be between $min and $MAX_PORT"
        return null
    }

    // ---------------------------------------------------------------------
    // Client connection profile
    // ---------------------------------------------------------------------

    fun validateProfileName(name: String): String? =
        if (name.trim().length > MAX_PROFILE_NAME) "Name must be at most $MAX_PROFILE_NAME characters" else null

    /**
     * Validates a host name, IPv4 or IPv6 address. Expects the value to have
     * already been passed through [normalizeHost].
     */
    fun validateHost(host: String): String? {
        val h = host.trim()
        if (h.isEmpty()) return "Host is required"
        if (h.any { it.isWhitespace() }) return "Host must not contain spaces"
        if (h.contains('/') || h.contains('@')) return "Enter only the host name or IP address"

        IPV4.matchEntire(h)?.let { m ->
            val ok = m.groupValues.drop(1).all { (it.toIntOrNull() ?: 256) in 0..255 }
            return if (ok) null else "Invalid IPv4 address"
        }
        if (h.contains(':')) {
            return if (IPV6.matches(h)) null else "Invalid IPv6 address"
        }
        if (h.length > 253) return "Host name is too long"
        // All-numeric dotted strings that didn't match IPV4 (e.g. "192.168.1") are typos.
        if (h.all { it.isDigit() || it == '.' }) return "Invalid IPv4 address"
        val labels = h.trimEnd('.').split('.')
        if (labels.any { !HOSTNAME_LABEL.matches(it) }) return "Invalid host name"
        return null
    }

    fun validateChunks(text: String): String? {
        if (text.isBlank()) return "Chunks is required"
        val value = text.trim().toIntOrNull() ?: return "Chunks must be a number"
        if (value !in MIN_CHUNKS..MAX_CHUNKS) return "Chunks must be between $MIN_CHUNKS and $MAX_CHUNKS"
        return null
    }

    fun validateClientUsername(username: String): String? {
        val u = username.trim()
        if (u.length > MAX_USERNAME) return "Username must be at most $MAX_USERNAME characters"
        if (u.any { it.isWhitespace() }) return "Username must not contain spaces"
        return null
    }

    fun validateClientPassword(password: String): String? {
        if (password.length > MAX_PASSWORD) return "Password must be at most $MAX_PASSWORD characters"
        if (password.contains('\n') || password.contains('\r')) return "Password must not contain line breaks"
        return null
    }

    /**
     * Strips things people commonly paste into a host field:
     * `ftp://host:21/path` → host `host`, port `21`.
     * Returns the cleaned host and, if one was embedded, the port.
     */
    fun normalizeHost(raw: String): Pair<String, Int?> {
        var h = raw.trim()
        h = h.removePrefix("ftp://").removePrefix("ftps://").removePrefix("FTP://").removePrefix("FTPS://")
        // Drop credentials "user:pass@host"
        if (h.contains('@')) h = h.substringAfterLast('@')
        // Drop path
        h = h.substringBefore('/')
        // Bracketed IPv6 with optional port: [::1]:21
        if (h.startsWith("[") && h.contains("]")) {
            val inner = h.substring(1, h.indexOf(']'))
            val port = h.substringAfter("]:", "").toIntOrNull()
            return inner to port
        }
        // host:port (only when exactly one colon — otherwise it is IPv6)
        if (h.count { it == ':' } == 1) {
            val port = h.substringAfter(':').toIntOrNull()
            return h.substringBefore(':') to port
        }
        return h to null
    }

    // ---------------------------------------------------------------------
    // Server config
    // ---------------------------------------------------------------------

    fun validateServerUsername(username: String): String? {
        val u = username.trim()
        if (u.isEmpty()) return "Username is required"
        if (u.length > MAX_USERNAME) return "Username must be at most $MAX_USERNAME characters"
        if (u.equals("anonymous", ignoreCase = true)) return "\"anonymous\" is reserved — use the Anonymous access switch"
        if (!u.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }) {
            return "Use only letters, digits, '.', '_' or '-'"
        }
        return null
    }

    fun validateServerPassword(password: String): String? {
        if (password.length < MIN_SERVER_PASSWORD) return "Password must be at least $MIN_SERVER_PASSWORD characters"
        if (password.length > MAX_PASSWORD) return "Password must be at most $MAX_PASSWORD characters"
        if (password.any { it == '\n' || it == '\r' }) return "Password must not contain line breaks"
        return null
    }

    /**
     * Validates the passive port range. Returns a pair of (startError, endError).
     */
    fun validatePasvRange(startText: String, endText: String, controlPort: Int?): Pair<String?, String?> {
        val startErr = validatePort(startText, MIN_UNPRIVILEGED_PORT, "PASV start")
        val endErr = validatePort(endText, MIN_UNPRIVILEGED_PORT, "PASV end")
        if (startErr != null || endErr != null) return startErr to endErr
        val start = startText.trim().toInt()
        val end = endText.trim().toInt()
        if (start > end) return null to "PASV end must be ≥ PASV start"
        if (controlPort != null && controlPort in start..end) {
            return "Range must not include the server port ($controlPort)" to null
        }
        return null to null
    }

    // ---------------------------------------------------------------------
    // Remote file names (mkdir / rename)
    // ---------------------------------------------------------------------

    fun validateRemoteName(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Name is required"
        if (n == "." || n == "..") return "Invalid name"
        if (n.contains('/') || n.contains('\\')) return "Name must not contain / or \\"
        if (n.any { it == '\n' || it == '\r' || it == '\u0000' }) return "Name contains invalid characters"
        if (n.length > 255) return "Name is too long"
        return null
    }
}
