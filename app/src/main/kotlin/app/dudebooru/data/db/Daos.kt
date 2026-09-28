package app.dudebooru.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Upsert
    suspend fun upsertAll(posts: List<PostEntity>)

    @Query("SELECT * FROM posts WHERE site = :site AND id = :id")
    suspend fun get(site: String, id: Long): PostEntity?

    @Query("SELECT * FROM posts WHERE md5 = :md5")
    suspend fun byMd5(md5: String): List<PostEntity>

    @Query("SELECT COUNT(*) FROM posts")
    suspend fun count(): Int

    /** Чистка кэша: лайкнутые и сохранённые посты не трогаем. */
    @Query(
        """
        DELETE FROM posts WHERE fetchedAt < :before
          AND NOT EXISTS (SELECT 1 FROM saved s WHERE s.site = posts.site AND s.postId = posts.id)
          AND NOT EXISTS (SELECT 1 FROM likes l WHERE l.site = posts.site AND l.postId = posts.id)
        """,
    )
    suspend fun evictOlderThan(before: Long): Int
}

@Dao
interface TagDao {
    /** Полный апдейт: пришли точные данные (автодополнение, tags.json). */
    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    /** Словарь из постов и tag summary: не затираем уже известные счётчики. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<TagAliasEntity>)

    @Query("SELECT * FROM tags WHERE site = :site AND name IN (:names)")
    suspend fun byNames(site: String, names: List<String>): List<TagEntity>

    /** Префиксный поиск диапазоном `[from, to)` вместо LIKE, чтобы работал индекс. */
    @Query(
        """
        SELECT * FROM tags WHERE site = :site AND name >= :from AND name < :to
        ORDER BY postCount IS NULL, postCount DESC LIMIT :limit
        """,
    )
    suspend fun byRange(site: String, from: String, to: String, limit: Int): List<TagEntity>

    suspend fun byPrefix(site: String, prefix: String, limit: Int): List<TagEntity> =
        byRange(site, prefix, prefix + '￿', limit)

    @Query("SELECT canonical FROM tag_aliases WHERE site = :site AND alias = :alias")
    suspend fun canonical(site: String, alias: String): String?

    @Query("SELECT COUNT(*) FROM tags WHERE site = :site")
    suspend fun count(site: String): Int

    @Transaction
    suspend fun importDictionary(tags: List<TagEntity>, aliases: List<TagAliasEntity>) {
        tags.chunked(2_000).forEach { insertIfAbsent(it) }
        aliases.chunked(2_000).forEach { upsertAliases(it) }
    }
}

@Dao
interface CollectionDao {
    @Query("SELECT site || ':' || postId FROM likes")
    fun likedKeys(): Flow<List<String>>

    @Query("SELECT site || ':' || postId FROM saved")
    fun savedKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(like: LikeEntity)

    @Query("DELETE FROM likes WHERE site = :site AND postId = :postId")
    suspend fun unlike(site: String, postId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(saved: SavedEntity)

    @Query("DELETE FROM saved WHERE site = :site AND postId = :postId")
    suspend fun unsave(site: String, postId: Long)

    @Query("SELECT COUNT(*) FROM saved")
    fun savedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM likes")
    fun likeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE site = :site AND postId = :postId)")
    suspend fun isLikedNow(site: String, postId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM saved WHERE site = :site AND postId = :postId)")
    suspend fun isSavedNow(site: String, postId: Long): Boolean
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE site = :site ORDER BY pinned DESC, usedAt DESC LIMIT :limit")
    fun recent(site: String, limit: Int = 30): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY usedAt DESC LIMIT :limit")
    fun recentAll(limit: Int = 200): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("SELECT * FROM search_history WHERE site = :site AND query = :query")
    suspend fun get(site: String, query: String): SearchHistoryEntity?

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE site = :site AND query = :query")
    suspend fun delete(site: String, query: String)
}

