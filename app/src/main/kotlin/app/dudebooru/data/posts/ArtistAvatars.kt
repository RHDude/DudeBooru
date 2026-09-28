package app.dudebooru.data.posts

import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.db.ArtistAvatarDao
import app.dudebooru.data.db.ArtistAvatarEntity
import app.dudebooru.data.filter.NegativeTags
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Аватарка художника — круглая вырезка из его самой популярной работы (по score) в текущем режиме.
 * На booru своих аватарок у художников нет. Запрос один раз, кэш на неделю; запросы фоновые,
 * чтобы не отнимать очередь у ленты.
 */
class ArtistAvatars(
    private val registry: SiteRegistry,
    private val accounts: AccountRepository,
    private val dao: ArtistAvatarDao,
    private val negative: NegativeTags,
) {
    private val memory = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    fun cached(site: SiteConfig, artist: String, mode: ContentMode): String? = memory[key(site, artist, mode)]

    suspend fun url(site: SiteConfig, artist: String, mode: ContentMode): String? {
        val key = key(site, artist, mode)
        memory[key]?.let { return it }
        if (key in missing) return null
        val mine = CompletableDeferred<String?>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) return existing.await()
        try {
            val result = load(site, artist, mode)
            if (result != null) memory[key] = result else missing += key
            mine.complete(result)
            return result
        } catch (e: Exception) {
            mine.complete(null)
            return null
        } finally {
            inFlight.remove(key)
        }
    }

    private suspend fun load(site: SiteConfig, artist: String, mode: ContentMode): String? {
        val now = System.currentTimeMillis()
        dao.get(site.id, artist, mode.name)?.let { cached ->
            if (now - cached.fetchedAt < WEEK) return cached.url
        }
        val session = accounts.session(site).copy(background = true)
        val page = registry.engine(site).posts(FeedRequest(listOf(artist), SortOrder.BEST, mode), null, 5, session)
        val url = page.posts.firstOrNull { negative.blacklist.value.match(it) == null }?.let { it.previewUrl ?: it.sampleUrl }
        dao.upsert(ArtistAvatarEntity(site.id, artist, mode.name, url, now))
        return url
    }

    private fun key(site: SiteConfig, artist: String, mode: ContentMode) = "${site.id}|$artist|${mode.name}"

    private companion object {
        const val WEEK = 7L * 24 * 3600 * 1000
    }
}
