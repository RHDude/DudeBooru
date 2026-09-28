package app.dudebooru.data.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.dudebooru.booru.download.NameTemplate
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.data.db.AppDatabase
import app.dudebooru.data.db.DownloadEntity
import app.dudebooru.data.db.DownloadStatus
import app.dudebooru.data.db.PostEntity
import app.dudebooru.data.posts.Downloader
import app.dudebooru.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Очередь загрузок: фоном, с уведомлением о прогрессе, докачкой после обрыва, паузой и повтором.
 * Уже скачанное (по md5) повторно не качается.
 */
class DownloadRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    val all: Flow<List<DownloadEntity>> = db.downloads().all()

    /** md5 готовых загрузок — отметка «уже скачано» в ленте. */
    val doneMd5: Flow<Set<String>> = db.downloads().doneMd5().map { it.toSet() }

    data class Enqueued(val added: Int, val skipped: Int)

    suspend fun enqueue(posts: List<Post>, original: Boolean): Enqueued {
        val prefs = settings.downloadPrefs.first()
        val now = System.currentTimeMillis()
        val entries = ArrayList<DownloadEntity>()
        var skipped = 0
        for (post in posts) {
            val md5 = post.md5
            if (md5 != null && db.downloads().exists(md5, original)) {
                skipped++
                continue
            }
            val url = (if (original) post.fileUrl else post.lightUrl ?: post.fileUrl)
            if (url == null) {
                skipped++
                continue
            }
            val ext = (if (original) post.fileExt else post.lightExt ?: post.fileExt)
                ?: url.substringBefore('?').substringAfterLast('.', "jpg")
            var path = NameTemplate.render(prefs.template, post, ext)
            // Облегчённая версия не должна затирать оригинал с тем же именем.
            if (!original) path = path.substringBeforeLast('.') + "_sample." + path.substringAfterLast('.')
            entries += DownloadEntity(
                site = post.site,
                postId = post.id,
                md5 = md5,
                url = url,
                original = original,
                relativePath = path,
                mime = Downloader.mimeOf(ext),
                status = DownloadStatus.QUEUED,
                createdAt = now + entries.size,
                updatedAt = now,
            )
        }
        if (entries.isNotEmpty()) {
            // Пост нужен целиком: теги для XMP и шаблона.
            db.posts().upsertAll(posts.map { PostEntity(it.site, it.id, it.md5, BooruJson.encodeToString(Post.serializer(), it), now) })
            db.downloads().insertAll(entries)
            schedule(prefs.wifiOnly)
        }
        return Enqueued(entries.size, skipped)
    }

    suspend fun pause(id: Long) = update(id) { if (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.PAUSED) else it }

    suspend fun resume(id: Long) {
        update(id) { if (it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED) it.copy(status = DownloadStatus.QUEUED, error = null) else it }
        schedule(settings.downloadPrefs.first().wifiOnly)
    }

    suspend fun pauseAll() {
        val now = System.currentTimeMillis()
        db.downloads().moveAll(DownloadStatus.QUEUED, DownloadStatus.PAUSED, now)
        db.downloads().moveAll(DownloadStatus.RUNNING, DownloadStatus.PAUSED, now)
    }

    suspend fun resumeAll() {
        val now = System.currentTimeMillis()
        db.downloads().moveAll(DownloadStatus.PAUSED, DownloadStatus.QUEUED, now)
        db.downloads().moveAll(DownloadStatus.FAILED, DownloadStatus.QUEUED, now)
        schedule(settings.downloadPrefs.first().wifiOnly)
    }

    suspend fun cancel(id: Long) {
        db.downloads().delete(id)
        partFile(context, id).delete()
    }

    suspend fun clearDone() = db.downloads().clearDone()

    private suspend fun update(id: Long, transform: (DownloadEntity) -> DownloadEntity) {
        val entry = db.downloads().get(id) ?: return
        val next = transform(entry)
        if (next != entry) db.downloads().update(next.copy(updatedAt = System.currentTimeMillis()))
    }

    fun schedule(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Идущая загрузка доработает, следующая подхватит новое из очереди.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "downloads"

        fun partFile(context: Context, id: Long): File = File(File(context.cacheDir, "downloads").apply { mkdirs() }, "$id.part")
    }
}
