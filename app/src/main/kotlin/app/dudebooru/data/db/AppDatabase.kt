package app.dudebooru.data.db

import android.content.Context
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
    ],
    version = 3,
    exportSchema = true,
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

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dudebooru.db")
                // До 1.0 схема меняется от шага к шагу; с первого релиза — только миграции.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
