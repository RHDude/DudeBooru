package app.dudebooru.ui.feed

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.dudebooru.booru.engine.PoolInfo
import app.dudebooru.booru.model.Post

/** Всё, что можно сделать с постом из ленты и просмотра. */
interface PostActions {
    fun open(controller: FeedController, post: Post)
    fun toggleLike(post: Post)
    fun doubleTapLike(post: Post)
    fun toggleSave(post: Post)
    fun share(post: Post)
    fun openArtist(post: Post, artist: String? = null)
    fun download(post: Post, original: Boolean)
    fun downloadAll(posts: List<Post>)
    fun copyLink(post: Post)
    fun copyDirectLink(post: Post)
    fun copyImage(post: Post)
    fun openOnSite(post: Post)
    fun findSimilar(post: Post)
    fun notInterested(post: Post)
    fun makeAvatar(post: Post)

    /** Картинка поста — фоном своей темы. */
    fun useAsBackground(post: Post) {}
    suspend fun pools(post: Post): List<PoolInfo>
    fun openPool(post: Post, pool: PoolInfo)
    fun searchTag(post: Post, tag: String, add: Boolean)
    fun hideTag(post: Post, tag: String)

    /** Причина рекомендации — открыть эти теги. */
    fun searchTags(site: String, tags: List<String>)

    /** Вернуть негативный тег (из «скрыто N»). */
    fun restoreTag(expression: String)

    /** Пост показан в просмотре — в «Историю». */
    fun viewed(post: Post)

    /** Снять цензуру с одного поста. */
    fun reveal(post: Post)

    /** Адрес сайта поста — для ссылок на вики. */
    fun postUrlBase(post: Post): String

    /** Аватарка художника: из памяти сразу, иначе догрузка. */
    fun cachedAvatar(post: Post): String?
    suspend fun loadAvatar(post: Post): String?

    /** Android 13+: спросить разрешение на уведомления, если его ещё нет. */
    fun askNotifications() {}
}

/** Лайки и сохранённые — ключи `site:id`, чтобы карточки перерисовывались без запросов в базу. */
@Immutable
data class Collections(val liked: Set<String> = emptySet(), val saved: Set<String> = emptySet())

val LocalCollections = staticCompositionLocalOf { Collections() }

val LocalFeedPrefs = staticCompositionLocalOf { app.dudebooru.data.settings.FeedPrefs() }

/** md5 уже скачанного — отметка в ленте. */
val LocalDownloaded = staticCompositionLocalOf { emptySet<String>() }
