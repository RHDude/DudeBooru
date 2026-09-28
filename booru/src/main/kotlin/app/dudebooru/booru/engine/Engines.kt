package app.dudebooru.booru.engine

import app.dudebooru.booru.engine.danbooru.DanbooruEngine
import app.dudebooru.booru.engine.moebooru.MoebooruEngine
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.net.BooruHttp
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

object Engines {
    fun create(site: SiteConfig, http: BooruHttp, tags: TagLookup = TagLookup.None): BooruEngine = when (site.engine) {
        EngineType.DANBOORU -> DanbooruEngine(site, http, tags)
        EngineType.MOEBOORU -> MoebooruEngine(site, http, tags)
    }

    /**
     * «Свой сайт»: движок определяется пробным запросом.
     * Danbooru отвечает на `/posts.json` массивом с `tag_string`, Moebooru на `/post.json` — массивом с `tags`.
     */
    suspend fun detect(baseUrl: String, http: BooruHttp): SiteConfig? {
        val normalized = normalizeBaseUrl(baseUrl) ?: return null
        val host = normalized.substringAfter("://")
        val probe = SiteConfig(
            id = "custom:$host",
            name = host,
            engine = EngineType.DANBOORU,
            baseUrl = normalized,
            maxPageSize = 100,
            anonymousTagLimit = 2,
            isCustom = true,
        )
        if (looksLike(http, probe, "$normalized/posts.json?limit=1", "tag_string")) {
            return probe.copy(maxPageSize = 200)
        }
        if (looksLike(http, probe, "$normalized/post.json?limit=1", "tags")) {
            return probe.copy(engine = EngineType.MOEBOORU, anonymousTagLimit = 6)
        }
        return null
    }

    fun normalizeBaseUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return "${url.scheme}://${url.host}${if (url.port != defaultPort(url.scheme)) ":${url.port}" else ""}"
    }

    private fun defaultPort(scheme: String) = if (scheme == "http") 80 else 443

    private suspend fun looksLike(http: BooruHttp, site: SiteConfig, url: String, field: String): Boolean = try {
        val body = http.get(site, Request.Builder().url(url).header("Accept", "application/json").build())
        val first = (BooruJson.parseToJsonElement(body) as? JsonArray)?.firstOrNull() as? JsonObject
        first != null && field in first
    } catch (e: BooruException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
