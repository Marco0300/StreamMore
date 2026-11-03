package com.marco.streammore.tv

import java.net.URI

/**
 * The session cookie authenticates against the Streammore backend only. Stream and
 * trailer URLs can point at other hosts (Xtream providers, CDNs), and sending the
 * login cookie there hands the session to a third party.
 */
internal fun shouldSendSessionCookie(requestUrl: String, baseUrl: String): Boolean {
    val request = hostAndScheme(requestUrl) ?: return false
    val base = hostAndScheme(baseUrl) ?: return false
    return request == base
}

private fun hostAndScheme(url: String): Triple<String, String, Int>? = runCatching {
    val uri = URI(url)
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
    Triple(scheme, host, port)
}.getOrNull()
