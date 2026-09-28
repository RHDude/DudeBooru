package app.dudebooru.data.net

import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

@Serializable
data class ProxyConfig(
    val type: Type = Type.NONE,
    val host: String = "",
    val port: Int = 0,
) {
    @Serializable
    enum class Type { NONE, HTTP, SOCKS }

    val isActive: Boolean get() = type != Type.NONE && host.isNotBlank() && port in 1..65535

    fun toProxy(): Proxy? {
        if (!isActive) return null
        val kind = if (type == Type.HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS
        return Proxy(kind, InetSocketAddress.createUnresolved(host.trim(), port))
    }
}

/** Прокси из настроек «Сеть» для всех запросов приложения, меняется на лету. */
class DynamicProxySelector : ProxySelector() {
    @Volatile
    var config: ProxyConfig = ProxyConfig()

    override fun select(uri: URI?): List<Proxy> = listOf(config.toProxy() ?: Proxy.NO_PROXY)

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}
