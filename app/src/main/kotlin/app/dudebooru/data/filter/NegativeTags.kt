package app.dudebooru.data.filter

import app.dudebooru.booru.filter.Blacklist
import app.dudebooru.booru.filter.BlacklistEntry
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.db.NegativeTagDao
import app.dudebooru.data.db.NegativeTagEntity
import app.dudebooru.data.tags.TagDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Есть ли тег на сайте; если нет — похожий местный вариант. */
data class SiteCheck(val site: SiteConfig, val found: Boolean, val suggestion: String?)

/**
 * Один список тегов, которые не должны попадать в ленту. Фильтрация идёт в приложении:
 * у Danbooru исключения `-tag` тратят слоты лимита, а бесплатному аккаунту их всего 2.
 */
class NegativeTags(
    private val dao: NegativeTagDao,
    private val tags: TagDictionary,
    private val registry: SiteRegistry,
    private val accounts: AccountRepository,
    scope: CoroutineScope,
) {
    val entries: StateFlow<List<NegativeTagEntity>> = dao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val blacklist: StateFlow<Blacklist> = dao.all()
        .map { list -> Blacklist(list.map { BlacklistEntry(it.id, it.expression, it.site) }) }
        .stateIn(scope, SharingStarted.Eagerly, Blacklist(emptyList()))

    /** Алиас сохраняется как каноничный тег. Возвращает то, что записалось. */
    suspend fun add(expression: String, site: SiteConfig?, dictionarySite: SiteConfig?): String {
        val normalized = expression.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { term ->
            val sign = if (term.startsWith("-")) "-" else ""
            val body = term.removePrefix("-")
            if (':' in body || dictionarySite == null) term else sign + tags.canonical(dictionarySite, body)
        }.joinToString(" ")
        if (normalized.isBlank()) return normalized
        dao.insert(NegativeTagEntity(expression = normalized, site = site?.id, createdAt = System.currentTimeMillis()))
        return normalized
    }

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun removeExpression(expression: String) {
        entries.value.filter { it.expression == expression }.forEach { dao.delete(it.id) }
    }

    /**
     * Есть ли тег на каждом сайте. Если на yande.re он называется иначе — подсказка местного варианта
     * (из автодополнения по тем же словам).
     */
    suspend fun checkAcrossSites(tag: String, sites: List<SiteConfig>): List<SiteCheck> {
        val name = tag.trim().lowercase().removePrefix("-")
        if (name.isEmpty() || ':' in name || ' ' in name) return emptyList()
        return sites.distinctBy { it.accountGroup to it.engine }.map { site ->
            val engine = registry.engine(site)
            val found = runCatching { engine.tagInfo(listOf(name)).any { it.name == name && (it.postCount ?: 1) > 0 } }.getOrDefault(true)
            val suggestion = if (found) {
                null
            } else {
                val words = name.substringBefore("_(").split('_').filter { it.length > 2 }
                runCatching { engine.autocomplete(words.firstOrNull() ?: name, 5) }.getOrDefault(emptyList())
                    .firstOrNull { candidate -> words.all { it in candidate.name } }?.name
            }
            SiteCheck(site, found, suggestion)
        }
    }

    /** Блэклист из профиля Danbooru после входа. Возвращает число новых строк. */
    suspend fun importFromDanbooru(): Int {
        val site = registry.sites.firstOrNull { it.engine == EngineType.DANBOORU } ?: return 0
        val session = accounts.session(site)
        val credentials = session.credentials ?: return -1
        val info = registry.engine(site).verify(credentials)
        val existing = entries.value.map { it.expression }.toSet()
        var added = 0
        for (line in info.blacklistedTags) {
            val expression = line.trim().lowercase()
            if (expression.isEmpty() || expression in existing) continue
            dao.insert(NegativeTagEntity(expression = expression, site = null, createdAt = System.currentTimeMillis()))
            added++
        }
        return added
    }
}
