package app.kawkab.android.domain.services

/** Owner reset rule 3: private LAN servers are off the phone too. Only loopback is local. */
object LocalOnlyPolicy {
    /** Exact loopback hosts only; no remote hostname, private LAN, or alternate numeric spelling. */
    fun isLocalEndpoint(url: String): Boolean {
        val host = CleartextPolicy.hostOf(url) ?: return false
        return host in setOf("localhost", "127.0.0.1", "::1", "[::1]")
    }
}
