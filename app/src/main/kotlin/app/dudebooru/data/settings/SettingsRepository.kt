package app.dudebooru.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.site.Sites
import app.dudebooru.data.net.DohProvider
import app.dudebooru.data.net.ProxyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class FolderSettings(
    val order: List<String>,
    val hidden: Set<String>,
)

/** Светлая или тёмная: как в системе, вручную, по расписанию или по закату и рассвету. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, SCHEDULE, SUNSET }

/** Настройки → Скачивание. */
data class DownloadPrefs(
    /** Папка, выбранная системным диалогом (SAF); null — Pictures/DudeBooru. */
    val treeUri: String? = null,
    val template: String = DEFAULT_TEMPLATE,
    val wifiOnly: Boolean = false,
    val parallel: Int = 2,
    val writeTags: Boolean = true,
) {
    companion object {
        const val DEFAULT_TEMPLATE = "Booru/{site}/{artist}/{site}_{id}.{ext}"
    }
}

/** Настройки → Аккаунты: что уходит на сайт. */
data class SyncPrefs(
    /** Сохранённые синхронизируются с избранным сайта (по умолчанию вкл.). */
    val syncSaved: Boolean = true,
    /** Лайки дублируются на сайт голосом (по умолчанию выкл.: upvote публично меняет score). */
    val mirrorLikes: Boolean = false,
)


enum class CensorStyle { SPOILER, BLUR, PIXELATE }

/** Настройки → Контент → цензура NSFW. Включение — глаз в боковом меню ([SettingsRepository.censorEnabled]). */
data class CensorPrefs(
    val style: CensorStyle = CensorStyle.SPOILER,
    /** 0..1: сила размытия или крупность пикселей. */
    val strength: Float = 0.7f,
    /** Только в ленте или в ленте и в просмотре. */
    val inViewer: Boolean = false,
    /** Размывать и rating:s (слегка откровенное). */
    val blurSensitive: Boolean = false,
)

/** Настройки → Лента. */
data class FeedPrefs(
    /** Сетка вместо постов во всю ширину. */
    val grid: Boolean = false,
    val doubleTapLike: Boolean = true,
    /** GIF в ленте анимируются. */
    val animateGifs: Boolean = true,
    /** Отметка «скрыто 12» в шапке ленты (хранится отдельно, см. [SettingsRepository.showHiddenCount]). */
    val showHiddenCount: Boolean = true,
)

