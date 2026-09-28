package app.dudebooru.data

import app.dudebooru.booru.engine.BooruEngine
import app.dudebooru.booru.engine.Engines
import app.dudebooru.booru.engine.TagLookup
import app.dudebooru.booru.net.BooruHttp
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.booru.site.Sites
import java.util.concurrent.ConcurrentHashMap

/** Сайты и их движки. Движок создаётся один раз на сайт. */
class SiteRegistry(
    private val http: BooruHttp,
    private val tags: TagLookup,
) {
    private val engines = ConcurrentHashMap<String, BooruEngine>()

    val sites: List<SiteConfig> get() = Sites.builtIn

    fun site(id: String): SiteConfig? = sites.firstOrNull { it.id == id }

    fun engine(site: SiteConfig): BooruEngine = engines.computeIfAbsent(site.id) { Engines.create(site, http, tags) }

    /** Сайты, делящие аккаунт (Danbooru и Safebooru). */
    fun sitesInGroup(group: String): List<SiteConfig> = sites.filter { it.accountGroup == group }
}
