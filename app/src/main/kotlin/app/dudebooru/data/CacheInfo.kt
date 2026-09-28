package app.dudebooru.data

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Настройки → Данные и память: сколько занимает кэш и его очистка. */
object CacheInfo {
    data class Sizes(val images: Long, val other: Long) {
        val total: Long get() = images + other
    }

    /** Что можно удалить без последствий: картинки, файлы для «Поделиться», скачанные обновления. */
    private val CLEARABLE = listOf("shared", "updates")

    suspend fun sizes(context: Context): Sizes = withContext(Dispatchers.IO) {
        val images = size(File(context.cacheDir, "images"))
        Sizes(images = images, other = (size(context.cacheDir) - images).coerceAtLeast(0))
    }

    /** Недокачанные файлы загрузок и сохранённые ленты для офлайна не трогаем. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        CLEARABLE.forEach { File(context.cacheDir, it).deleteRecursively() }
    }

    private fun size(file: File): Long = if (file.isDirectory) {
        file.listFiles()?.sumOf { size(it) } ?: 0L
    } else {
        file.length()
    }
}
