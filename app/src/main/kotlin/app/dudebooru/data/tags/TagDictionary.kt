package app.dudebooru.data.tags

import app.dudebooru.booru.engine.TagLookup
import app.dudebooru.booru.engine.moebooru.MoebooruEngine
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.booru.model.TagInfo
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.db.TagAliasEntity
import app.dudebooru.data.db.TagDao
import app.dudebooru.data.db.TagEntity
import app.dudebooru.data.settings.SettingsRepository
import app.dudebooru.util.DudeLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Локальный словарь тегов. Moebooru отдаёт его целиком (`/tag/summary.json`: типы и алиасы),
 * у Danbooru он копится из постов, автодополнения и `tags.json`.
 */
class TagDictionary(
    private val dao: TagDao,
    private val settings: SettingsRepository,
) : TagLookup {
    private lateinit var registry: SiteRegistry
    private val summaryLocks = ConcurrentHashMap<String, Mutex>()

    fun attach(registry: SiteRegistry) {
        this.registry = registry
    }

    override suspend fun postCounts(site: SiteConfig, names: Collection<String>): Map<String, Long> =
        names.toList().chunked(500).flatMap { dao.byNames(site.id, it) }
            .mapNotNull { tag -> tag.postCount?.let { tag.name to it } }
            .toMap()

    override suspend fun categories(site: SiteConfig, names: Collection<String>): Map<String, TagCategory> {
        if (names.isEmpty()) return emptyMap()
        val known = lookupCategories(site, names)
        if (known.size == names.size || site.engine != EngineType.MOEBOORU) return known
        // Для Moebooru недостающее почти всегда значит «словарь ещё не загружен».
        if (!ensureSummary(site)) return known
        return lookupCategories(site, names)
    }

    private suspend fun lookupCategories(site: SiteConfig, names: Collection<String>): Map<String, TagCategory> =
        names.toList().chunked(500).flatMap { dao.byNames(site.id, it) }.associate { it.name to it.category }

    /** Загружает словарь Moebooru раз в неделю. Возвращает true, если словарь обновился сейчас. */
    suspend fun ensureSummary(site: SiteConfig, maxAgeMillis: Long = WEEK): Boolean {
        if (site.engine != EngineType.MOEBOORU) return false
        val lock = summaryLocks.computeIfAbsent(site.id) { Mutex() }
        return lock.withLock {
            val stored = settings.tagSummaryVersion(site.id)
            val now = System.currentTimeMillis()
            if (stored != null && now - stored.second < maxAgeMillis) return@withLock false
            val engine = registry.engine(site) as? MoebooruEngine ?: return@withLock false
            val summary = runCatching { engine.tagSummary() }
                .onFailure { DudeLog.w("tags", "summary ${site.id} failed: ${it.message}") }
                .getOrNull() ?: return@withLock false
            val tags = summary.entries.map { TagEntity(site.id, it.name, it.category, null, now) }
            val aliases = summary.entries.flatMap { entry -> entry.aliases.map { TagAliasEntity(site.id, it, entry.name) } }
            dao.importDictionary(tags, aliases)
            settings.setTagSummaryVersion(site.id, summary.version, now)
            DudeLog.d("tags", "summary ${site.id} v${summary.version}: ${tags.size} tags, ${aliases.size} aliases")
            true
        }
    }

    /** Категории из постов Danbooru — бесплатное пополнение словаря. */
    suspend fun recordPosts(site: SiteConfig, posts: List<Post>) {
        if (site.engine != EngineType.DANBOORU || posts.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = posts.flatMap { post ->
            TagCategory.entries.flatMap { category -> post.tags.byCategory(category).map { TagEntity(site.id, it, category, null, now) } }
        }.distinctBy { it.name }
        dao.insertIfAbsent(entities)
    }

    /** Точные данные с сайта: категория и число постов, алиасы. */
    suspend fun recordInfo(site: SiteConfig, infos: List<TagInfo>) {
        if (infos.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(infos.map { TagEntity(site.id, it.name, it.category, it.postCount, now) })
        val aliases = infos.mapNotNull { info -> info.antecedent?.takeIf { it != info.name }?.let { TagAliasEntity(site.id, it, info.name) } }
        if (aliases.isNotEmpty()) dao.upsertAliases(aliases)
    }

    /** Подсказки: сначала мгновенно из словаря, потом с сайта. */
    suspend fun localSuggestions(site: SiteConfig, prefix: String, limit: Int = 10): List<TagInfo> =
        dao.byPrefix(site.id, prefix.lowercase(), limit).map { TagInfo(it.name, it.category, it.postCount) }

    suspend fun remoteSuggestions(site: SiteConfig, prefix: String, limit: Int = 10): List<TagInfo> {
        val results = registry.engine(site).autocomplete(prefix, limit)
        recordInfo(site, results)
        return results
    }

    /** Алиас → каноничный тег (или сам тег). */
    suspend fun canonical(site: SiteConfig, tag: String): String = dao.canonical(site.id, tag) ?: tag

    private companion object {
        const val WEEK = 7L * 24 * 3600 * 1000
    }
}
