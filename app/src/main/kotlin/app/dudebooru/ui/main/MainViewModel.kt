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
import app.dudebooru.data.db.LikeEntity
import app.dudebooru.data.db.SavedEntity
import app.dudebooru.data.settings.CensorPrefs
import app.dudebooru.data.settings.FeedPrefs
import app.dudebooru.data.settings.Profile
import app.dudebooru.data.settings.ThemeMode
import app.dudebooru.ui.feed.FeedController
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
        viewModelScope.launch {
            val dao = c.db.collections()
            if (post.key in liked.value) dao.unlike(post.site, post.id) else dao.like(LikeEntity(post.site, post.id, System.currentTimeMillis()))
        }
    }

    /** Двойной тап только ставит лайк, не снимает. */
    fun like(post: Post) {
        if (post.key in liked.value) return
        viewModelScope.launch { c.db.collections().like(LikeEntity(post.site, post.id, System.currentTimeMillis())) }
    }

    fun toggleSave(post: Post) {
        viewModelScope.launch {
            val dao = c.db.collections()
            if (post.key in saved.value) dao.unsave(post.site, post.id) else dao.save(SavedEntity(post.site, post.id, System.currentTimeMillis()))
        }
    }

    val savedCount: StateFlow<Int> = c.db.collections().savedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}
