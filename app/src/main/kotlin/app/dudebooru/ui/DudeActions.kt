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
) : PostActions {
    private val c get() = vm.c

    /** Пост, для которого открыт лист «Не интересно…». */
    val notInterestedPost = MutableStateFlow<Post?>(null)

    private fun site(post: Post) = requireNotNull(c.registry.site(post.site))
    private fun engine(post: Post) = c.registry.engine(site(post))
    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    override fun open(controller: FeedController, post: Post) = vm.navigate(Route.Viewer(controller.id, post.key))

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

    override fun download(post: Post, original: Boolean) {
        scope.launch {
            try {
                val result = c.downloader.download(post, original)
                toast(context.getString(R.string.download_done, result.displayPath))
            } catch (e: Exception) {
                toast(context.getString(R.string.download_failed, context.errorText(e)))
            }
        }
    }

    override fun downloadAll(posts: List<Post>) {
        scope.launch {
            var ok = 0
            for (post in posts) if (runCatching { c.downloader.download(post, original = true) }.isSuccess) ok++
            toast(context.getString(R.string.download_all_done, ok, posts.size))
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
    override fun findSimilar(post: Post) {
        val tags = (post.tags.character.take(1) + post.tags.copyright.take(1)).ifEmpty { post.tags.general.take(2) }
        if (tags.isNotEmpty()) vm.openSearchResults(post.site, tags)
    }

    override fun notInterested(post: Post) {
        notInterestedPost.value = post
    }

    override fun makeAvatar(post: Post) {
        scope.launch {
            c.settings.setAvatar(post.previewUrl ?: post.sampleUrl)
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

    override fun restoreTag(expression: String) {
        scope.launch { c.negative.removeExpression(expression) }
    }

    override fun cachedAvatar(post: Post): String? {
        val artist = post.tags.artist.firstOrNull() ?: return null
        return c.avatars.cached(site(post), artist, vm.mode.value)
    }

    override suspend fun loadAvatar(post: Post): String? {
        val artist = post.tags.artist.firstOrNull() ?: return null
        return c.avatars.url(site(post), artist, vm.mode.value)
    }
}