/** Локальный профиль: к аккаунтам сайтов не привязан и никуда не отправляется. */
data class Profile(
    val name: String,
    val nick: String,
    val avatarUrl: String?,
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val contentMode: Flow<ContentMode> = store.data.map { prefs ->
        prefs[CONTENT_MODE]?.let { runCatching { ContentMode.valueOf(it) }.getOrNull() } ?: ContentMode.SFW
    }

    suspend fun setContentMode(mode: ContentMode) {
        store.edit { it[CONTENT_MODE] = mode.name }
    }

    val folders: Flow<FolderSettings> = store.data.map { prefs ->
        val known = Sites.builtIn.map { it.id }
        val saved = prefs[FOLDER_ORDER]?.split(',')?.filter { it in known }.orEmpty()
        FolderSettings(
            order = saved + known.filter { it !in saved },
            hidden = prefs[HIDDEN_FOLDERS] ?: DEFAULT_HIDDEN,
        )
    }

    fun sort(siteId: String): Flow<SortOrder> = store.data.map { prefs ->
        prefs[sortKey(siteId)]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.NEW
    }

    suspend fun setSort(siteId: String, sort: SortOrder) {
        store.edit { it[sortKey(siteId)] = sort.name }
    }

    suspend fun lastSite(): String? = store.data.first()[LAST_SITE]

    suspend fun setLastSite(siteId: String) {
        store.edit { it[LAST_SITE] = siteId }
    }

    /** Аккаунты без секретов (логин, уровень, лимит) — JSON; секреты в SecretStore. */
    val accountsJson: Flow<String?> = store.data.map { it[ACCOUNTS] }

    suspend fun setAccountsJson(json: String) {
        store.edit { it[ACCOUNTS] = json }
    }

    suspend fun tagSummaryVersion(siteId: String): Pair<Long, Long>? {
        val prefs = store.data.first()
        val version = prefs[longPreferencesKey("tag_summary_version.$siteId")] ?: return null
        val at = prefs[longPreferencesKey("tag_summary_at.$siteId")] ?: 0L
        return version to at
    }

    suspend fun setTagSummaryVersion(siteId: String, version: Long, at: Long) {
        store.edit {
            it[longPreferencesKey("tag_summary_version.$siteId")] = version
            it[longPreferencesKey("tag_summary_at.$siteId")] = at
        }
    }

    val proxy: Flow<ProxyConfig> = store.data.map { prefs ->
        prefs[PROXY]?.let { runCatching { BooruJson.decodeFromString(ProxyConfig.serializer(), it) }.getOrNull() } ?: ProxyConfig()
    }

    suspend fun setProxy(config: ProxyConfig) {
        store.edit { it[PROXY] = BooruJson.encodeToString(ProxyConfig.serializer(), config) }
    }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    val profile: Flow<Profile> = store.data.map { prefs ->
        Profile(
            name = prefs[PROFILE_NAME] ?: DEFAULT_NAME,
            nick = prefs[PROFILE_NICK] ?: DEFAULT_NICK,
            avatarUrl = prefs[PROFILE_AVATAR],
        )
    }

    suspend fun setAvatar(url: String?) {
        store.edit { if (url == null) it.remove(PROFILE_AVATAR) else it[PROFILE_AVATAR] = url }
    }

    val feedPrefs: Flow<FeedPrefs> = store.data.map { prefs ->
        FeedPrefs(
            grid = prefs[FEED_GRID] ?: false,
            doubleTapLike = prefs[DOUBLE_TAP_LIKE] ?: true,
            animateGifs = prefs[ANIMATE_GIFS] ?: true,
        )
    }

    suspend fun setFeedPrefs(value: FeedPrefs) {
        store.edit {
            it[FEED_GRID] = value.grid
            it[DOUBLE_TAP_LIKE] = value.doubleTapLike
            it[ANIMATE_GIFS] = value.animateGifs
        }
    }

    val censorPrefs: Flow<CensorPrefs> = store.data.map { prefs ->
        CensorPrefs(
            style = prefs[CENSOR_STYLE]?.let { runCatching { CensorStyle.valueOf(it) }.getOrNull() } ?: CensorStyle.SPOILER,
            strength = prefs[CENSOR_STRENGTH] ?: 0.7f,
            inViewer = prefs[CENSOR_IN_VIEWER] ?: false,
            blurSensitive = prefs[CENSOR_SENSITIVE] ?: false,
        )
    }

    suspend fun setCensorPrefs(value: CensorPrefs) {
        store.edit {
            it[CENSOR_STYLE] = value.style.name
            it[CENSOR_STRENGTH] = value.strength
            it[CENSOR_IN_VIEWER] = value.inViewer
            it[CENSOR_SENSITIVE] = value.blurSensitive
        }
    }

    /** «Мне есть 18» подтверждено: NSFW и «Всё» включаются без вопроса. */
    val adultConfirmed: Flow<Boolean> = store.data.map { it[ADULT_CONFIRMED] ?: false }

    suspend fun setAdultConfirmed() {
        store.edit { it[ADULT_CONFIRMED] = true }
    }

    val downloadPrefs: Flow<DownloadPrefs> = store.data.map { prefs ->
        DownloadPrefs(
            treeUri = prefs[DL_TREE],
            template = prefs[DL_TEMPLATE] ?: DownloadPrefs.DEFAULT_TEMPLATE,
            wifiOnly = prefs[DL_WIFI] ?: false,
            parallel = prefs[DL_PARALLEL] ?: 2,
            writeTags = prefs[DL_TAGS] ?: true,
        )
    }

    suspend fun setDownloadPrefs(value: DownloadPrefs) {
        store.edit {
            if (value.treeUri == null) it.remove(DL_TREE) else it[DL_TREE] = value.treeUri
            it[DL_TEMPLATE] = value.template
            it[DL_WIFI] = value.wifiOnly
            it[DL_PARALLEL] = value.parallel
            it[DL_TAGS] = value.writeTags
        }
    }

    val syncPrefs: Flow<SyncPrefs> = store.data.map { SyncPrefs(it[SYNC_SAVED] ?: true, it[MIRROR_LIKES] ?: false) }

    suspend fun setSyncPrefs(value: SyncPrefs) {
        store.edit {
            it[SYNC_SAVED] = value.syncSaved
            it[MIRROR_LIKES] = value.mirrorLikes
        }
    }

    suspend fun syncPrefsNow(): SyncPrefs = syncPrefs.first()

    /** Вести историю просмотров (выключается в «Приватности»). */
    val keepHistory: Flow<Boolean> = store.data.map { it[KEEP_HISTORY] ?: true }

    suspend fun setProfile(name: String, nick: String) {
        store.edit {
            it[PROFILE_NAME] = name.ifBlank { DEFAULT_NAME }
            it[PROFILE_NICK] = nick.removePrefix("@").ifBlank { DEFAULT_NICK }
        }
    }

    /** Отметка «скрыто 12» в шапке ленты. */
    val showHiddenCount: Flow<Boolean> = store.data.map { it[SHOW_HIDDEN_COUNT] ?: true }

    suspend fun setShowHiddenCount(value: Boolean) {
        store.edit { it[SHOW_HIDDEN_COUNT] = value }
    }

    /** Глаз в боковом меню: цензура NSFW для всех лент сразу. */
    val censorEnabled: Flow<Boolean> = store.data.map { it[CENSOR] ?: true }

    suspend fun setCensorEnabled(enabled: Boolean) {
        store.edit { it[CENSOR] = enabled }
    }

    /** Самый новый пост, который человек видел в папке, — для счётчика новых. */
    val lastSeen: Flow<Map<String, Long>> = store.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith("last_seen.") }
            .associate { it.key.name.removePrefix("last_seen.") to (it.value as? Long ?: 0L) }
    }

    suspend fun setLastSeen(siteId: String, postId: Long) {
        store.edit { prefs ->
            val key = longPreferencesKey("last_seen.$siteId")
            if ((prefs[key] ?: 0L) < postId) prefs[key] = postId
        }
    }

    suspend fun setFolderOrder(order: List<String>) {
        store.edit { it[FOLDER_ORDER] = order.joinToString(",") }
    }

    suspend fun setFolderHidden(siteId: String, hidden: Boolean) {
        store.edit { prefs ->
            val current = prefs[HIDDEN_FOLDERS] ?: DEFAULT_HIDDEN
            prefs[HIDDEN_FOLDERS] = if (hidden) current + siteId else current - siteId
        }
    }

    val doh: Flow<DohProvider> = store.data.map { prefs ->
        prefs[DOH]?.let { runCatching { DohProvider.valueOf(it) }.getOrNull() } ?: DohProvider.NONE
    }

    /** Пресет темы или «custom». */
    val themeId: Flow<String> = store.data.map { it[THEME_ID] ?: "monet" }

    suspend fun setThemeId(id: String) {
        store.edit { it[THEME_ID] = id }
    }

    val customTheme: Flow<app.dudebooru.ui.theme.AppTheme?> = store.data.map { prefs ->
        prefs[CUSTOM_THEME]?.let { runCatching { BooruJson.decodeFromString(app.dudebooru.ui.theme.AppTheme.serializer(), it) }.getOrNull() }
    }

    suspend fun setCustomTheme(theme: app.dudebooru.ui.theme.AppTheme) {
        store.edit { it[CUSTOM_THEME] = BooruJson.encodeToString(app.dudebooru.ui.theme.AppTheme.serializer(), theme) }
    }

    /** Расписание ночи в минутах от полуночи (по умолчанию 22:00–07:00). */
    val nightSchedule: Flow<Pair<Int, Int>> = store.data.map { (it[NIGHT_START] ?: 22 * 60) to (it[NIGHT_END] ?: 7 * 60) }

    suspend fun setNightSchedule(start: Int, end: Int) {
        store.edit {
            it[NIGHT_START] = start
            it[NIGHT_END] = end
        }
    }

    /** Ручное переключение при расписании/закате действует до следующей смены. */
    val nightOverride: Flow<Pair<Boolean, Long>?> = store.data.map { prefs ->
        val until = prefs[OVERRIDE_UNTIL] ?: return@map null
        (prefs[OVERRIDE_DARK] ?: false) to until
    }

    suspend fun setNightOverride(dark: Boolean?, until: Long) {
        store.edit {
            if (dark == null) {
                it.remove(OVERRIDE_DARK)
                it.remove(OVERRIDE_UNTIL)
            } else {
                it[OVERRIDE_DARK] = dark
                it[OVERRIDE_UNTIL] = until
            }
        }
    }

    /** Первый запуск пройден. */
    val onboarded: Flow<Boolean> = store.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded() {
        store.edit { it[ONBOARDED] = true }
    }

    /** Рекорд в игре без сети. */
    val gameRecord: Flow<Int> = store.data.map { it[GAME_RECORD] ?: 0 }

    suspend fun setGameRecord(value: Int) {
        store.edit { prefs -> if ((prefs[GAME_RECORD] ?: 0) < value) prefs[GAME_RECORD] = value }
    }

    /** Сколько было лайков, когда рекомендации открывали в последний раз — для метки «new». */
    val recsSeenLikes: Flow<Int> = store.data.map { it[RECS_SEEN] ?: 0 }

    suspend fun setRecsSeenLikes(value: Int) {
        store.edit { it[RECS_SEEN] = value }
    }

    suspend fun setDoh(provider: DohProvider) {
        store.edit { it[DOH] = provider.name }
    }

    private fun sortKey(siteId: String) = stringPreferencesKey("sort.$siteId")

    private companion object {
        val CONTENT_MODE = stringPreferencesKey("content_mode")
        val FOLDER_ORDER = stringPreferencesKey("folder_order")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val LAST_SITE = stringPreferencesKey("last_site")
        val ACCOUNTS = stringPreferencesKey("accounts")
        val PROXY = stringPreferencesKey("proxy")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_NICK = stringPreferencesKey("profile_nick")
        val PROFILE_AVATAR = stringPreferencesKey("profile_avatar")
        val CENSOR = androidx.datastore.preferences.core.booleanPreferencesKey("censor_enabled")
        val FEED_GRID = androidx.datastore.preferences.core.booleanPreferencesKey("feed_grid")
        val DOUBLE_TAP_LIKE = androidx.datastore.preferences.core.booleanPreferencesKey("double_tap_like")
        val ANIMATE_GIFS = androidx.datastore.preferences.core.booleanPreferencesKey("animate_gifs")
        val CENSOR_STYLE = stringPreferencesKey("censor_style")
        val CENSOR_STRENGTH = androidx.datastore.preferences.core.floatPreferencesKey("censor_strength")
        val CENSOR_IN_VIEWER = androidx.datastore.preferences.core.booleanPreferencesKey("censor_in_viewer")
        val CENSOR_SENSITIVE = androidx.datastore.preferences.core.booleanPreferencesKey("censor_sensitive")
        val ADULT_CONFIRMED = androidx.datastore.preferences.core.booleanPreferencesKey("adult_confirmed")
        val SHOW_HIDDEN_COUNT = androidx.datastore.preferences.core.booleanPreferencesKey("show_hidden_count")
        val DL_TREE = stringPreferencesKey("dl_tree")
        val DL_TEMPLATE = stringPreferencesKey("dl_template")
        val DL_WIFI = androidx.datastore.preferences.core.booleanPreferencesKey("dl_wifi")
        val DL_PARALLEL = androidx.datastore.preferences.core.intPreferencesKey("dl_parallel")
        val DL_TAGS = androidx.datastore.preferences.core.booleanPreferencesKey("dl_tags")
        val SYNC_SAVED = androidx.datastore.preferences.core.booleanPreferencesKey("sync_saved")
        val MIRROR_LIKES = androidx.datastore.preferences.core.booleanPreferencesKey("mirror_likes")
        val KEEP_HISTORY = androidx.datastore.preferences.core.booleanPreferencesKey("keep_history")
        val DOH = stringPreferencesKey("doh")
        val RECS_SEEN = androidx.datastore.preferences.core.intPreferencesKey("recs_seen_likes")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEME = stringPreferencesKey("custom_theme")
        val NIGHT_START = androidx.datastore.preferences.core.intPreferencesKey("night_start")
        val NIGHT_END = androidx.datastore.preferences.core.intPreferencesKey("night_end")
        val OVERRIDE_DARK = androidx.datastore.preferences.core.booleanPreferencesKey("night_override_dark")
        val OVERRIDE_UNTIL = longPreferencesKey("night_override_until")
        val ONBOARDED = androidx.datastore.preferences.core.booleanPreferencesKey("onboarded")
        val GAME_RECORD = androidx.datastore.preferences.core.intPreferencesKey("game_record")

        const val DEFAULT_NAME = "Чувак"
        const val DEFAULT_NICK = "dude"

        /** Sakugabooru включается в настройках. */
        val DEFAULT_HIDDEN = setOf(Sites.SAKUGABOORU.id)
    }
}
