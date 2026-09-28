package app.dudebooru.booru.engine.danbooru

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
import app.dudebooru.booru.model.MediaType
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.Credentials as OkCredentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.OffsetDateTime

class DanbooruEngine(
    override val site: SiteConfig,
    private val http: BooruHttp,
    private val tags: TagLookup = TagLookup.None,
) : BooruEngine {

    override val supportedSorts: List<SortOrder> = listOf(
        SortOrder.NEW,
        SortOrder.HOT,
        SortOrder.POPULAR_DAY,
        SortOrder.POPULAR_WEEK,
        SortOrder.POPULAR_MONTH,
        SortOrder.BEST,
        SortOrder.FAVCOUNT,
        SortOrder.MPIXELS,
        SortOrder.LANDSCAPE,
        SortOrder.PORTRAIT,
        SortOrder.RANDOM,
    )

    private val base: HttpUrl = site.baseUrl.toHttpUrl()

    override suspend fun posts(request: FeedRequest, page: PageKey?, limit: Int, session: Session): PostsPage {
        val pageSize = limit.coerceIn(1, site.maxPageSize)
        val userTerms = request.tags.filter { it.isNotBlank() }

        // «Популярное» без поиска — отдельный эндпоинт, он не принимает теги; режим проверяем у себя.
        if (request.sort.isPopular && userTerms.isEmpty()) {
            val scale = when (request.sort) {
                SortOrder.POPULAR_DAY -> "day"
                SortOrder.POPULAR_WEEK -> "week"
                else -> "month"
            }
            val number = (page as? PageKey.Number)?.page ?: 1
            val url = base.newBuilder()
                .addPathSegments("explore/posts/popular.json")
                .addQueryParameter("scale", scale)
                .addQueryParameter("page", number.toString())
                .addQueryParameter("limit", pageSize.toString())
                .build()
            val raw = getList(url, session, ListSerializer(DanbooruPostDto.serializer()))
            val posts = raw.map { it.toPost() }
            return PostsPage(
                posts = posts.filter { it.isViewable && request.mode.allows(it.rating) },
                rawCount = raw.size,
                next = if (raw.isEmpty()) null else PageKey.Number(number + 1),
                plan = QueryPlan.EMPTY,
            )
        }

        val tagLimit = session.tagLimit ?: defaultTagLimit
        val plan = QueryPlanner.plan(
            siteId = site.id,
            rules = RULES,
            userTerms = userTerms,
            systemTerms = listOfNotNull(ratingTerm(request.mode)) + listOfNotNull(ageTerm(request.sort, userTerms)),
            orderTerm = orderTerm(request.sort),
            limit = tagLimit,
            postCounts = countsIfNeeded(userTerms, tagLimit),
        )

        val url = base.newBuilder()
            .addPathSegments("posts.json")
            .addQueryParameter("tags", plan.query)
            .addQueryParameter("limit", pageSize.toString())
            .apply {
                when (page) {
                    is PageKey.Before -> addQueryParameter("page", "b${page.id}")
                    is PageKey.Number -> addQueryParameter("page", page.page.toString())
                    null -> Unit
                }
            }
            .build()

        val raw = try {
            getList(url, session, ListSerializer(DanbooruPostDto.serializer()))
        } catch (e: BooruException.BadRequest) {
            throw tagLimitError(e, tagLimit) ?: e
        }
        val matcher = LocalMatcher(plan.localTerms)
        val posts = raw.map { it.toPost() }
        val visible = posts.filter { it.isViewable && request.mode.allows(it.rating) && matcher.matches(it) }

        // Порядок по умолчанию (новые сверху) листаем курсором «старше id»:
        // свежие загрузки не сдвигают страницы и не дают дублей.
        val defaultOrder = plan.serverTerms.none { RULES.metatagName(it) in ORDER_METATAGS }
        val next: PageKey? = when {
            raw.isEmpty() -> null
            defaultOrder -> PageKey.Before(raw.minOf { it.id })
            else -> PageKey.Number(((page as? PageKey.Number)?.page ?: 1) + 1)
        }
        return PostsPage(visible, raw.size, next, plan)
    }

    override suspend fun post(id: Long, session: Session): Post? = try {
        val url = base.newBuilder().addPathSegments("posts/$id.json").build()
        getOne(url, session, DanbooruPostDto.serializer()).toPost()
    } catch (e: BooruException.BadRequest) {
        if (e.code == 404) null else throw e
    }

    override suspend fun family(parentId: Long, mode: ContentMode, session: Session): List<Post> {
        val terms = listOfNotNull("parent:$parentId", ratingTerm(mode)).joinToString(" ")
        val url = base.newBuilder()
            .addPathSegments("posts.json")
            .addQueryParameter("tags", "$terms order:id_asc")
            .addQueryParameter("limit", "100")
            .build()
        return getList(url, session, ListSerializer(DanbooruPostDto.serializer()))
            .map { it.toPost() }
            .filter { it.isViewable && mode.allows(it.rating) }
    }

    override suspend fun byMd5(md5: String, session: Session): Post? {
        val url = base.newBuilder()
            .addPathSegments("posts.json")
            .addQueryParameter("tags", "md5:$md5")
            .addQueryParameter("limit", "1")
            .build()
        return getList(url, session, ListSerializer(DanbooruPostDto.serializer())).firstOrNull()?.toPost()
    }

    override suspend fun autocomplete(query: String, limit: Int, session: Session): List<TagInfo> {
        val q = query.trim().removePrefix("-").removePrefix("~")
        if (q.isEmpty()) return emptyList()
        val url = base.newBuilder()
            .addPathSegments("autocomplete.json")
            .addQueryParameter("search[query]", q)
            .addQueryParameter("search[type]", "tag_query")
            .addQueryParameter("limit", limit.toString())
            .build()
        return getList(url, session, ListSerializer(DanbooruAutocompleteDto.serializer()))
            .filter { it.type == null || it.type.startsWith("tag") }
            .map {
                TagInfo(
                    name = it.value,
                    category = TagCategory.fromDanbooru(it.category ?: 0),
                    postCount = it.postCount,
                    antecedent = it.antecedent,
                )
            }
    }

    override suspend fun tagInfo(names: Collection<String>, session: Session): List<TagInfo> {
        val clean = names.map { it.trim().lowercase() }.filter { it.isNotEmpty() && ',' !in it }.distinct()
        if (clean.isEmpty()) return emptyList()
        return clean.chunked(100).flatMap { chunk ->
            val url = base.newBuilder()
                .addPathSegments("tags.json")
                .addQueryParameter("search[name_comma]", chunk.joinToString(","))
                .addQueryParameter("only", "name,post_count,category")
                .addQueryParameter("limit", chunk.size.toString())
                .build()
            getList(url, session, ListSerializer(DanbooruTagDto.serializer()))
                .map { TagInfo(it.name, TagCategory.fromDanbooru(it.category), it.postCount) }
        }
    }

    override suspend fun artist(name: String, session: Session): ArtistInfo? {
        val url = base.newBuilder()
            .addPathSegments("artists.json")
            .addQueryParameter("search[name]", name)
            .addQueryParameter("only", "name,other_names,group_name,is_banned,urls")
            .build()
        val artist = getList(url, session, ListSerializer(DanbooruArtistDto.serializer())).firstOrNull() ?: return null
        return ArtistInfo(
            name = artist.name,
            otherNames = artist.otherNames,
            groupName = artist.groupName?.takeIf { it.isNotBlank() },
            urls = artist.urls.filter { it.isActive }.map { it.url },
            isBanned = artist.isBanned,
        )
    }

    override suspend fun pools(post: Post, session: Session): List<PoolInfo> {
        val url = base.newBuilder()
            .addPathSegments("pools.json")
            .addQueryParameter("search[post_ids_include_any]", post.id.toString())
            .addQueryParameter("only", "id,name,post_count")
            .build()
        return getList(url, session, ListSerializer(DanbooruPoolDto.serializer())).map { PoolInfo(it.id, it.name, it.postCount) }
    }

    override fun postUrl(post: Post): String = "${site.baseUrl}/posts/${post.id}"

    /** Upvote на Danbooru публично меняет score, поэтому дублирование лайков — только по желанию. */
    override suspend fun pushCollectionState(postId: Long, like: Boolean?, save: Boolean?, session: Session) {
        val creds = session.credentials as? Credentials.ApiKey ?: throw BooruException.Unauthorized(site.id)
        if (like != null) {
            val url = base.newBuilder().addPathSegments("posts/$postId/votes.json").apply { if (like) addQueryParameter("score", "1") }.build()
            val builder = Request.Builder().url(url).header("Authorization", OkCredentials.basic(creds.login, creds.apiKey))
            val request = if (like) builder.post(EMPTY_BODY).build() else builder.delete().build()
            tolerate(404, 422) { http.send(site, request) }
        }
        if (save != null) {
            val request = if (save) {
                val url = base.newBuilder().addPathSegments("favorites.json").addQueryParameter("post_id", postId.toString()).build()
                Request.Builder().url(url).post(EMPTY_BODY)
            } else {
                // В Danbooru избранное удаляется по id поста.
                Request.Builder().url(base.newBuilder().addPathSegments("favorites/$postId.json").build()).delete()
            }.header("Authorization", OkCredentials.basic(creds.login, creds.apiKey)).build()
            tolerate(404, 422) { http.send(site, request) }
        }
    }

    /** Избранное — `ordfav:логин`: в порядке добавления, новые сверху. */
    override suspend fun favorites(page: PageKey?, limit: Int, session: Session): PostsPage {
        val login = session.credentials?.login ?: throw BooruException.Unauthorized(site.id)
        val number = (page as? PageKey.Number)?.page ?: 1
        val url = base.newBuilder()
            .addPathSegments("posts.json")
            .addQueryParameter("tags", "ordfav:$login")
            .addQueryParameter("limit", limit.coerceIn(1, site.maxPageSize).toString())
            .addQueryParameter("page", number.toString())
            .build()
        val raw = getList(url, session, ListSerializer(DanbooruPostDto.serializer()))
        return PostsPage(raw.map { it.toPost() }.filter { it.isViewable }, raw.size, if (raw.isEmpty()) null else PageKey.Number(number + 1), QueryPlan.EMPTY)
    }

    /** Повторная отправка того же состояния («уже в избранном», «голоса нет») — не ошибка. */
    private suspend fun tolerate(vararg codes: Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: BooruException.BadRequest) {
            if (e.code !in codes) throw e
        }
    }

    override fun credentials(login: String, secret: String): Credentials =
        Credentials.ApiKey(login.trim(), secret.trim())

    override suspend fun verify(credentials: Credentials): AccountInfo {
        require(credentials is Credentials.ApiKey) { "Danbooru needs an API key" }
        val url = base.newBuilder().addPathSegments("profile.json").build()
        val profile = try {
            getOne(url, Session(credentials), DanbooruProfileDto.serializer())
        } catch (e: BooruException.Unauthorized) {
            throw BooruException.InvalidCredentials(site.id)
        } catch (e: BooruException.Forbidden) {
            throw BooruException.InvalidCredentials(site.id)
        }
        // Без входа сайт отвечает анонимным профилем с id = null.
        if (profile.id == null) throw BooruException.InvalidCredentials(site.id)
        val limit = (profile.tagQueryLimit as? JsonPrimitive)?.intOrNull
        return AccountInfo(
            login = profile.name ?: credentials.login,
            userId = profile.id,
            level = profile.level,
            levelName = profile.levelString,
            tagLimit = limit,
            blacklistedTags = profile.blacklistedTags.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() },
            favoriteCount = profile.favoriteCount,
        )
    }

    // ---------------------------------------------------------------------------------------

    private fun request(url: HttpUrl, session: Session): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .apply {
            val creds = session.credentials
            if (creds is Credentials.ApiKey) header("Authorization", OkCredentials.basic(creds.login, creds.apiKey))
        }
        .build()

    private suspend fun <T> getList(url: HttpUrl, session: Session, serializer: KSerializer<List<T>>): List<T> {
        val body = http.get(site, request(url, session), session.background)
        return decode(body, serializer)
    }

    private suspend fun <T> getOne(url: HttpUrl, session: Session, serializer: KSerializer<T>): T {
        val body = http.get(site, request(url, session), session.background)
        return decode(body, serializer)
    }

    private fun <T> decode(body: String, serializer: KSerializer<T>): T = try {
        BooruJson.decodeFromString(serializer, body)
    } catch (e: SerializationException) {
        throw BooruException.Malformed(site.id, e)
    } catch (e: IllegalArgumentException) {
        throw BooruException.Malformed(site.id, e)
    }

    private suspend fun countsIfNeeded(userTerms: List<String>, tagLimit: Int?): Map<String, Long> {
        if (tagLimit == null) return emptyMap()
        val plain = userTerms.filter { RULES.metatagName(it) == null && !it.startsWith("-") && !it.startsWith("~") && '*' !in it }
        // Считать редкость имеет смысл, только если всё не помещается.
        if (userTerms.size + 1 <= tagLimit || plain.size <= 1) return emptyMap()
        val known = tags.postCounts(site, plain)
        val missing = plain.filter { it !in known }
        if (missing.isEmpty()) return known
        val fetched = runCatching { tagInfo(missing) }.getOrDefault(emptyList())
            .mapNotNull { info -> info.postCount?.let { info.name to it } }
        return known + fetched
    }

    private fun tagLimitError(e: BooruException.BadRequest, limit: Int?): BooruException? {
        val msg = e.serverMessage ?: return null
        if (e.code != 422 || !msg.contains("more than", ignoreCase = true) || !msg.contains("tag", ignoreCase = true)) return null
        val serverLimit = Regex("""more than (\d+)""").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        val actual = serverLimit ?: limit ?: 0
        return BooruException.TagLimitExceeded(site.id, actual, actual + 1)
    }

    private fun DanbooruPostDto.toPost(): Post {
        val variants = mediaAsset?.variants.orEmpty().associateBy { it.type }
        val ext = fileExt?.lowercase()
        val media = MediaType.fromExt(ext)
        val sample = variants["sample"]
        val feed = when {
            media == MediaType.IMAGE && sample != null -> sample
            media == MediaType.IMAGE -> null
            else -> variants["720x720"] ?: variants["360x360"]
        }
        return Post(
            site = site.id,
            id = id,
            md5 = md5,
            createdAt = createdAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L,
            rating = Rating.fromDanbooru(rating),
            score = score,
            favCount = favCount,
            width = imageWidth,
            height = imageHeight,
            fileExt = ext,
            fileSize = fileSize,
            previewUrl = variants["360x360"]?.url ?: previewFileUrl,
            sampleUrl = feed?.url ?: if (media == MediaType.IMAGE) fileUrl else previewFileUrl,
            sampleWidth = feed?.width ?: imageWidth.takeIf { media == MediaType.IMAGE },
            sampleHeight = feed?.height ?: imageHeight.takeIf { media == MediaType.IMAGE },
            lightUrl = largeFileUrl ?: fileUrl,
            lightSize = if (largeFileUrl == null || largeFileUrl == fileUrl) fileSize else null,
            lightExt = largeFileUrl?.substringAfterLast('.', "")?.substringBefore('?')?.ifEmpty { null } ?: ext,
            fileUrl = fileUrl,
            tags = PostTags(
                artist = tagsArtist.splitTags(),
                copyright = tagsCopyright.splitTags(),
                character = tagsCharacter.splitTags(),
                general = tagsGeneral.splitTags(),
                meta = tagsMeta.splitTags(),
            ),
            source = source?.takeIf { it.isNotBlank() },
            parentId = parentId,
            hasChildren = hasActiveChildren ?: hasChildren,
            pixivId = pixivId,
            status = when {
                isBanned -> PostStatus.BANNED
                isDeleted -> PostStatus.DELETED
                isFlagged -> PostStatus.FLAGGED
                isPending -> PostStatus.PENDING
                else -> PostStatus.ACTIVE
            },
        )
    }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        /** Из исходников Danbooru: app/logical/post_query_builder.rb и post_query.rb. */
        val RULES = TagRules(
            metatags = setOf(
                "user", "approver", "commenter", "comm", "noter", "noteupdater", "artcomm", "commentaryupdater",
                "flagger", "appealer", "upvote", "downvote", "fav", "ordvote", "ordfav", "favgroup", "ordfavgroup",
                "reacted", "pool", "ordpool", "note", "comment", "commentary", "id", "rating", "source", "status",
                "filetype", "disapproved", "parent", "child", "search", "embedded", "md5", "pixelhash", "width",
                "height", "mpixels", "ratio", "score", "upvotes", "downvotes", "favcount", "filesize", "date", "age",
                "order", "limit", "tagcount", "pixiv_id", "pixiv", "unaliased", "exif", "duration", "random", "is",
                "has", "ai", "gentags", "arttags", "copytags", "chartags", "metatags",
                "comment_count", "deleted_comment_count", "active_comment_count", "note_count", "deleted_note_count",
                "active_note_count", "flag_count", "child_count", "deleted_child_count", "active_child_count",
                "pool_count", "deleted_pool_count", "active_pool_count", "series_pool_count", "collection_pool_count",
                "appeal_count", "approval_count", "replacement_count", "disapproval_count",
                "comments", "deleted_comments", "active_comments", "notes", "deleted_notes", "active_notes", "flags",
                "children", "deleted_children", "active_children", "pools", "deleted_pools", "active_pools",
                "series_pools", "collection_pools", "appeals", "approvals", "replacements", "disapprovals",
            ),
            freeMetatags = setOf(
                "status", "rating", "limit", "is", "id", "date", "age", "filesize", "filetype", "parent", "child",
                "md5", "width", "height", "duration", "mpixels", "ratio", "score", "upvote", "downvotes", "favcount",
                "embedded", "tagcount", "pixiv_id", "pixiv",
            ),
        )

        private val ORDER_METATAGS = setOf("order", "ordfav", "ordvote", "ordfavgroup", "ordpool")

        fun ratingTerm(mode: ContentMode): String? = when (mode) {
            ContentMode.SFW -> "rating:g,s"
            ContentMode.NSFW -> "rating:q,e"
            ContentMode.ALL -> null
        }

        fun orderTerm(sort: SortOrder): String? = when (sort) {
            SortOrder.NEW -> null
            SortOrder.HOT -> "order:rank"
            SortOrder.POPULAR_DAY, SortOrder.POPULAR_WEEK, SortOrder.POPULAR_MONTH, SortOrder.POPULAR_YEAR,
            SortOrder.BEST -> "order:score"
            SortOrder.FAVCOUNT -> "order:favcount"
            SortOrder.MPIXELS -> "order:mpixels"
            SortOrder.LANDSCAPE -> "order:landscape"
            SortOrder.PORTRAIT -> "order:portrait"
            SortOrder.RANDOM -> "order:random"
        }

        /** «Популярное» с поиском превращается в `order:score age:<1w`; `age:` слот не тратит. */
        fun ageTerm(sort: SortOrder, userTerms: List<String>): String? {
            if (!sort.isPopular || userTerms.isEmpty()) return null
            return when (sort) {
                SortOrder.POPULAR_DAY -> "age:<1d"
                SortOrder.POPULAR_WEEK -> "age:<1w"
                SortOrder.POPULAR_MONTH -> "age:<1mo"
                SortOrder.POPULAR_YEAR -> "age:<1y"
                else -> null
            }
        }

        private fun String.splitTags(): List<String> =
            if (isBlank()) emptyList() else trim().split(' ').filter { it.isNotEmpty() }
    }
}
