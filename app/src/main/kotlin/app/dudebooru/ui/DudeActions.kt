package app.dudebooru.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.dudebooru.R
import app.dudebooru.booru.engine.PoolInfo
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.site.EngineType
import app.dudebooru.data.db.NegativeTagEntity
import app.dudebooru.ui.common.errorText
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.feed.PostActions
import app.dudebooru.ui.main.MainViewModel
import app.dudebooru.ui.main.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Действия с постом из ленты и просмотра. */
class DudeActions(
    private val context: Context,
    private val vm: MainViewModel,
    private val scope: CoroutineScope,
    /** Android 13+: спросить разрешение на уведомления (перед первой загрузкой, при подписке на художника). */
    private val requestNotificationPermission: () -> Unit = {},
) : PostActions, app.dudebooru.ui.feed.FeedEnvironment {

    override fun askNotifications() = requestNotificationPermission()
    private val c get() = vm.c

    // --- пустые экраны и ошибки ленты ---

    override val online get() = c.connectivity.online
    override val gameRecord get() = vm.gameRecord

    override fun saveRecord(score: Int) = vm.saveGameRecord(score)

    override fun openNegativeTags() = vm.navigate(Route.NegativeTags)

    override fun openNetworkSettings() = vm.navigate(Route.Settings(app.dudebooru.ui.main.SettingsPage.NETWORK))

    override fun openAccountSettings() = vm.navigate(Route.Settings(app.dudebooru.ui.main.SettingsPage.ACCOUNTS))

    override fun openSite(site: app.dudebooru.booru.site.SiteConfig) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site.baseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Похожие теги для «ничего не нашли»: словарь по укороченному префиксу, ближайшие по написанию. */
    override suspend fun similarTags(site: app.dudebooru.booru.site.SiteConfig, tag: String): List<String> {
        val found = LinkedHashSet<String>()
        var prefix = tag
        while (prefix.length >= 3 && found.size < 12) {
            c.tags.localSuggestions(site, prefix, 20).forEach { if (it.name != tag) found += it.name }
            if (found.isEmpty()) runCatching { c.tags.remoteSuggestions(site, prefix, 20) }.getOrNull()?.forEach { if (it.name != tag) found += it.name }
            if (found.isNotEmpty()) break
            prefix = prefix.dropLast(if (prefix.length > 6) 2 else 1)
        }
        return found.sortedBy { app.dudebooru.ui.common.editDistance(it, tag) }.take(5)
    }

    override fun search(site: app.dudebooru.booru.site.SiteConfig, tags: List<String>) {
        if (vm.stack.last() is Route.Results) vm.back()
        vm.openSearchResults(site.id, tags)
    }

    /** Картинка поста — фоном своей темы: берём из кэша картинок и открываем редактор. */
    override fun useAsBackground(post: Post) {
        scope.launch {
            val ok = vm.setThemeBackgroundFrom(context, post.sampleUrl ?: post.fileUrl ?: return@launch)
            if (ok) vm.navigate(Route.ThemeEditor) else toast(context.getString(R.string.theme_bg_failed))
        }
    }

    /** Пост, для которого открыт лист «Не интересно…». */
    val notInterestedPost = MutableStateFlow<Post?>(null)

    /** Лист быстрых действий по долгому нажатию: пост и его карусель. */
    val quickPost = MutableStateFlow<Pair<Post, List<Post>>?>(null)

    override fun quickActions(post: Post, group: List<Post>) {
        quickPost.value = post to group
    }

    private fun site(post: Post) = requireNotNull(c.registry.site(post.site))
    private fun engine(post: Post) = c.registry.engine(site(post))
    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** Сначала карточка получает общий элемент, кадром позже открывается просмотр — картинка перелетает из неё. */
    override fun open(controller: FeedController, post: Post) {
        // Открыл пост сам — он показан; соседние в просмотре остаются под цензурой, пока не нажмёшь.
        if (!vm.censorPrefs.value.inViewer) vm.reveal(post)
        vm.sharedKey.value = post.key
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            vm.navigate(Route.Viewer(controller.id, post.key))
        }
    }

    override fun toggleLike(post: Post) = vm.toggleLike(post)

    override fun doubleTapLike(post: Post) = vm.like(post)

    override fun toggleSave(post: Post) = vm.toggleSave(post)

    override fun share(post: Post) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, engine(post).postUrl(post))
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openArtist(post: Post, artist: String?) {
        val name = artist ?: post.tags.artist.firstOrNull() ?: return
        vm.openArtist(post.site, name)
    }

    override fun download(post: Post, original: Boolean) = enqueue(listOf(post), original)

    override fun downloadAll(posts: List<Post>) = enqueue(posts, original = true)

    private fun enqueue(posts: List<Post>, original: Boolean) {
        askNotifications()
        scope.launch {
            val result = c.downloads.enqueue(posts, original)
            toast(
                when {
                    result.added == 0 && result.skipped > 0 -> context.getString(R.string.download_already)
                    result.skipped > 0 -> context.getString(R.string.download_queued_skipped, result.added, result.skipped)
                    else -> context.getString(R.string.download_queued, result.added)
                },
            )
        }
    }

    private fun copy(label: String, text: String, toastRes: Int) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        toast(context.getString(toastRes))
    }

    override fun copyLink(post: Post) = copy("post", engine(post).postUrl(post), R.string.copied_link)

    override fun copyDirectLink(post: Post) {
        val url = post.fileUrl ?: return
        copy("file", url, R.string.copied_link)
    }

    override fun copyImage(post: Post) {
        scope.launch {
            try {
                val uri = c.downloader.toCache(post)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "image", uri))
                toast(context.getString(R.string.copied_image))
            } catch (e: Exception) {
                toast(context.errorText(e))
            }
        }
    }

    override fun openOnSite(post: Post) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(engine(post).postUrl(post))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Похожие по тегам: персонаж и копирайт поста. Движок рекомендаций и поиск той же картинки
     * на других источниках по md5 — шаг «рекомендации».
     */
    /** Похожие по тегам (движок рекомендаций) и та же картинка на других источниках по md5. */
    override fun findSimilar(post: Post) = vm.openSimilar(post)

    /** Замена дизлайку: минус-сигнал для рекомендаций и лист с тегами поста. */
    override fun notInterested(post: Post) {
        vm.dislike(post)
        notInterestedPost.value = post
    }

    override fun searchTags(site: String, tags: List<String>) = vm.openSearchResults(site, tags)

    override fun makeAvatar(post: Post) {
        scope.launch {
            // Картинка ленты, а не крошечное превью: в профиле аватарку можно растянуть во всю ширину.
            c.settings.setAvatar(post.sampleUrl ?: post.previewUrl)
            toast(context.getString(R.string.avatar_set))
        }
    }

    override suspend fun pools(post: Post): List<PoolInfo> = engine(post).pools(post, c.accounts.session(site(post)))

    override fun openPool(post: Post, pool: PoolInfo) {
        // Пул открывается лентой по порядку: у Danbooru — ordpool, у Moebooru pool уже упорядочен.
        val term = if (site(post).engine == EngineType.DANBOORU) "ordpool:${pool.id}" else "pool:${pool.id}"
        vm.openSearchResults(post.site, listOf(term))
    }

    override fun searchTag(post: Post, tag: String, add: Boolean) {
        if (add) vm.navigate(Route.Search(post.site, tag)) else vm.openSearchResults(post.site, listOf(tag))
    }

    /** В негативные теги; сами ленты начнут их отсеивать на шаге «фильтры». */
    override fun hideTag(post: Post, tag: String) {
        scope.launch {
            runCatching { c.db.negativeTags().insert(NegativeTagEntity(expression = tag, site = null, createdAt = System.currentTimeMillis())) }
            toast(context.getString(R.string.tag_hidden, tag))
        }
    }

    override fun postUrlBase(post: Post): String = site(post).baseUrl

    override fun reveal(post: Post) = vm.reveal(post)

    override fun viewed(post: Post) = vm.recordView(post)

    override fun restoreTag(expression: String) {
        scope.launch { c.negative.removeExpression(expression) }
    }

    override fun cachedAvatar(post: Post): String? {
        val artist = post.tags.artist.firstOrNull() ?: return null
        return c.avatars.cached(site(post), artist, vm.mode.value)
    }

    /** Запрос и разбор ответа — не в главном потоке: аватарки грузятся прямо во время прокрутки. */
    override suspend fun loadAvatar(post: Post): String? {
        val artist = post.tags.artist.firstOrNull() ?: return null
        val site = site(post)
        val mode = vm.mode.value
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.avatars.url(site, artist, mode) }
    }
}
