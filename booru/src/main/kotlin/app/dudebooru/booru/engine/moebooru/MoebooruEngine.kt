package app.dudebooru.booru.engine.moebooru

import app.dudebooru.booru.engine.BooruEngine
import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.engine.LocalMatcher
import app.dudebooru.booru.engine.PageKey
import app.dudebooru.booru.engine.PoolInfo
import app.dudebooru.booru.engine.PostsPage
import app.dudebooru.booru.engine.QueryPlan
import app.dudebooru.booru.engine.QueryPlanner
import app.dudebooru.booru.engine.Session
import app.dudebooru.booru.engine.TagLookup
import app.dudebooru.booru.engine.TagRules
import app.dudebooru.booru.model.AccountInfo
import app.dudebooru.booru.model.ArtistInfo
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.PostStatus
import app.dudebooru.booru.model.PostTags
import app.dudebooru.booru.model.Rating
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.booru.model.TagInfo
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.net.BooruHttp
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.site.SiteConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneOffset

class MoebooruEngine(
    override val site: SiteConfig,
    private val http: BooruHttp,
    private val tags: TagLookup = TagLookup.None,
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) : BooruEngine {

    override val supportedSorts: List<SortOrder> = listOf(
        SortOrder.NEW,
        SortOrder.HOT,
        SortOrder.POPULAR_DAY,
        SortOrder.POPULAR_WEEK,
        SortOrder.POPULAR_MONTH,
        SortOrder.POPULAR_YEAR,
        SortOrder.BEST,
        SortOrder.MPIXELS,
        SortOrder.LANDSCAPE,
        SortOrder.PORTRAIT,
        SortOrder.RANDOM,
    )

    private fun base(mode: ContentMode): HttpUrl = site.baseUrlFor(mode).toHttpUrl()

    override suspend fun posts(request: FeedRequest, page: PageKey?, limit: Int, session: Session): PostsPage {
        val pageSize = limit.coerceIn(1, site.maxPageSize)
        val userTerms = request.tags.filter { it.isNotBlank() }
        val number = (page as? PageKey.Number)?.page ?: 1

        // «Горячее» и «Популярное» без поиска — popular_recent: одна страница, теги не принимает.
        val period = popularPeriod(request.sort)
        if (period != null && userTerms.isEmpty()) {
            if (number > 1) return PostsPage(emptyList(), 0, null, QueryPlan.EMPTY)
            val url = base(request.mode).newBuilder()
                .addPathSegments("post/popular_recent.json")
                .addQueryParameter("period", period)
                .build()
            val raw = decode(http.get(site, get(url), session.background), ListSerializer(MoebooruPostDto.serializer()))
            val categories = tags.categories(site, raw.flatMap { it.tags.splitTags() }.toSet())
            val posts = raw.map { it.toPost(categories, emptyMap(), request.mode) }
            return PostsPage(
                posts = posts.filter { it.isViewable && request.mode.allows(it.rating) },
                rawCount = raw.size,
                next = null,
                plan = QueryPlan.EMPTY,
            )
        }

        val plan = QueryPlanner.plan(
            siteId = site.id,
            rules = RULES,
            userTerms = userTerms,
            systemTerms = listOfNotNull(ratingTerm(request.mode), dateTerm(request.sort, userTerms)),
            orderTerm = orderTerm(request.sort),
            limit = session.tagLimit ?: defaultTagLimit,
            postCounts = countsIfNeeded(userTerms, session),
            moebooruLimits = true,
        )
        val url = postsUrl(request.mode, plan.query, pageSize, number)
        val response = decode(http.get(site, get(url), session.background), MoebooruPostsV2Dto.serializer())
        val categories = resolveCategories(response)
        val pools = response.poolPosts.groupBy({ it.postId }, { it.poolId })
        val matcher = LocalMatcher(plan.localTerms)
        val posts = response.posts.map { it.toPost(categories, pools, request.mode) }
        return PostsPage(
            posts = posts.filter { it.isViewable && request.mode.allows(it.rating) && matcher.matches(it) },
            rawCount = response.posts.size,
            next = if (response.posts.isEmpty()) null else PageKey.Number(number + 1),
            plan = plan,
        )
    }

    override suspend fun post(id: Long, session: Session): Post? =
        searchV2("id:$id", ContentMode.ALL, 1).firstOrNull()

    override suspend fun family(parentId: Long, mode: ContentMode, session: Session): List<Post> =
        searchV2(listOfNotNull("parent:$parentId", ratingTerm(mode)).joinToString(" "), mode, site.maxPageSize)
            .filter { mode.allows(it.rating) }
            .sortedBy { it.id }

    override suspend fun byMd5(md5: String, session: Session): Post? =
        searchV2("md5:$md5", ContentMode.ALL, 1).firstOrNull()

    override suspend fun autocomplete(query: String, limit: Int, session: Session): List<TagInfo> {
        val q = query.trim().removePrefix("-").removePrefix("~")
        if (q.isEmpty()) return emptyList()
        val url = base(ContentMode.ALL).newBuilder()
            .addPathSegments("tag.json")
            .addQueryParameter("name", if (q.endsWith("*")) q else "$q*")
            .addQueryParameter("order", "count")
            .addQueryParameter("limit", limit.toString())
            .build()
        return decode(http.get(site, get(url)), ListSerializer(MoebooruTagDto.serializer()))
            .map { TagInfo(it.name, TagCategory.fromMoebooru(it.type), it.count) }
    }

    override suspend fun tagInfo(names: Collection<String>, session: Session): List<TagInfo> =
        names.map { it.trim().lowercase() }.filter { it.isNotEmpty() && '*' !in it }.distinct().mapNotNull { name ->
            val url = base(ContentMode.ALL).newBuilder()
                .addPathSegments("tag.json")
                .addQueryParameter("name", name)
                .addQueryParameter("limit", "5")
                .build()
            decode(http.get(site, get(url)), ListSerializer(MoebooruTagDto.serializer()))
                .firstOrNull { it.name == name }
                ?.let { TagInfo(it.name, TagCategory.fromMoebooru(it.type), it.count) }
        }

    override suspend fun artist(name: String, session: Session): ArtistInfo? {
        val url = base(ContentMode.ALL).newBuilder()
            .addPathSegments("artist.json")
            .addQueryParameter("name", name)
            .build()
        val list = decode(http.get(site, get(url)), ListSerializer(MoebooruArtistDto.serializer()))
        val exact = list.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        val canonical = exact.aliasId?.let { id -> list.firstOrNull { it.id == id } } ?: exact
        val aliases = list.filter { it.aliasId == canonical.id }.map { it.name }
        return ArtistInfo(
            name = canonical.name,
            otherNames = (aliases + exact.name).distinct().filter { it != canonical.name },
            urls = canonical.urls,
        )
    }

    override suspend fun pools(post: Post, session: Session): List<PoolInfo> {
        val response = decode(
            http.get(site, get(postsUrl(ContentMode.ALL, "id:${post.id}", 1, 1)), session.background),
            MoebooruPostsV2Dto.serializer(),
        )
        return response.pools.map { PoolInfo(it.id, it.name, it.postCount) }
    }

    override fun postUrl(post: Post): String = "${site.baseUrl}/post/show/${post.id}"

    /**
     * У поста один голос от пользователя: сохранено → 3, лайк → 1, иначе голос снимается.
     * Неизвестные части ([like] или [save] = null) берутся из текущего голоса на сайте,
     * чтобы, например, лайк не затёр сохранение.
     */
    override suspend fun pushCollectionState(postId: Long, like: Boolean?, save: Boolean?, session: Session) {
        val creds = session.credentials as? Credentials.PasswordHash ?: throw BooruException.Unauthorized(site.id)
        val current = if (like == null || save == null) currentVote(postId, creds) else 0
        val saved = save ?: (current == 3)
        val liked = like ?: (current == 1 || current == 2)
        val target = when {
            saved -> 3
            liked -> 1
            else -> 0
        }
        if (target == current && (like == null || save == null)) return
        vote(postId, target, creds)
    }

    /** Избранное Moebooru — голос 3: `vote:3:логин order:vote`. */
    override suspend fun favorites(page: PageKey?, limit: Int, session: Session): PostsPage {
        val login = session.credentials?.login ?: throw BooruException.Unauthorized(site.id)
        val number = (page as? PageKey.Number)?.page ?: 1
        val response = decode(
            http.get(site, get(postsUrl(ContentMode.ALL, "vote:3:$login order:vote", limit.coerceIn(1, site.maxPageSize), number))),
            MoebooruPostsV2Dto.serializer(),
        )
        val categories = resolveCategories(response)
        val posts = response.posts.map { it.toPost(categories, emptyMap(), ContentMode.ALL) }.filter { it.isViewable }
        return PostsPage(posts, response.posts.size, if (response.posts.isEmpty()) null else PageKey.Number(number + 1), QueryPlan.EMPTY)
    }

    private suspend fun currentVote(postId: Long, creds: Credentials.PasswordHash): Int {
        val form = FormBody.Builder()
            .add("id", postId.toString())
            .add("login", creds.login)
            .add("password_hash", creds.passwordHash)
            .build()
        val url = base(ContentMode.ALL).newBuilder().addPathSegments("post/vote.json").build()
        val result = try {
            decode(http.send(site, Request.Builder().url(url).post(form).build()), MoebooruVoteDto.serializer())
        } catch (e: BooruException.Forbidden) {
            throw BooruException.Unauthorized(site.id)
        }
        return result.vote ?: 0
    }

    private suspend fun vote(postId: Long, score: Int, creds: Credentials.PasswordHash) {
        val form = FormBody.Builder()
            .add("id", postId.toString())
            .add("score", score.toString())
            .add("login", creds.login)
            .add("password_hash", creds.passwordHash)
            .build()
        val url = base(ContentMode.ALL).newBuilder().addPathSegments("post/vote.json").build()
        try {
            http.send(site, Request.Builder().url(url).post(form).build())
        } catch (e: BooruException.Forbidden) {
            throw BooruException.Unauthorized(site.id)
        } catch (e: BooruException.BadRequest) {
            // 423 «Already voted» — такой голос уже стоит.
            if (e.code != 423) throw e
        }
    }

    override fun credentials(login: String, secret: String): Credentials {
        val salt = requireNotNull(site.passwordSalt) { "${site.name} has no password salt" }
        return Credentials.PasswordHash(login.trim(), MoebooruAuth.passwordHash(salt, secret))
    }

    /**
     * Moebooru молча считает неверный вход анонимным, поэтому проверяем через `post/vote.json`
     * без `score`: для участника он возвращает текущий голос и ничего не меняет, аноним получает 403.
     */
    override suspend fun verify(credentials: Credentials): AccountInfo {
        require(credentials is Credentials.PasswordHash) { "Moebooru needs a password hash" }
        val userUrl = base(ContentMode.ALL).newBuilder()
            .addPathSegments("user.json")
            .addQueryParameter("name", credentials.login)
            .build()
        val user = decode(http.get(site, get(userUrl)), ListSerializer(MoebooruUserDto.serializer()))
            .firstOrNull { it.name.equals(credentials.login, ignoreCase = true) }
            ?: throw BooruException.InvalidCredentials(site.id)

        val latest = searchV2("", ContentMode.ALL, 1).firstOrNull()
            ?: throw BooruException.ServerError(site.id, 500)
        val voteUrl = base(ContentMode.ALL).newBuilder().addPathSegments("post/vote.json").build()
        val form = FormBody.Builder()
            .add("id", latest.id.toString())
            .add("login", credentials.login)
            .add("password_hash", credentials.passwordHash)
            .build()
        val vote = try {
            decode(http.send(site, Request.Builder().url(voteUrl).post(form).build()), MoebooruVoteDto.serializer())
        } catch (e: BooruException.Forbidden) {
            throw BooruException.InvalidCredentials(site.id)
        }
        if (!vote.success) throw BooruException.InvalidCredentials(site.id)
        return AccountInfo(login = user.name, userId = user.id, tagLimit = site.anonymousTagLimit)
    }

    /** Полный словарь тегов сайта для локального автодополнения и типов тегов. */
    suspend fun tagSummary(): MoebooruTagSummary {
        val url = base(ContentMode.ALL).newBuilder().addPathSegments("tag/summary.json").build()
        val dto = decode(http.get(site, get(url)), MoebooruTagSummaryDto.serializer())
        return MoebooruTagSummary.parse(dto.version, dto.data)
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun searchV2(query: String, mode: ContentMode, limit: Int): List<Post> {
        val response = decode(http.get(site, get(postsUrl(mode, query, limit, 1))), MoebooruPostsV2Dto.serializer())
        val categories = resolveCategories(response)
        val pools = response.poolPosts.groupBy({ it.postId }, { it.poolId })
        return response.posts.map { it.toPost(categories, pools, mode) }.filter { it.isViewable }
    }

    private fun postsUrl(mode: ContentMode, query: String, limit: Int, page: Int): HttpUrl =
        base(mode).newBuilder()
            .addPathSegments("post.json")
            .addQueryParameter("api_version", "2")
            .addQueryParameter("include_tags", "1")
            .addQueryParameter("include_pools", "1")
            .addQueryParameter("tags", query)
            .addQueryParameter("limit", limit.toString())
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()

    private suspend fun resolveCategories(response: MoebooruPostsV2Dto): Map<String, TagCategory> {
        val fromResponse = response.tags.mapValues { TagCategory.fromMoebooruName(it.value) }
        val missing = response.posts.flatMap { it.tags.splitTags() }.filter { it !in fromResponse }.toSet()
        return if (missing.isEmpty()) fromResponse else tags.categories(site, missing) + fromResponse
    }

    private suspend fun countsIfNeeded(userTerms: List<String>, session: Session): Map<String, Long> {
        val limit = session.tagLimit ?: defaultTagLimit ?: return emptyMap()
        val plain = userTerms.filter { RULES.metatagName(it) == null && !it.startsWith("-") && !it.startsWith("~") && '*' !in it }
        if (plain.size <= limit) return emptyMap()
        val known = tags.postCounts(site, plain)
        val missing = plain.filter { it !in known }
        if (missing.isEmpty()) return known
        return known + runCatching { tagInfo(missing) }.getOrDefault(emptyList())
            .mapNotNull { info -> info.postCount?.let { info.name to it } }
    }

    private fun get(url: HttpUrl): Request = Request.Builder().url(url).header("Accept", "application/json").build()

    private fun <T> decode(body: String, serializer: KSerializer<T>): T = try {
        BooruJson.decodeFromString(serializer, body)
    } catch (e: SerializationException) {
        throw BooruException.Malformed(site.id, e)
    } catch (e: IllegalArgumentException) {
        throw BooruException.Malformed(site.id, e)
    }

    private fun MoebooruPostDto.toPost(
        categories: Map<String, TagCategory>,
        pools: Map<Long, List<Long>>,
        mode: ContentMode,
    ): Post {
        val root = site.baseUrlFor(mode)
        val file = fileUrl?.let { absolute(it, root) }
        val jpeg = jpegUrl?.let { absolute(it, root) }
        val sample = sampleUrl?.let { absolute(it, root) }
        val ext = (fileExt ?: file?.substringBefore('?')?.substringAfterLast('.', ""))?.lowercase()?.ifEmpty { null }

        // «Облегчённая»: JPEG-версия PNG-оригинала, иначе sample, иначе сам оригинал.
        val (light, lightSize) = when {
            jpeg != null && jpeg != file && (jpegFileSize ?: 0) > 0 -> jpeg to jpegFileSize
            sample != null && sample != file -> sample to sampleFileSize?.takeIf { it > 0 }
            else -> file to fileSize
        }
        val tagNames = tags.splitTags()
        return Post(
            site = site.id,
            id = id,
            md5 = md5,
            createdAt = createdAt * 1000,
            rating = Rating.fromMoebooru(rating),
            score = score,
            favCount = null,
            width = width,
            height = height,
            fileExt = ext,
            fileSize = fileSize,
            previewUrl = previewUrl?.let { absolute(it, root) },
            sampleUrl = sample ?: file,
            sampleWidth = sampleWidth?.takeIf { it > 0 } ?: width,
            sampleHeight = sampleHeight?.takeIf { it > 0 } ?: height,
            lightUrl = light,
            lightSize = lightSize,
            lightExt = light?.substringBefore('?')?.substringAfterLast('.', "")?.lowercase()?.ifEmpty { null },
            fileUrl = file,
            tags = PostTags.fromCategorized(tagNames.map { it to (categories[it] ?: TagCategory.GENERAL) }),
            source = source?.takeIf { it.isNotBlank() },
            parentId = parentId,
            hasChildren = hasChildren,
            poolIds = pools[id].orEmpty(),
            status = when (status) {
                "pending" -> PostStatus.PENDING
                "flagged" -> PostStatus.FLAGGED
                "deleted" -> PostStatus.DELETED
                else -> PostStatus.ACTIVE
            },
        )
    }

    companion object {
        /** Из moebooru app/models/tag/parse_methods.rb; метатеги лимит не тратят. */
        val RULES: TagRules = run {
            val meta = setOf(
                "ratio", "unlocked", "deleted", "ext", "user", "sub", "vote", "fav", "md5", "rating", "width",
                "height", "mpixels", "score", "source", "id", "date", "pool", "parent", "order", "change", "holds",
                "pending", "shown", "limit",
            )
            TagRules(metatags = meta, freeMetatags = meta)
        }

        fun ratingTerm(mode: ContentMode): String? = when (mode) {
            ContentMode.SFW -> "rating:s"
            ContentMode.NSFW -> "-rating:s"
            ContentMode.ALL -> null
        }

        fun orderTerm(sort: SortOrder): String? = when (sort) {
            SortOrder.NEW, SortOrder.FAVCOUNT -> null
            SortOrder.HOT, SortOrder.POPULAR_DAY, SortOrder.POPULAR_WEEK, SortOrder.POPULAR_MONTH,
            SortOrder.POPULAR_YEAR, SortOrder.BEST -> "order:score"
            SortOrder.MPIXELS -> "order:mpixels"
            SortOrder.LANDSCAPE -> "order:landscape"
            SortOrder.PORTRAIT -> "order:portrait"
            SortOrder.RANDOM -> "order:random"
        }

        internal fun popularPeriod(sort: SortOrder): String? = when (sort) {
            SortOrder.HOT, SortOrder.POPULAR_DAY -> "1d"
            SortOrder.POPULAR_WEEK -> "1w"
            SortOrder.POPULAR_MONTH -> "1m"
            SortOrder.POPULAR_YEAR -> "1y"
            else -> null
        }

        private fun String.splitTags(): List<String> =
            if (isBlank()) emptyList() else trim().split(' ').filter { it.isNotEmpty() }

        internal fun absolute(url: String, root: String): String = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> root.trimEnd('/') + url
            else -> url
        }
    }

    /** «Популярное» с поиском: `order:score date:>=ДАТА`. */
    private fun dateTerm(sort: SortOrder, userTerms: List<String>): String? {
        if (userTerms.isEmpty()) return null
        val since = when (sort) {
            SortOrder.HOT, SortOrder.POPULAR_DAY -> today().minusDays(1)
            SortOrder.POPULAR_WEEK -> today().minusWeeks(1)
            SortOrder.POPULAR_MONTH -> today().minusMonths(1)
            SortOrder.POPULAR_YEAR -> today().minusYears(1)
            else -> return null
        }
        return "date:>=$since"
    }
}
