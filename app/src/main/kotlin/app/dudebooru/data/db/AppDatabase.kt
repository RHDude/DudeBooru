package app.dudebooru.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PostEntity::class,
        TagEntity::class,
        TagAliasEntity::class,
        LikeEntity::class,
        SavedEntity::class,
        NegativeTagEntity::class,
        PendingActionEntity::class,
        TasteEntity::class,
        SearchHistoryEntity::class,
        ArtistAvatarEntity::class,
        ViewHistoryEntity::class,
        SubscriptionEntity::class,
        SavedFolderEntity::class,
        SavedFolderPostEntity::class,
        DownloadEntity::class,
        DislikeEntity::class,
        MutedTagEntity::class,
    ],
    version = 5,
    exportSchema = true,
    // С версии 3 — только миграции: обновление не должно стирать лайки и сохранённые.
    // 5: колокольчик у подписок (notify, notifiedId).
    autoMigrations = [AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posts(): PostDao
    abstract fun tags(): TagDao
    abstract fun collections(): CollectionDao
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun artistAvatars(): ArtistAvatarDao
    abstract fun negativeTags(): NegativeTagDao
    abstract fun history(): HistoryDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun saved(): SavedDao
    abstract fun downloads(): DownloadDao
    abstract fun pending(): PendingDao
    abstract fun taste(): TasteDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dudebooru.db")
                // Совсем ранние сборки (до версии 3) пересоздаются; дальше — миграции.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                .build()
    }
}
