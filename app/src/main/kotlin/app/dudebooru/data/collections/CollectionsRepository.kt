package app.dudebooru.data.collections

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.db.AppDatabase
import app.dudebooru.data.db.LikeEntity
import app.dudebooru.data.db.PendingActionEntity
import app.dudebooru.data.db.PostEntity
import app.dudebooru.data.db.SavedEntity
import app.dudebooru.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Лайки и сохранённые: локально всегда, на сайт — по желанию.
 * Действие применяется в интерфейсе сразу и уходит на сайт из очереди, когда появится связь.
 */
class CollectionsRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val registry: SiteRegistry,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
) {
    suspend fun setLiked(post: Post, liked: Boolean) {
        remember(post)
        if (liked) db.collections().like(LikeEntity(post.site, post.id, System.currentTimeMillis())) else db.collections().unlike(post.site, post.id)
        if (settings.syncPrefs.first().mirrorLikes) enqueue(post.site, post.id, ACTION_LIKE)
    }

    suspend fun setSaved(post: Post, saved: Boolean) {
        // Пост хранится целиком: переживёт удаление на сайте.
        remember(post)
        if (saved) db.collections().save(SavedEntity(post.site, post.id, System.currentTimeMillis())) else db.collections().unsave(post.site, post.id)
        if (settings.syncPrefs.first().syncSaved) enqueue(post.site, post.id, ACTION_SAVE)
    }

    private suspend fun remember(post: Post) {
        db.posts().upsertAll(listOf(PostEntity(post.site, post.id, post.md5, BooruJson.encodeToString(Post.serializer(), post), System.currentTimeMillis())))
    }

    /** Только если есть вход на сайт: без аккаунта отправлять некуда. */
    private suspend fun enqueue(siteId: String, postId: Long, action: String) {
        val site = registry.site(siteId) ?: return
        if (accounts.session(site).credentials == null) return
        // Быстрые переключения туда-обратно схлопываются: на сайт уходит итоговое состояние.
        db.pending().deleteSame(siteId, postId, action)
        db.pending().insert(PendingActionEntity(site = siteId, action = action, postId = postId, value = null, createdAt = System.currentTimeMillis()))
        scheduleSync(context)
    }

    /**
     * Первый вход: избранное с сайта переезжает в «Сохранённые». Посты, которые уже есть локально, склеиваются.
     * Возвращает число новых.
     */
    suspend fun importFavorites(site: SiteConfig, maxPosts: Int = 2_000): Int {
        val session = accounts.session(site)
        if (session.credentials == null) return 0
        val engine = registry.engine(site)
        var page: app.dudebooru.booru.engine.PageKey? = null
        var imported = 0
        var total = 0
        val now = System.currentTimeMillis()
        do {
            val result = engine.favorites(page, site.maxPageSize, session)
            for ((index, post) in result.posts.withIndex()) {
                total++
                if (db.collections().isSavedNow(post.site, post.id)) continue
                remember(post)
                // Порядок избранного сохраняется: новее на сайте — выше в «Сохранённых».
                db.collections().save(SavedEntity(post.site, post.id, now - total - index))
                imported++
            }
            page = result.next
        } while (page != null && total < maxPosts && result.rawCount > 0)
        return imported
    }

    companion object {
        const val ACTION_LIKE = "like"
        const val ACTION_SAVE = "save"

        fun scheduleSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("collection-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
