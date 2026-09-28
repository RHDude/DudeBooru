package app.dudebooru.data.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.dudebooru.DudeApp
import app.dudebooru.R
import app.dudebooru.booru.download.NameTemplate
import app.dudebooru.booru.download.Xmp
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.net.await
import app.dudebooru.data.db.DownloadEntity
import app.dudebooru.data.db.DownloadStatus
import app.dudebooru.data.settings.DownloadPrefs
import app.dudebooru.util.DudeLog
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Фоновая очередь загрузок. Не больше [PER_SITE] загрузок с одного сайта одновременно,
 * докачка через Range, пауза проверяется по базе. Если оригинал уже подгружен в просмотре,
 * файл берётся из кэша картинок без повторного трафика.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val c = (context.applicationContext as DudeApp).container
    private val dao = c.db.downloads()
    private val done = AtomicInteger()
    private val failed = AtomicInteger()
    private val total = AtomicInteger()

    private class Paused : Exception()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)

    override suspend fun doWork(): Result {
        dao.requeueInterrupted(System.currentTimeMillis())
        runCatching { setForeground(foreground(0, 0)) }.onFailure { DudeLog.w("dl", "no foreground: ${it.message}") }
        val prefs = c.settings.downloadPrefs.first()
        val global = Semaphore(prefs.parallel.coerceIn(1, 4))
        val perSite = ConcurrentHashMap<String, Semaphore>()
        coroutineScope {
            while (true) {
                val batch = dao.queued(20)
                if (batch.isEmpty()) break
                total.addAndGet(batch.size)
                batch.map { entry ->
                    async {
                        val site = perSite.computeIfAbsent(entry.site) { Semaphore(PER_SITE) }
                        global.withPermit { site.withPermit { process(entry, prefs) } }
                    }
                }.awaitAll()
            }
        }
        finish()
        return Result.success()
    }

    private suspend fun process(entry: DownloadEntity, prefs: DownloadPrefs) {
        val current = dao.get(entry.id) ?: return
        if (current.status != DownloadStatus.QUEUED) return
        dao.update(current.copy(status = DownloadStatus.RUNNING, updatedAt = System.currentTimeMillis()))
        val part = DownloadRepository.partFile(applicationContext, entry.id)
        try {
            val size = fetch(current, part)
            val uri = store(current, part, prefs)
            dao.update(current.copy(status = DownloadStatus.DONE, bytes = size, total = size, uri = uri.toString(), error = null, updatedAt = System.currentTimeMillis()))
            part.delete()
            done.incrementAndGet()
        } catch (e: Paused) {
            dao.get(entry.id)?.let { dao.update(it.copy(status = DownloadStatus.PAUSED, updatedAt = System.currentTimeMillis())) }
        } catch (e: CancellationException) {
            dao.get(entry.id)?.let { dao.update(it.copy(status = DownloadStatus.QUEUED, updatedAt = System.currentTimeMillis())) }
            throw e
        } catch (e: Exception) {
            DudeLog.w("dl", "download ${entry.site}:${entry.postId} failed: ${e.message}")
            failed.incrementAndGet()
            dao.get(entry.id)?.let { dao.update(it.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName, updatedAt = System.currentTimeMillis())) }
        }
        updateNotification()
    }

    /** Скачивает во временный файл с докачкой. Возвращает размер. */
    private suspend fun fetch(entry: DownloadEntity, part: File): Long = withContext(Dispatchers.IO) {
        // Оригинал уже в кэше просмотра — копируем без сети.
        if (!part.exists() || part.length() == 0L) {
            val cached = runCatching {
                SingletonImageLoader.get(applicationContext).diskCache?.openSnapshot(entry.url)?.use { snapshot ->
                    snapshot.data.toFile().copyTo(part, overwrite = true)
                    true
                }
            }.getOrNull()
            if (cached == true) return@withContext part.length()
        }
        val offset = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(entry.url).apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
        c.imageClient.newCall(request).await().use { response ->
            if (response.code == 416) return@withContext part.length()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val append = response.code == 206 && offset > 0
            val body = response.body
            val expected = body.contentLength().let { if (it > 0) it + (if (append) offset else 0) else 0 }
            var written = if (append) offset else 0L
            var lastCheck = 0L
            java.io.FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(64 * 1024)
                val input = body.byteStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    written += n
                    val now = System.currentTimeMillis()
                    if (now - lastCheck > 700) {
                        lastCheck = now
                        val latest = dao.get(entry.id) ?: throw Paused()
                        if (latest.status == DownloadStatus.PAUSED) throw Paused()
                        dao.update(latest.copy(bytes = written, total = expected, updatedAt = now))
                    }
                }
            }
            written
        }
    }

    /** Кладёт файл в выбранную папку (SAF) или в Pictures, с тегами в XMP. */
    private suspend fun store(entry: DownloadEntity, part: File, prefs: DownloadPrefs): Uri = withContext(Dispatchers.IO) {
        val ext = entry.relativePath.substringAfterLast('.', "").lowercase()
        val packet = if (prefs.writeTags) xmpPacket(entry) else null
        val (uri, finish) = openDestination(entry, prefs)
        try {
            applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { out -> copy(part, out, ext, packet) }
                ?: throw IOException("no output stream")
            finish(true)
        } catch (e: Exception) {
            finish(false)
            throw e
        }
        uri
    }

    private fun copy(part: File, out: OutputStream, ext: String, packet: String?) {
        part.inputStream().use { input ->
            if (packet != null) {
                try {
                    Xmp.copyWithXmp(input, out, ext, packet)
                    return
                } catch (e: IOException) {
                    DudeLog.w("dl", "xmp skipped: ${e.message}")
                }
            }
        }
        part.inputStream().use { it.copyTo(out) }
    }

    private suspend fun xmpPacket(entry: DownloadEntity): String? {
        val post = c.db.posts().get(entry.site, entry.postId)?.let {
            runCatching { BooruJson.decodeFromString(Post.serializer(), it.json) }.getOrNull()
        } ?: return null
        val title = listOfNotNull(post.tags.character.firstOrNull(), post.tags.artist.firstOrNull()?.let { "by $it" }).joinToString(" ").ifEmpty { null }
        return Xmp.packet(post.tags.all, post.source, title)
    }

    /** Возвращает адрес файла и завершение (для MediaStore — снять IS_PENDING или удалить). */
    private fun openDestination(entry: DownloadEntity, prefs: DownloadPrefs): Pair<Uri, (Boolean) -> Unit> {
        val directory = NameTemplate.directory(entry.relativePath)
        val name = NameTemplate.fileName(entry.relativePath)
        val tree = prefs.treeUri?.let { DocumentFile.fromTreeUri(applicationContext, Uri.parse(it)) }
        if (tree != null && tree.canWrite()) {
            var dir: DocumentFile = tree
            for (segment in directory.split('/').filter { it.isNotEmpty() }) {
                dir = dir.findFile(segment)?.takeIf { it.isDirectory } ?: dir.createDirectory(segment) ?: throw IOException("cannot create $segment")
            }
            dir.findFile(name)?.delete()
            val file = dir.createFile(entry.mime, name) ?: throw IOException("cannot create $name")
            return file.uri to { ok -> if (!ok) file.delete() }
        }
        val resolver = applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val video = entry.mime.startsWith("video")
            val root = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, entry.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (directory.isEmpty()) root else "$root/$directory")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (video) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
            return uri to { ok ->
                if (ok) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                else resolver.delete(uri, null, null)
            }
        }
        // Android 8–9 без выбранной папки: в папку приложения, без разрешений.
        val dir = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), directory).apply { mkdirs() }
        val file = File(dir, name)
        return Uri.fromFile(file) to { ok -> if (!ok) file.delete() }
    }

    // --- уведомления -------------------------------------------------------------------------

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.dl_notification_title))
            .setContentText(if (total > 0) applicationContext.getString(R.string.dl_progress, done, total) else null)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_PROGRESS, notification)
        }
    }

    private suspend fun updateNotification() {
        runCatching { setForeground(foreground(done.get() + failed.get(), total.get())) }
    }

    private fun finish() {
        val ok = done.get()
        val bad = failed.get()
        if (ok + bad == 0) return
        ensureChannel(applicationContext)
        val text = if (bad > 0) applicationContext.getString(R.string.dl_done_with_errors, ok, bad) else applicationContext.getString(R.string.dl_done, ok)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(applicationContext.getString(R.string.dl_notification_title))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching {
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_DONE, notification)
        }
    }

    companion object {
        const val PER_SITE = 2
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_PROGRESS = 42
        private const val NOTIFICATION_DONE = 43

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.dl_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
