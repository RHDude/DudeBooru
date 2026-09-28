package app.dudebooru.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.dudebooru.booru.model.TagCategory

/**
 * Кэш постов и основа «Сохранённых»: пост хранится целиком (JSON единой модели),
 * поэтому переживёт удаление на сайте.
 */
@Entity(tableName = "posts", primaryKeys = ["site", "id"], indices = [Index("md5")])
data class PostEntity(
    val site: String,
    val id: Long,
    val md5: String?,
    val json: String,
    val fetchedAt: Long,
)

/** Словарь тегов: мгновенные подсказки, категории, редкость. */
@Entity(tableName = "tags", primaryKeys = ["site", "name"], indices = [Index(value = ["site", "postCount"])])
data class TagEntity(
    val site: String,
    val name: String,
    val category: TagCategory,
    val postCount: Long?,
    val updatedAt: Long,
)

@Entity(tableName = "tag_aliases", primaryKeys = ["site", "alias"])
data class TagAliasEntity(
    val site: String,
    val alias: String,
    val canonical: String,
)

@Entity(tableName = "likes", primaryKeys = ["site", "postId"], indices = [Index("likedAt")])
data class LikeEntity(
    val site: String,
    val postId: Long,
    val likedAt: Long,
)

@Entity(tableName = "saved", primaryKeys = ["site", "postId"], indices = [Index("savedAt")])
data class SavedEntity(
    val site: String,
    val postId: Long,
    val savedAt: Long,
)

/** Негативный тег или комбинация `tag_a tag_b`; [site] = null — для всех источников. */
@Entity(tableName = "negative_tags", indices = [Index(value = ["expression", "site"], unique = true)])
data class NegativeTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val site: String?,
    val createdAt: Long,
)

/** Очередь действий для сайта (лайки, сохранения) — уходят, когда появится сеть. */
@Entity(tableName = "pending_actions", indices = [Index("createdAt")])
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val site: String,
    val action: String,
    val postId: Long,
    val value: Int?,
    val createdAt: Long,
    val attempts: Int = 0,
)

/** История поиска по источникам; закреплённые показываются первыми. */
@Entity(tableName = "search_history", primaryKeys = ["site", "query"], indices = [Index("usedAt")])
data class SearchHistoryEntity(
    val site: String,
    val query: String,
    val usedAt: Long,
    val pinned: Boolean = false,
)

/** Аватарка художника: вырезка из самой популярной работы, кэш на неделю. */
@Entity(tableName = "artist_avatars", primaryKeys = ["site", "artist", "mode"])
data class ArtistAvatarEntity(
    val site: String,
    val artist: String,
    val mode: String,
    val url: String?,
    val fetchedAt: Long,
)

/** Профиль вкуса для рекомендаций: вес тега. */
@Entity(tableName = "taste", primaryKeys = ["tag"])
data class TasteEntity(
    val tag: String,
    val category: TagCategory,
    val weight: Double,
    val updatedAt: Long,
)

/** Просмотренные посты — для «Истории». */
@Entity(tableName = "view_history", primaryKeys = ["site", "postId"], indices = [Index("viewedAt")])
data class ViewHistoryEntity(
    val site: String,
    val postId: Long,
    val viewedAt: Long,
)

/** Подписка на художника: новые работы — как непрочитанные каналы. */
@Entity(tableName = "subscriptions", primaryKeys = ["site", "artist"])
data class SubscriptionEntity(
    val site: String,
    val artist: String,
    val subscribedAt: Long,
    /** Самая новая работа, которую человек видел. */
    val lastSeenId: Long,
    val newCount: Int = 0,
    val checkedAt: Long = 0,
)

/** Папки внутри «Сохранённых», как favorite groups на Danbooru. */
@Entity(tableName = "saved_folders")
data class SavedFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "saved_folder_posts", primaryKeys = ["folderId", "site", "postId"])
data class SavedFolderPostEntity(
    val folderId: Long,
    val site: String,
    val postId: Long,
)

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED }

/** Загрузка в фоновой очереди. */
@Entity(tableName = "downloads", indices = [Index("status"), Index("md5")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val site: String,
    val postId: Long,
    val md5: String?,
    val url: String,
    val original: Boolean,
    /** Путь внутри папки загрузок по шаблону имени. */
    val relativePath: String,
    val mime: String,
    val status: DownloadStatus,
    val bytes: Long = 0,
    val total: Long = 0,
    /** Готовый файл. */
    val uri: String? = null,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** «Не интересно…» — минус-сигнал для рекомендаций. */
@Entity(tableName = "dislikes", primaryKeys = ["site", "postId"])
data class DislikeEntity(
    val site: String,
    val postId: Long,
    val at: Long,
)

/** Тег, который человек убрал из «Моих тегов»: не учитывается во вкусе. */
@Entity(tableName = "muted_taste")
data class MutedTagEntity(
    @PrimaryKey val tag: String,
    val at: Long,
)
