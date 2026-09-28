package app.dudebooru.data.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/** DNS-over-HTTPS: помогает, когда провайдер подменяет или режет DNS-ответы для сайта. */
enum class DohProvider(val url: String?, val bootstrap: List<String>) {
    NONE(null, emptyList()),
    CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15")),
}

/** Системный DNS или DoH по настройке «Сеть», переключается на лету. */
class DynamicDns(private val bootstrapClient: () -> OkHttpClient) : Dns {
    @Volatile
    private var resolver: Dns = Dns.SYSTEM

    fun use(provider: DohProvider) {
        val url = provider.url
        resolver = if (url == null) {
            Dns.SYSTEM
        } else {
            DnsOverHttps.Builder()
                .client(bootstrapClient())
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(provider.bootstrap.map { InetAddress.getByName(it) })
                .includeIPv6(false)
                .build()
        }
    }

    override fun lookup(hostname: String): List<InetAddress> = resolver.lookup(hostname)
}
