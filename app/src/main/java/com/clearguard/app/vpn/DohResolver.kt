package com.clearguard.app.vpn

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sends raw RFC 8484 DNS-over-HTTPS queries on a shared OkHttp client. Reusing one client
 * lets repeated lookups share a pooled HTTP/2 connection instead of paying a fresh TCP +
 * TLS handshake per DNS query, which is the dominant cost of DoH.
 *
 * The VPN tunnel only routes the virtual DNS address (a /32), so these sockets reach the
 * network directly and do not need VpnService.protect().
 */
class DohResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .dns(BootstrapDns())
        .build()

    @Throws(IOException::class)
    fun query(endpoint: String, dnsPayload: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", DNS_MESSAGE_TYPE)
            .header("User-Agent", "ShieldDNS/0.4 DoH")
            .post(dnsPayload.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("DoH server returned HTTP " + response.code)
            }
            return response.body.bytes()
        }
    }

    fun shutdown() {
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        } catch (ignored: RuntimeException) {
            // Idle threads also time out on their own; shutdown is best-effort.
        }
    }

    /**
     * Resolves well-known DoH hosts from built-in IPs so the very first encrypted query does
     * not depend on a plain-DNS lookup of the DoH hostname looping back through the tunnel.
     * Unknown (custom) endpoints fall back to the system resolver.
     */
    private class BootstrapDns : Dns {
        private val bootstrap = mapOf(
            "dns.quad9.net" to listOf("9.9.9.9", "149.112.112.112"),
            "cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
            "dns.mullvad.net" to listOf("194.242.2.2"),
            "dns.adguard-dns.com" to listOf("94.140.14.14", "94.140.15.15")
        )

        override fun lookup(hostname: String): List<InetAddress> {
            val ips = bootstrap[hostname.lowercase(Locale.US)]
                ?: return Dns.SYSTEM.lookup(hostname)
            // Numeric literals resolve locally without any network lookup.
            return ips.map { InetAddress.getByName(it) }
        }
    }

    private companion object {
        const val DNS_MESSAGE_TYPE = "application/dns-message"
        val mediaType = DNS_MESSAGE_TYPE.toMediaType()
    }
}
