package app.dudebooru.data.posts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Скачивание через тот же клиент, что и лента (прокси, User-Agent).
 * Пока — сразу в Pictures/DudeBooru/{сайт}; очередь, выбор папки, шаблон имени и теги в XMP — шаг «коллекции».
 */
class Downloader(private val context: Context, private val client: () -> OkHttpClient) {

    data class Result(val uri: Uri, val displayPath: String)

    suspend fun download(post: Post, original: Boolean): Result = withContext(Dispatchers.IO) {
        val url = (if (original) post.fileUrl else post.lightUrl ?: post.fileUrl)
            ?: throw IOException("no file url for ${post.key}")
        val ext = (if (original) post.fileExt else post.lightExt ?: post.fileExt) ?: url.substringAfterLast('.', "jpg").substringBefore('?')
        val name = "${post.site}_${post.id}${if (original) "" else "_sample"}.$ext"
        val folder = "${Environment.DIRECTORY_PICTURES}/DudeBooru/${post.site}"

        val response = client().newCall(Request.Builder().url(url).build()).await()
        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(ext))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = if (mimeOf(ext).startsWith("video")) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
                try {
                    resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) } ?: throw IOException("no output stream")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                Result(uri, "$folder/$name")
            } else {
                // Android 8–9: в папку приложения, без запроса разрешений.
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DudeBooru/${post.site}").apply { mkdirs() }
                val file = File(dir, name)
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
                Result(Uri.fromFile(file), file.absolutePath)
            }
        }
    }

    /** Картинка во временный файл для буфера обмена и «поделиться». */
    suspend fun toCache(post: Post): Uri = withContext(Dispatchers.IO) {
        val url = post.sampleUrl ?: post.fileUrl ?: throw IOException("no url")
        val ext = url.substringBefore('?').substringAfterLast('.', "jpg")
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${post.site}_${post.id}.$ext")
        if (!file.exists() || file.length() == 0L) {
            client().newCall(Request.Builder().url(url).build()).await().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                file.outputStream().use { out -> resp.body.byteStream().copyTo(out) }
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    companion object {
        fun mimeOf(ext: String): String = when (ext.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }
}
