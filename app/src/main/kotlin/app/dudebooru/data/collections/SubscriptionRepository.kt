package app.dudebooru.data.collections

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.dudebooru.DudeApp
import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.db.SubscriptionDao
import app.dudebooru.data.db.SubscriptionEntity
import app.dudebooru.data.filter.NegativeTags
import app.dudebooru.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Новые работы художника, о которых ещё не было уведомления. */
data class NewWorks(val site: SiteConfig, val artist: String, val count: Int, val newestId: Long)

/** Подписки на художников: новые работы попадают в «Художники» со счётчиком, как непрочитанные каналы. */
class SubscriptionRepository(
    private val dao: SubscriptionDao,
    private val registry: SiteRegistry,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val negative: NegativeTags,
) {
    val all: Flow<List<SubscriptionEntity>> = dao.all()

    /** Сколько художников с новыми работами — метка в боковом меню. */
    val withNew: Flow<Int> = dao.all().map { list -> list.count { it.newCount > 0 } }

    fun isSubscribed(site: SiteConfig, artist: String): Flow<Boolean> = dao.isSubscribed(site.id, artist)

    /** Подписка целиком (с колокольчиком); null — не подписан. */
    fun observe(site: SiteConfig, artist: String): Flow<SubscriptionEntity?> = dao.observe(site.id, artist)

    suspend fun subscribe(site: SiteConfig, artist: String, newestSeenId: Long) {
        dao.upsert(SubscriptionEntity(site.id, artist, System.currentTimeMillis(), newestSeenId, notifiedId = newestSeenId))
    }

    suspend fun unsubscribe(site: SiteConfig, artist: String) = dao.delete(site.id, artist)

    suspend fun setNotify(site: SiteConfig, artist: String, notify: Boolean) = dao.setNotify(site.id, artist, notify)

    suspend fun markSeen(site: SiteConfig, artist: String, newestId: Long) = dao.markSeen(site.id, artist, newestId)

    suspend fun markNotified(works: NewWorks) = dao.markNotified(works.site.id, works.artist, works.newestId)

    /**
     * Проверка новых работ у всех подписок; запросы фоновые, чтобы не мешать ленте.
     * Возвращает то, о чём стоит уведомить: у художника включён колокольчик и есть работы новее прошлого уведомления.
     */
    suspend fun checkAll(): List<NewWorks> {
        val mode = settings.contentMode.first()
        val blacklist = negative.blacklist.value
        val result = mutableListOf<NewWorks>()
        for (sub in dao.list()) {
            val site = registry.site(sub.site) ?: continue
            val session = accounts.session(site).copy(background = true)
            val page = runCatching {
                registry.engine(site).posts(FeedRequest(listOf(sub.artist), SortOrder.NEW, mode), null, 20, session)
            }.getOrNull() ?: continue
            val fresh = page.posts.filter { it.id > sub.lastSeenId && blacklist.match(it) == null }
            dao.setNewCount(sub.site, sub.artist, fresh.size, System.currentTimeMillis())
            val newest = fresh.maxOfOrNull { it.id } ?: continue
            if (sub.notify && newest > sub.notifiedId) result += NewWorks(site, sub.artist, fresh.size, newest)
        }
        return result
    }

    /** Раз в 6 часов в фоне: счётчики «Художников» и уведомления о новых работах. */
    class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as DudeApp).container
            val fresh = runCatching { c.subscriptions.checkAll() }.getOrElse { return Result.retry() }
            if (c.settings.notifyArtists.first()) app.dudebooru.notify.Notifications.showNewWorks(applicationContext, fresh)
            // Отмечаем и при выключенных уведомлениях: включат их снова — не придёт пачка старого.
            fresh.forEach { c.subscriptions.markNotified(it) }
            return Result.success()
        }
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("artist-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
