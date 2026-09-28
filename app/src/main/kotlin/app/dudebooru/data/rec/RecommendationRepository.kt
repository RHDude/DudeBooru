package app.dudebooru.data.rec

import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.rec.CandidateQuery
import app.dudebooru.booru.rec.Recommendation
import app.dudebooru.booru.rec.Recommender
import app.dudebooru.booru.rec.Signal
import app.dudebooru.booru.rec.SignalKind
import app.dudebooru.booru.rec.TasteProfile
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.db.AppDatabase
import app.dudebooru.data.db.DislikeEntity
import app.dudebooru.data.db.MutedTagEntity
import app.dudebooru.data.db.PostEntity
import app.dudebooru.data.filter.NegativeTags
import app.dudebooru.data.tags.TagDictionary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Рекомендации по лайкам и сохранённым. Профиль вкуса общий для всех источников,
 * а лента, как и главный экран, разложена по папкам-источникам без смешивания.
 */
class RecommendationRepository(
    private val db: AppDatabase,
    private val registry: SiteRegistry,
    private val accounts: AccountRepository,
    private val negative: NegativeTags,
    private val tags: TagDictionary,
    private val settings: app.dudebooru.data.settings.SettingsRepository,
) {
    private val mutex = Mutex()
    private var cached: Pair<Int, TasteProfile>? = null

    /** Пока лайков меньше — холодный старт: популярное за неделю. */
    val coldStartLikes = 10

    /**
     * Профиль пересчитывается сразу после лайка: кэш сбрасывается по числу сигналов.
     * Настройки → Рекомендации: сохранённые можно не учитывать, «Сбросить профиль» отсекает старые сигналы.
     */
    suspend fun profile(): TasteProfile = mutex.withLock {
        val prefs = settings.recPrefs.first()
        val rows = db.taste().signals().filter { it.at > prefs.resetAt && (prefs.useSaved || it.kind != SignalKind.SAVE.name) }
        val signature = (rows.size * 31 + rows.sumOf { it.at.hashCode() }) * 31 + prefs.hashCode()
        cached?.let { (sig, profile) -> if (sig == signature) return@withLock profile }
        val signals = rows.mapNotNull { row ->
            val post = runCatching { BooruJson.decodeFromString(Post.serializer(), row.json) }.getOrNull() ?: return@mapNotNull null
            Signal(post, SignalKind.valueOf(row.kind), row.at)
        }
        val muted = db.taste().muted().toSet()
        val idf = idfTable(signals.flatMap { it.post.allTags }.toSet())
        val profile = TasteProfile.build(signals, System.currentTimeMillis(), { idf[it] }, muted)
        cached = signature to profile
        profile
    }

    /**
     * Редкость тегов: ln(постов на сайте / постов с тегом) по словарю. Самым весомым тегам без счётчика
     * счётчики догружаются одним запросом к Danbooru и попадают в словарь.
     */
    private suspend fun idfTable(names: Set<String>): Map<String, Double> {
        val result = HashMap<String, Double>()
        val danbooru = registry.sites.first { it.engine == EngineType.DANBOORU }
        for (site in listOf(danbooru) + registry.sites.filter { it.engine == EngineType.MOEBOORU }) {
            val missing = names.filter { it !in result }
            if (missing.isEmpty()) break
            tags.postCounts(site, missing).forEach { (tag, count) -> result[tag] = TasteProfile.idf(count, site.approxTotalPosts) }
        }
        val unknown = names.filter { it !in result }.take(100)
        if (unknown.isNotEmpty()) {
            runCatching {
                val info = registry.engine(danbooru).tagInfo(unknown, accounts.session(danbooru).copy(background = true))
                tags.recordInfo(danbooru, info)
                info.forEach { t -> t.postCount?.let { result[t.name] = TasteProfile.idf(it, danbooru.approxTotalPosts) } }
            }
        }
        return result
    }

    /** «Не интересно…»: минус-сигнал, пост хранится для профиля. */
    suspend fun dislike(post: Post) {
        db.posts().upsertAll(listOf(PostEntity(post.site, post.id, post.md5, BooruJson.encodeToString(Post.serializer(), post), System.currentTimeMillis())))
        db.taste().dislike(DislikeEntity(post.site, post.id, System.currentTimeMillis()))
    }

    /** Убрать тег из «Моих тегов». */
    suspend fun mute(tag: String) = db.taste().mute(MutedTagEntity(tag, System.currentTimeMillis()))

    /**
     * Страница рекомендаций для источника: кандидаты по самым весомым тегам (каждый запрос — в лимите
     * тегов аккаунта), ранжирование по совпадению с профилем, отсев виденного, лайкнутого,
     * сохранённого, негативных тегов и режима.
     */
    suspend fun page(site: SiteConfig, mode: ContentMode, page: Int, shown: Set<String>): List<Recommendation> {
        val profile = profile()
        val queries = Recommender.queries(profile)
        val session = accounts.session(site)
        val engine = registry.engine(site)
        val candidates = coroutineScope {
            val fromQueries = queries.map { query ->
                async {
                    val sort = if (query.kind == CandidateQuery.Kind.ARTIST_NEW) SortOrder.NEW else SortOrder.HOT
                    runCatching {
                        engine.posts(FeedRequest(query.tags, sort, mode), app.dudebooru.booru.engine.PageKey.Number(page), 20, session).posts
                    }.getOrDefault(emptyList())
                }
            }
            // Рекомендатор Danbooru — дополнительный источник, если есть вход и он вообще отвечает.
            val fromSite = if (page == 1 && session.credentials != null) {
                listOf(async { runCatching { engine.recommendedForUser(50, session) }.getOrDefault(emptyList()) })
            } else {
                emptyList()
            }
            (fromQueries + fromSite).awaitAll().flatten()
        }
        val blacklist = negative.blacklist.value
        val exclude = shown + db.taste().seenKeys() + db.taste().collectedKeys()
        val allowed = candidates.filter { mode.allows(it.rating) && blacklist.match(it) == null }
        cache(allowed)
        return Recommender.rank(allowed, profile, exclude, exploreShare = settings.recPrefs.first().exploreShare.toDouble())
    }

    /** «Найти похожие»: профиль из одного поста, кандидаты по его тегам. */
    suspend fun similar(post: Post, mode: ContentMode, page: Int, shown: Set<String>): List<Recommendation> {
        val site = registry.site(post.site) ?: return emptyList()
        val idf = idfTable(post.allTags)
        val profile = TasteProfile.ofPost(post) { idf[it] }
        val engine = registry.engine(site)
        val session = accounts.session(site)
        val queries = Recommender.queries(profile, artists = 1, characters = 2, copyrights = 1, generalPairs = 1)
        val candidates = coroutineScope {
            queries.map { query ->
                async {
                    runCatching {
                        engine.posts(FeedRequest(query.tags, SortOrder.BEST, mode), app.dudebooru.booru.engine.PageKey.Number(page), 20, session).posts
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        val blacklist = negative.blacklist.value
        val allowed = candidates.filter { it.key != post.key && mode.allows(it.rating) && blacklist.match(it) == null }
        cache(allowed)
        return Recommender.rank(allowed, profile, shown + post.key, exploreShare = 0.0)
    }

    /** Та же картинка на других источниках — по md5. */
    suspend fun sameImageElsewhere(post: Post): List<Post> {
        val md5 = post.md5 ?: return emptyList()
        return coroutineScope {
            registry.sites.filter { it.id != post.site && !it.safeOnly }.map { site ->
                async { runCatching { registry.engine(site).byMd5(md5, accounts.session(site)) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun cache(posts: List<Post>) {
        if (posts.isEmpty()) return
        val now = System.currentTimeMillis()
        runCatching { db.posts().upsertAll(posts.map { PostEntity(it.site, it.id, it.md5, BooruJson.encodeToString(Post.serializer(), it), now) }) }
    }
}
