package app.dudebooru.booru.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Единая модель поста для всех движков. Ключ — [key] (`site:id`), [md5] — для отметок
 * «уже скачано» и поиска той же картинки на других источниках.
 */
@Serializable
data class Post(
    val site: String,
    val id: Long,
    val md5: String? = null,
    /** Дата загрузки на сайт, epoch millis. */
    val createdAt: Long,
    val rating: Rating,
    val score: Int = 0,
    /** Только Danbooru; у Moebooru избранное — это голоса, счётчика нет. */
    val favCount: Int? = null,
    val width: Int,
    val height: Int,
    val fileExt: String? = null,
    val fileSize: Long? = null,
    /** Маленькое превью для сетки. */
    val previewUrl: String? = null,
    /** Картинка для ленты и первого показа в просмотре. */
    val sampleUrl: String? = null,
    val sampleWidth: Int? = null,
    val sampleHeight: Int? = null,
    /** «Скачать» (облегчённая): Danbooru large_file_url, Moebooru jpeg_url или sample_url. */
    val lightUrl: String? = null,
    val lightSize: Long? = null,
    val lightExt: String? = null,
    /** «Скачать оригинал». */
    val fileUrl: String? = null,
    val tags: PostTags = PostTags(),
    val source: String? = null,
    val parentId: Long? = null,
    val hasChildren: Boolean = false,
    val pixivId: Long? = null,
    val poolIds: List<Long> = emptyList(),
    val status: PostStatus = PostStatus.ACTIVE,
) {
    val key: String get() = key(site, id)

    val mediaType: MediaType get() = MediaType.fromExt(fileExt)

    /** Пропорции известны до загрузки картинки — лента не прыгает. */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f

    /** Есть что показать: у ограниченных постов Danbooru без входа ссылок нет. */
    val isViewable: Boolean get() = sampleUrl != null || fileUrl != null || previewUrl != null

    @Transient
    val allTags: Set<String> = tags.all.toHashSet()

    companion object {
        fun key(site: String, id: Long) = "$site:$id"
    }
}

@Serializable
data class PostTags(
    val artist: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val general: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
) {
    val all: List<String> get() = artist + copyright + character + general + meta

    fun byCategory(category: TagCategory): List<String> = when (category) {
        TagCategory.ARTIST -> artist
        TagCategory.COPYRIGHT -> copyright
        TagCategory.CHARACTER -> character
        TagCategory.GENERAL -> general
        TagCategory.META -> meta
    }

    companion object {
        fun fromCategorized(pairs: Iterable<Pair<String, TagCategory>>): PostTags {
            val buckets = TagCategory.entries.associateWith { mutableListOf<String>() }
            for ((name, category) in pairs) buckets.getValue(category) += name
            return PostTags(
                artist = buckets.getValue(TagCategory.ARTIST),
                copyright = buckets.getValue(TagCategory.COPYRIGHT),
                character = buckets.getValue(TagCategory.CHARACTER),
                general = buckets.getValue(TagCategory.GENERAL),
                meta = buckets.getValue(TagCategory.META),
            )
        }
    }
}

@Serializable
enum class PostStatus { ACTIVE, PENDING, FLAGGED, DELETED, BANNED }

enum class MediaType {
    IMAGE, GIF, VIDEO;

    companion object {
        fun fromExt(ext: String?): MediaType = when (ext?.lowercase()) {
            "gif" -> GIF
            "mp4", "webm", "mkv", "mov", "zip" -> VIDEO
            else -> IMAGE
        }
    }
}
