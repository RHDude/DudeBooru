package app.dudebooru.booru.engine

import app.dudebooru.booru.model.AccountInfo
import app.dudebooru.booru.model.ArtistInfo
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.booru.model.TagInfo
import app.dudebooru.booru.site.SiteConfig

/** Что показывать: теги поиска (как ввёл человек, уже с подчёркиваниями), сортировка, режим. */
data class FeedRequest(
    val tags: List<String> = emptyList(),
    val sort: SortOrder = SortOrder.NEW,
    val mode: ContentMode = ContentMode.SFW,
)

data class PoolInfo(val id: Long, val name: String, val postCount: Int? = null) {
    val displayName: String get() = name.replace('_', ' ')
}

/** Курсор страницы: номер или «посты старше id» (Danbooru `page=b123` для новых). */
sealed interface PageKey {
    data class Number(val page: Int) : PageKey
    data class Before(val id: Long) : PageKey
}

/**
 * Страница ленты. [posts] уже прошли локальную проверку: рейтинг под режим и теги,
 * которые не влезли в лимит. [rawCount] — сколько пришло с сервера до проверки.
 */
data class PostsPage(
    val posts: List<Post>,
    val rawCount: Int,
    val next: PageKey?,
    val plan: QueryPlan,
)

/**
 * Аккаунт, от имени которого идёт запрос, и его лимит тегов (`null` — без лимита).
 * [background] — запрос не для ленты (аватарки и т.п.), уступает ей очередь.
 */
data class Session(
    val credentials: Credentials? = null,
    val tagLimit: Int? = null,
    val background: Boolean = false,
)

/**
 * Словарь тегов приложения: число постов для выбора редких тегов и категории для постов,
 * пришедших без типов тегов (популярное Moebooru).
 */
interface TagLookup {
    suspend fun postCounts(site: SiteConfig, names: Collection<String>): Map<String, Long>
    suspend fun categories(site: SiteConfig, names: Collection<String>): Map<String, TagCategory>

    object None : TagLookup {
        override suspend fun postCounts(site: SiteConfig, names: Collection<String>) = emptyMap<String, Long>()
        override suspend fun categories(site: SiteConfig, names: Collection<String>) = emptyMap<String, TagCategory>()
    }
}

interface BooruEngine {
    val site: SiteConfig

    /** Сортировки, которые поддерживает источник, в порядке показа в листе. */
    val supportedSorts: List<SortOrder>

    /** Лимит тегов по умолчанию, если аккаунт не сообщил свой. */
    val defaultTagLimit: Int? get() = site.anonymousTagLimit

    suspend fun posts(request: FeedRequest, page: PageKey?, limit: Int, session: Session = Session()): PostsPage

    suspend fun post(id: Long, session: Session = Session()): Post?

    /** Родитель вместе с детьми (`parent:ID`) — для каруселей. */
    suspend fun family(parentId: Long, mode: ContentMode, session: Session = Session()): List<Post>

    /** Та же картинка по md5 — для «Найти похожие» на других источниках. */
    suspend fun byMd5(md5: String, session: Session = Session()): Post?

    suspend fun autocomplete(query: String, limit: Int = 10, session: Session = Session()): List<TagInfo>

    /** Категории и число постов для точных имён тегов. */
    suspend fun tagInfo(names: Collection<String>, session: Session = Session()): List<TagInfo>

    suspend fun artist(name: String, session: Session = Session()): ArtistInfo?

    /** Пулы (серии, артбуки), в которые входит пост. */
    suspend fun pools(post: Post, session: Session = Session()): List<PoolInfo>

    /** Ссылка на пост на сайте. */
    fun postUrl(post: Post): String

    /**
     * Отправить на сайт состояние поста в коллекциях. `null` — эту часть не трогаем.
     * Danbooru: [like] — upvote / снять голос, [save] — избранное.
     * Moebooru: у поста один голос — сохранено → 3, лайк → 1, иначе голос снимается.
     */
    suspend fun pushCollectionState(postId: Long, like: Boolean?, save: Boolean?, session: Session)

    /** Избранное аккаунта на сайте — для импорта в «Сохранённые» при первом входе. */
    suspend fun favorites(page: PageKey?, limit: Int, session: Session): PostsPage

    /** Собирает данные для входа из того, что ввёл человек (для Moebooru сразу хеширует пароль). */
    fun credentials(login: String, secret: String): Credentials

    /** Проверочный запрос. Бросает [app.dudebooru.booru.net.BooruException.InvalidCredentials]. */
    suspend fun verify(credentials: Credentials): AccountInfo
}
