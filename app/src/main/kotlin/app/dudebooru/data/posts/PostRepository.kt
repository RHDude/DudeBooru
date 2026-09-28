package app.dudebooru.data.posts

import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.engine.PageKey
import app.dudebooru.booru.engine.QueryPlan
import app.dudebooru.booru.engine.Session
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.db.AppDatabase
import app.dudebooru.data.db.PostEntity
import app.dudebooru.data.filter.NegativeTags
import app.dudebooru.data.tags.TagDictionary
import app.dudebooru.util.DudeLog

data class FeedChunk(
    val posts: List<Post>,
    val next: PageKey?,
    val plan: QueryPlan,
    /** Сайт отверг ключ (401): аккаунт помечен недействительным, лента продолжилась без входа. */
    val authDropped: Boolean,
    /** Скрыто негативными тегами: строка блэклиста → сколько постов. */
    val hidden: Map<String, Int> = emptyMap(),
)

class PostRepository(
    private val registry: SiteRegistry,
    private val accounts: AccountRepository,
    private val db: AppDatabase,
    private val tags: TagDictionary,
    private val negative: NegativeTags,
) {
    /**
     * Порция ленты. Если локальная проверка (режим, теги сверх лимита) отсеяла почти всё,
     * догружает следующие страницы, чтобы экран не пустел.
     */
    suspend fun load(
        site: SiteConfig,
        request: FeedRequest,
        cursor: PageKey?,
        seen: Set<Long> = emptySet(),
        pageSize: Int = 40,
        minVisible: Int = 12,
        maxRequests: Int = 5,
    ): FeedChunk {
        val engine = registry.engine(site)
        var session = accounts.session(site)
        var authDropped = false
        var key = cursor
        var plan = QueryPlan.EMPTY
        val collected = LinkedHashMap<Long, Post>()
        val hidden = HashMap<String, Int>()
        var requests = 0

        while (requests < maxRequests) {
            val page = try {
                engine.posts(request, key, pageSize, session)
            } catch (e: BooruException.Unauthorized) {
                if (session.credentials == null) throw e
                accounts.markInvalid(site)
                session = Session()
                authDropped = true
                continue
            }
            requests++
            plan = page.plan
            // Негативные теги отсекаются до отрисовки: пост ни разу не мелькнёт на экране.
            val (visible, blocked) = negative.blacklist.value.partition(page.posts)
            for ((_, entry) in blocked) hidden.merge(entry.expression, 1, Int::plus)
            for (post in visible) {
                if (post.id !in seen) collected.putIfAbsent(post.id, post)
            }
            cache(site, page.posts)
            key = page.next
            if (key == null || collected.size >= minVisible) break
        }
        return FeedChunk(collected.values.toList(), key, plan, authDropped, hidden)
    }

    private suspend fun cache(site: SiteConfig, posts: List<Post>) {
        if (posts.isEmpty()) return
        runCatching {
            val now = System.currentTimeMillis()
            db.posts().upsertAll(posts.map { PostEntity(it.site, it.id, it.md5, BooruJson.encodeToString(Post.serializer(), it), now) })
            tags.recordPosts(site, posts)
        }.onFailure { DudeLog.w("posts", "cache failed: ${it.message}") }
    }

    suspend fun cached(site: String, id: Long): Post? =
        db.posts().get(site, id)?.let { runCatching { BooruJson.decodeFromString(Post.serializer(), it.json) }.getOrNull() }
}
