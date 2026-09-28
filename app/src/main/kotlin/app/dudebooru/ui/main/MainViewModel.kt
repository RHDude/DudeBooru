package app.dudebooru.ui.main

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dudebooru.AppContainer
import app.dudebooru.booru.filter.Blacklist
import app.dudebooru.booru.filter.BlacklistEntry
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.account.StoredAccount
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.data.db.DownloadEntity
import app.dudebooru.data.db.DownloadStatus
import app.dudebooru.data.db.PostEntity
import app.dudebooru.data.db.ViewHistoryEntity
import kotlinx.coroutines.flow.Flow
import app.dudebooru.data.settings.CensorPrefs
import app.dudebooru.data.settings.FeedPrefs
import app.dudebooru.data.settings.Profile
import app.dudebooru.data.settings.ThemeMode
import app.dudebooru.ui.feed.CustomChunk
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.feed.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application, val c: AppContainer) : AndroidViewModel(app) {

    val mode: StateFlow<ContentMode> = c.settings.contentMode.stateIn(viewModelScope, SharingStarted.Eagerly, ContentMode.SFW)
    val themeMode: StateFlow<ThemeMode> = c.settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val profile: StateFlow<Profile?> = c.settings.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val censor: StateFlow<Boolean> = c.settings.censorEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val censorPrefs: StateFlow<CensorPrefs> = c.settings.censorPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, CensorPrefs())
    val showHiddenCount: StateFlow<Boolean> = c.settings.showHiddenCount.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val adultConfirmed: StateFlow<Boolean> = c.settings.adultConfirmed.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Посты, с которых цензуру сняли тапом (до конца сессии). */
    private val _revealed = MutableStateFlow<Set<String>>(emptySet())
    val revealed: StateFlow<Set<String>> = _revealed.asStateFlow()

    fun reveal(post: Post) {
        _revealed.value = _revealed.value + post.key
    }

    /** Режим, для которого ждём «Мне есть 18». */
    private val _pendingMode = MutableStateFlow<ContentMode?>(null)
    val pendingMode: StateFlow<ContentMode?> = _pendingMode.asStateFlow()

    fun confirmAdult(confirmed: Boolean) {
        val mode = _pendingMode.value ?: return
        _pendingMode.value = null
        if (!confirmed) return
        viewModelScope.launch {
            c.settings.setAdultConfirmed()
            c.settings.setContentMode(mode)
        }
    }
    val accounts: StateFlow<Map<String, StoredAccount>> = c.accounts.accounts

    val liked: StateFlow<Set<String>> = c.db.collections().likedKeys().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val saved: StateFlow<Set<String>> = c.db.collections().savedKeys().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Папки = источники. В режиме NSFW Safebooru скрывается сама. */
    val folders: StateFlow<List<SiteConfig>> = combine(c.settings.folders, mode) { folders, mode ->
        folders.order.mapNotNull { c.registry.site(it) }
            .filter { it.id !in folders.hidden }
            .filter { !(mode == ContentMode.NSFW && it.safeOnly) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val lastSeen: StateFlow<Map<String, Long>> = c.settings.lastSeen.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val feedPrefs: StateFlow<FeedPrefs> = c.settings.feedPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, FeedPrefs())

    /** Отметка прошлого визита, снятая на старте: разделитель «Новое» не уезжает, пока листаешь. */
    private val _visitMarks = MutableStateFlow<Map<String, Long>>(emptyMap())
    val visitMarks: StateFlow<Map<String, Long>> = _visitMarks.asStateFlow()

    fun setFeedPrefs(prefs: FeedPrefs) {
        viewModelScope.launch { c.settings.setFeedPrefs(prefs) }
    }

    /** Навигация: главный экран всегда внизу стека. */
    val stack = mutableStateListOf<Route>(Route.Main)

    private val controllers = HashMap<String, FeedController>()
    private val savedSorts = HashMap<String, SortOrder>()

    init {
        viewModelScope.launch {
            // Новые работы у подписок — сразу при запуске, дальше раз в 6 часов в фоне.
            runCatching { c.subscriptions.checkAll() }
        }
        viewModelScope.launch {
            _visitMarks.value = c.settings.lastSeen.first()
            c.registry.sites.forEach { savedSorts[it.id] = c.settings.sort(it.id).first() }
            val last = c.settings.lastSite()
            val visible = folders.first { it.isNotEmpty() }
            select(visible.firstOrNull { it.id == last }?.id ?: visible.first().id)
            // Остальные папки подгружаются заранее: переключение мгновенное, счётчики новых видны сразу.
            visible.forEach { folderFeed(it.id).ensureLoaded() }
        }
        viewModelScope.launch {
            folders.collect { visible ->
                val current = _selected.value
                if (current != null && visible.none { it.id == current } && visible.isNotEmpty()) select(visible.first().id)
            }
        }
        viewModelScope.launch {
            mode.collect { folders.value.forEach { folderFeed(it.id).ensureLoaded() } }
        }
        viewModelScope.launch {
            // Добавили тег — посты уходят сразу; убрали — ленты перезагружаются, чтобы скрытое вернулось.
            var previous: Set<Long>? = null
            c.negative.entries.collect { list ->
                val ids = list.map { it.id }.toSet()
                val before = previous
                previous = ids
                if (before == null) return@collect
                val blacklist = Blacklist(list.map { BlacklistEntry(it.id, it.expression, it.site) })
                if ((before - ids).isNotEmpty()) controllers.values.forEach { it.refresh() }
                else controllers.values.forEach { it.applyBlacklist(blacklist) }
            }
        }
    }

    // --- навигация ---------------------------------------------------------------------------

    fun navigate(route: Route) {
        stack += route
    }

    /** «Назад»: закрывает верхний экран. На главном — возврат к источнику по умолчанию, потом выход. */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        val first = folders.value.firstOrNull()?.id
        if (first != null && _selected.value != first) {
            select(first)
            return true
        }
        return false
    }

    // --- папки -------------------------------------------------------------------------------

    fun select(siteId: String) {
        _selected.value = siteId
        viewModelScope.launch { c.settings.setLastSite(siteId) }
        folderFeed(siteId).ensureLoaded()
    }

    fun folderFeed(siteId: String): FeedController = controllers.getOrPut("folder:$siteId") {
        val site = requireNotNull(c.registry.site(siteId))
        FeedController(
            id = "folder:$siteId",
            site = site,
            c = c,
            scope = viewModelScope,
            tags = emptyList(),
            initialSort = savedSorts[siteId] ?: SortOrder.NEW,
            mode = { mode.value },
            onSortChanged = { sort -> viewModelScope.launch { c.settings.setSort(siteId, sort) } },
        )
    }

    fun controller(id: String): FeedController? = controllers[id]

    /** Результаты поиска открываются лентой поверх текущей, с той же сортировкой. */
    fun openSearchResults(siteId: String, tags: List<String>) {
        val id = "search:$siteId:${tags.joinToString(" ")}"
        val controller = controllers.getOrPut(id) {
            FeedController(
                id = id,
                site = requireNotNull(c.registry.site(siteId)),
                c = c,
                scope = viewModelScope,
                tags = tags,
                initialSort = folderFeed(siteId).sort.value,
                mode = { mode.value },
            )
        }
        controller.ensureLoaded()
        navigate(Route.Results(id))
    }

    fun openArtist(siteId: String, name: String) {
        val id = "artist:$siteId:$name"
        val controller = controllers.getOrPut(id) {
            FeedController(id, requireNotNull(c.registry.site(siteId)), c, viewModelScope, listOf(name), SortOrder.NEW, { mode.value })
        }
        controller.ensureLoaded()
        navigate(Route.Artist(id, siteId, name))
    }

    /**
     * Случайная работа из текущего источника с учётом режима (и негативных тегов — шаг «фильтры»).
     * Открывается просмотр; свайп — следующий случайный.
     */
    fun openRandom(): Route? {
        val siteId = _selected.value ?: return null
        val id = "random:$siteId"
        controllers.remove(id)
        val controller = FeedController(id, requireNotNull(c.registry.site(siteId)), c, viewModelScope, emptyList(), SortOrder.RANDOM, { mode.value })
        controllers[id] = controller
        controller.ensureLoaded()
        return Route.Viewer(id, startKey = "")
    }

    /** Новые с прошлого визита: посты свежее последнего увиденного, до 99. */
    fun newCounts(): StateFlow<Map<String, Int>> = newCountsFlow

    private val newCountsFlow: StateFlow<Map<String, Int>> = run {
        val flow = MutableStateFlow<Map<String, Int>>(emptyMap())
        val watched = HashSet<String>()
        viewModelScope.launch {
            folders.collect { visible ->
                visible.filter { watched.add(it.id) }.forEach { site ->
                    val controller = folderFeed(site.id)
                    viewModelScope.launch {
                        combine(controller.state, lastSeen) { state, seen ->
                            val mark = seen[site.id] ?: 0L
                            if (mark == 0L) 0 else state.items.sumOf { item -> item.posts.count { it.id > mark } }.coerceAtMost(99)
                        }.collect { count -> flow.value = flow.value + (site.id to count) }
                    }
                }
            }
        }
        flow.asStateFlow()
    }

    /** Человек открыл папку и увидел верх ленты. */
    fun markSeen(siteId: String, topPostId: Long) {
        app.dudebooru.util.DudeLog.d("seen", "markSeen $siteId $topPostId (was ${lastSeen.value[siteId]})")
        viewModelScope.launch { c.settings.setLastSeen(siteId, topPostId) }
    }

    fun markAllSeen(siteId: String) {
        val top = folderFeed(siteId).state.value.items.maxOfOrNull { item -> item.posts.maxOf { it.id } } ?: return
        markSeen(siteId, top)
    }

    fun hideFolder(siteId: String) {
        viewModelScope.launch { c.settings.setFolderHidden(siteId, true) }
    }

    fun moveFolder(siteId: String, delta: Int) {
        viewModelScope.launch {
            val order = c.settings.folders.first().order.toMutableList()
            val i = order.indexOf(siteId)
            val j = (i + delta).coerceIn(0, order.lastIndex)
            if (i < 0 || i == j) return@launch
            order.add(j, order.removeAt(i))
            c.settings.setFolderOrder(order)
        }
    }

    // --- быстрые переключатели ---------------------------------------------------------------

    fun setMode(mode: ContentMode) {
        if (mode != ContentMode.SFW && !adultConfirmed.value) {
            _pendingMode.value = mode
            return
        }
        viewModelScope.launch { c.settings.setContentMode(mode) }
    }

    /** Глаз снимает и возвращает цензуру для всех лент сразу. */
    fun toggleCensor() {
        val enable = !censor.value
        if (enable) _revealed.value = emptySet()
        viewModelScope.launch { c.settings.setCensorEnabled(enable) }
    }

    fun toggleTheme(currentlyDark: Boolean) {
        viewModelScope.launch { c.settings.setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK) }
    }

    // --- лайки и сохранённые (синхронизация с сайтом — шаг «коллекции») -----------------------

    fun toggleLike(post: Post) {
        viewModelScope.launch { c.collections.setLiked(post, post.key !in liked.value) }
    }

    /** Двойной тап только ставит лайк, не снимает. */
    fun like(post: Post) {
        if (post.key in liked.value) return
        viewModelScope.launch { c.collections.setLiked(post, true) }
    }

    fun toggleSave(post: Post) {
        viewModelScope.launch { c.collections.setSaved(post, post.key !in saved.value) }
    }

    // --- шаг «коллекции» ---------------------------------------------------------------------

    val likeCount: StateFlow<Int> = c.db.collections().likeCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** md5 скачанного: отметка в ленте, повторно не качается. */
    val downloadedMd5: StateFlow<Set<String>> = c.downloads.doneMd5.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val downloads: StateFlow<List<DownloadEntity>> = c.downloads.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** «2 из 5» прямо в пункте меню: готово / всего в текущей пачке, пока она не закончилась. */
    val downloadProgress: StateFlow<Pair<Int, Int>?> = c.downloads.all.map { list ->
        val active = list.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PAUSED }
        if (active.isEmpty()) return@map null
        val batchStart = active.minOf { it.createdAt }
        val batch = list.filter { it.createdAt >= batchStart }
        batch.count { it.status == DownloadStatus.DONE } to batch.size
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val artistsWithNew: StateFlow<Int> = c.subscriptions.withNew.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val keepHistory: StateFlow<Boolean> = c.settings.keepHistory.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Просмотренный пост — в «Историю» (если её ведут). */
    fun recordView(post: Post) {
        if (!keepHistory.value) return
        viewModelScope.launch { c.db.history().upsert(ViewHistoryEntity(post.site, post.id, System.currentTimeMillis())) }
    }

    // --- шаг «рекомендации» ------------------------------------------------------------------

    /** Метка «new» у «Рекомендаций»: с прошлого открытия прибавилось лайков — подборка обновилась. */
    val recsNew: StateFlow<Boolean> = combine(likeCount, c.settings.recsSeenLikes) { likes, seen ->
        likes >= c.recs.coldStartLikes && likes - seen >= 3
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markRecsSeen() {
        viewModelScope.launch { c.settings.setRecsSeenLikes(likeCount.value) }
    }

    fun recsFeed(siteId: String): FeedController = controllers.getOrPut("recs:$siteId") {
        val site = requireNotNull(c.registry.site(siteId))
        FeedController(
            "recs:$siteId", site, c, viewModelScope, emptyList(), SortOrder.NEW, { mode.value },
            customSource = { page, shown ->
                val recs = c.recs.page(site, mode.value, page, shown)
                CustomChunk(recs.map { FeedItem(listOf(it.post), it.reasons, it.explore) }, hasMore = recs.isNotEmpty() && page < 10)
            },
        )
    }

    /** Холодный старт: популярное за неделю. */
    fun coldStartFeed(siteId: String): FeedController = controllers.getOrPut("recs-cold:$siteId") {
        FeedController("recs-cold:$siteId", requireNotNull(c.registry.site(siteId)), c, viewModelScope, emptyList(), SortOrder.POPULAR_WEEK, { mode.value })
    }

    fun openSimilar(post: Post) {
        val id = "similar:${post.key}"
        val controller = controllers.getOrPut(id) {
            FeedController(
                id, requireNotNull(c.registry.site(post.site)), c, viewModelScope, emptyList(), SortOrder.BEST, { mode.value },
                customSource = { page, shown ->
                    val recs = c.recs.similar(post, mode.value, page, shown)
                    CustomChunk(recs.map { FeedItem(listOf(it.post), it.reasons) }, hasMore = recs.isNotEmpty() && page < 5)
                },
            )
        }
        controller.ensureLoaded()
        navigate(Route.Similar(id, post))
    }

    fun dislike(post: Post) {
        viewModelScope.launch { c.recs.dislike(post) }
    }

    /** Лента из базы: «Сохранённые», лайки, история. */
    fun localFeed(id: String, source: Flow<List<Post>>): FeedController = controllers.getOrPut(id) {
        val site = c.registry.sites.first()
        FeedController(id, site, c, viewModelScope, emptyList(), SortOrder.NEW, { mode.value }, localSource = source)
    }

    fun decodePosts(entities: Flow<List<PostEntity>>): Flow<List<Post>> = entities.map { list ->
        list.mapNotNull { runCatching { BooruJson.decodeFromString(Post.serializer(), it.json) }.getOrNull() }
    }

    val savedCount: StateFlow<Int> = c.db.collections().savedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}