@Dao
interface NegativeTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: NegativeTagEntity): Long

    @Query("SELECT * FROM negative_tags ORDER BY createdAt DESC")
    fun all(): Flow<List<NegativeTagEntity>>

    @Query("DELETE FROM negative_tags WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ArtistAvatarDao {
    @Query("SELECT * FROM artist_avatars WHERE site = :site AND artist = :artist AND mode = :mode")
    suspend fun get(site: String, artist: String, mode: String): ArtistAvatarEntity?

    @Upsert
    suspend fun upsert(entry: ArtistAvatarEntity)
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(entry: ViewHistoryEntity)

    @Query(
        """
        SELECT p.* FROM view_history h JOIN posts p ON p.site = h.site AND p.id = h.postId
        ORDER BY h.viewedAt DESC LIMIT :limit
        """,
    )
    fun recentPosts(limit: Int = 500): Flow<List<PostEntity>>

    @Query("DELETE FROM view_history")
    suspend fun clear()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY newCount > 0 DESC, subscribedAt DESC")
    fun all(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun list(): List<SubscriptionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE site = :site AND artist = :artist)")
    fun isSubscribed(site: String, artist: String): Flow<Boolean>

    @Query("SELECT * FROM subscriptions WHERE site = :site AND artist = :artist")
    fun observe(site: String, artist: String): Flow<SubscriptionEntity?>

    @Upsert
    suspend fun upsert(entry: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE site = :site AND artist = :artist")
    suspend fun delete(site: String, artist: String)

    @Query("UPDATE subscriptions SET newCount = :count, checkedAt = :at WHERE site = :site AND artist = :artist")
    suspend fun setNewCount(site: String, artist: String, count: Int, at: Long)

    /** Увиденное не присылается и уведомлением. */
    @Query(
        """
        UPDATE subscriptions SET lastSeenId = MAX(lastSeenId, :id), notifiedId = MAX(notifiedId, :id), newCount = 0
        WHERE site = :site AND artist = :artist
        """,
    )
    suspend fun markSeen(site: String, artist: String, id: Long)

    @Query("UPDATE subscriptions SET notify = :notify WHERE site = :site AND artist = :artist")
    suspend fun setNotify(site: String, artist: String, notify: Boolean)

    @Query("UPDATE subscriptions SET notifiedId = MAX(notifiedId, :id) WHERE site = :site AND artist = :artist")
    suspend fun markNotified(site: String, artist: String, id: Long)
}

@Dao
interface SavedDao {
    @Query(
        """
        SELECT p.* FROM saved s JOIN posts p ON p.site = s.site AND p.id = s.postId
        ORDER BY s.savedAt DESC
        """,
    )
    fun savedPosts(): Flow<List<PostEntity>>

    @Query(
        """
        SELECT p.* FROM saved_folder_posts f JOIN posts p ON p.site = f.site AND p.id = f.postId
        JOIN saved s ON s.site = f.site AND s.postId = f.postId
        WHERE f.folderId = :folderId ORDER BY s.savedAt DESC
        """,
    )
    fun folderPosts(folderId: Long): Flow<List<PostEntity>>

    @Query(
        """
        SELECT p.* FROM likes l JOIN posts p ON p.site = l.site AND p.id = l.postId
        ORDER BY l.likedAt DESC
        """,
    )
    fun likedPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM saved_folders ORDER BY createdAt")
    fun folders(): Flow<List<SavedFolderEntity>>

    @Insert
    suspend fun createFolder(folder: SavedFolderEntity): Long

    @Query("DELETE FROM saved_folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToFolder(entry: SavedFolderPostEntity)

    @Query("DELETE FROM saved_folder_posts WHERE folderId = :folderId AND site = :site AND postId = :postId")
    suspend fun removeFromFolder(folderId: Long, site: String, postId: Long)

    @Query("SELECT folderId FROM saved_folder_posts WHERE site = :site AND postId = :postId")
    suspend fun foldersOf(site: String, postId: Long): List<Long>
}

@Dao
interface DownloadDao {
    @Insert
    suspend fun insertAll(entries: List<DownloadEntity>): List<Long>

    @Update
    suspend fun update(entry: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT 1000")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt LIMIT :limit")
    suspend fun queued(limit: Int): List<DownloadEntity>

    @Query("SELECT md5 FROM downloads WHERE status = 'DONE' AND md5 IS NOT NULL")
    fun doneMd5(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE md5 = :md5 AND original = :original AND status IN ('QUEUED','RUNNING','DONE','PAUSED'))")
    suspend fun exists(md5: String, original: Boolean): Boolean

    @Query("UPDATE downloads SET status = 'QUEUED', error = NULL, updatedAt = :now WHERE status IN ('RUNNING')")
    suspend fun requeueInterrupted(now: Long)

    @Query("UPDATE downloads SET status = :to, updatedAt = :now WHERE status = :from")
    suspend fun moveAll(from: DownloadStatus, to: DownloadStatus, now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'DONE'")
    suspend fun clearDone()
}

@Dao
interface PendingDao {
    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    @Query("DELETE FROM pending_actions WHERE site = :site AND postId = :postId AND action = :action")
    suspend fun deleteSame(site: String, postId: Long, action: String)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt LIMIT :limit")
    suspend fun next(limit: Int): List<PendingActionEntity>

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_actions SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bump(id: Long)

    @Query("SELECT COUNT(*) FROM pending_actions")
    fun count(): Flow<Int>
}

@Dao
interface TasteDao {
    @Query(
        """
        SELECT p.json AS json, l.likedAt AS at, 'LIKE' AS kind FROM likes l JOIN posts p ON p.site = l.site AND p.id = l.postId
        UNION ALL
        SELECT p.json AS json, s.savedAt AS at, 'SAVE' AS kind FROM saved s JOIN posts p ON p.site = s.site AND p.id = s.postId
        UNION ALL
        SELECT p.json AS json, d.at AS at, 'DISLIKE' AS kind FROM dislikes d JOIN posts p ON p.site = d.site AND p.id = d.postId
        """,
    )
    suspend fun signals(): List<SignalRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dislike(entry: DislikeEntity)

    @Query("SELECT tag FROM muted_taste")
    suspend fun muted(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mute(entry: MutedTagEntity)

    @Query("SELECT site || ':' || postId FROM view_history")
    suspend fun seenKeys(): List<String>

    @Query("SELECT site || ':' || postId FROM likes UNION SELECT site || ':' || postId FROM saved UNION SELECT site || ':' || postId FROM dislikes")
    suspend fun collectedKeys(): List<String>
}

data class SignalRow(val json: String, val at: Long, val kind: String)
