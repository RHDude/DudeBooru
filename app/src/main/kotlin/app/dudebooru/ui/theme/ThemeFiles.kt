package app.dudebooru.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Файлы тем: картинки фона, экспорт `.booru-theme`, импорт из файла (в том числе открытого из Telegram). */
object ThemeFiles {
    private const val MAX_SIDE = 1600

    private fun dir(context: Context) = File(context.filesDir, "themes").apply { mkdirs() }

    /** Картинка фона из галереи: уменьшаем и храним у себя, чтобы тема не зависела от чужого файла. */
    suspend fun imageFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            saveImage(context, bytes)
        }.getOrNull()
    }

    /** Картинка фона из поста: из кэша Coil (или сети через него). */
    suspend fun imageFromUrl(context: Context, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
            writeBitmap(context, result.image.toBitmap())
        }.getOrNull()
    }

    /** Байты картинки (из файла темы или галереи) → уменьшенный JPEG в папке тем. */
    fun saveImage(context: Context, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return writeBitmap(context, bitmap)
    }

    private fun writeBitmap(context: Context, source: Bitmap): String {
        val scale = MAX_SIDE.toFloat() / maxOf(source.width, source.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
        val file = File(dir(context), "bg_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.absolutePath
    }

    /** Старые картинки фона, на которые больше не ссылается тема. */
    fun cleanup(context: Context, keep: String?) {
        dir(context).listFiles()?.forEach { if (it.absolutePath != keep && it.name.startsWith("bg_")) it.delete() }
    }

    /** Файл темы для «Поделиться»: фон-картинка упакована внутрь. */
    suspend fun exportFile(context: Context, theme: AppTheme): Uri = withContext(Dispatchers.IO) {
        val image = theme.background.imagePath?.let { path -> runCatching { File(path).readBytes() }.getOrNull() }
        val out = File(context.cacheDir, "shared").apply { mkdirs() }
        val name = theme.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifEmpty { "theme" }
        val file = File(out, "$name.booru-theme")
        file.writeText(ThemeCodec.toFile(theme, image))
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Тема из файла; картинка фона сразу сохраняется к себе. */
    suspend fun readImport(context: Context, uri: Uri): AppTheme? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                // Файл темы с картинкой — сотни килобайт; больше 20 МБ это точно не тема.
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > 20 * 1024 * 1024) return@runCatching null
                    buffer.write(chunk, 0, n)
                }
                buffer.toString(Charsets.UTF_8.name())
            } ?: return@runCatching null
            val (theme, image) = ThemeCodec.fromFile(text) ?: return@runCatching null
            val path = image?.let { saveImage(context, it) }
            theme.copy(background = theme.background.copy(imagePath = path))
        }.getOrNull()
    }
}
