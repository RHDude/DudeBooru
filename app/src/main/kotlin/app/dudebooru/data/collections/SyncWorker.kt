package app.dudebooru.data.collections

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.dudebooru.DudeApp
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.net.RateLimiter
import app.dudebooru.util.DudeLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Отправляет очередь лайков и сохранений на сайты. На момент отправки берётся итоговое состояние
 * из базы, поэтому «лайк — снял — лайк» уходит одним запросом.
 * Новым аккаунтам Danbooru даёт мало голосов (8 в минуту, запас 60) — отправка идёт с этим темпом.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as DudeApp).container
        val dao = c.db.pending()
        val sync = c.settings.syncPrefsNow()
        while (true) {
            val batch = dao.next(50)
            if (batch.isEmpty()) return Result.success()
            for (action in batch) {
                val site = c.registry.site(action.site)
                if (site == null || action.attempts >= MAX_ATTEMPTS) {
                    dao.delete(action.id)
                    continue
                }
                val session = c.accounts.session(site)
                if (session.credentials == null) {
                    dao.delete(action.id)
                    continue
                }
                writeLimiter(site.id).acquire()
                val liked = c.db.collections().isLikedNow(site.id, action.postId)
                val saved = c.db.collections().isSavedNow(site.id, action.postId)
                // Отправляем только то, чего касалось действие: иначе лайк мог бы снять с сайта избранное,
                // которого нет локально. У Moebooru один голос на пост, поэтому при дублировании лайков
                // лайк учитывается и при сохранении (сняли сохранение, лайк стоит → голос 1).
                val moebooru = site.engine == app.dudebooru.booru.site.EngineType.MOEBOORU
                val sendLike = action.action == CollectionsRepository.ACTION_LIKE || (moebooru && sync.mirrorLikes)
                val sendSave = action.action == CollectionsRepository.ACTION_SAVE
                try {
                    c.registry.engine(site).pushCollectionState(
                        postId = action.postId,
                        like = if (sendLike) liked else null,
                        save = if (sendSave) saved else null,
                        session = session,
                    )
                    dao.delete(action.id)
                } catch (e: BooruException.Unauthorized) {
                    c.accounts.markInvalid(site)
                    dao.delete(action.id)
                } catch (e: BooruException.TooManyRequests) {
                    DudeLog.w("sync", "rate limited on ${site.id}, retry later")
                    return Result.retry()
                } catch (e: BooruException.NotResponding) {
                    return Result.retry()
                } catch (e: BooruException.ServerError) {
                    return Result.retry()
                } catch (e: BooruException) {
                    DudeLog.w("sync", "drop ${action.action} ${site.id}:${action.postId}: ${e.message}")
                    dao.bump(action.id)
                }
            }
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private val limiters = ConcurrentHashMap<String, RateLimiter>()

        fun writeLimiter(siteId: String): RateLimiter = limiters.computeIfAbsent(siteId) { RateLimiter(8.0 / 60.0, 60) }
    }
}
