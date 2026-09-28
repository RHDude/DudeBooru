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
import app.dudebooru.data.settings.FolderPrefs
import app.dudebooru.data.settings.PrivacyPrefs
import app.dudebooru.data.settings.ViewerPrefs
import app.dudebooru.data.settings.Profile
import app.dudebooru.data.settings.ThemeMode
import app.dudebooru.ui.theme.AppTheme
import app.dudebooru.ui.theme.NightSchedule
import app.dudebooru.ui.theme.ThemePresets
import app.dudebooru.ui.theme.ThemeFiles
import app.dudebooru.ui.feed.CustomChunk
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.feed.FeedItem
import app.dudebooru.ui.update.UpdateController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Настройка прямо сейчас: DataStore читает файл один раз, дальше из памяти. */
private fun <T> Flow<T>.now(): T = kotlinx.coroutines.runBlocking { first() }

class MainViewModel(app: Application, val c: AppContainer) : AndroidViewModel(app) {

    val mode: StateFlow<ContentMode> = c.settings.contentMode.stateIn(viewModelScope, SharingStarted.Eagerly, ContentMode.SFW)
    // Тема и первый запуск читаются сразу: первый кадр без мигания светлым.
    val themeMode: StateFlow<ThemeMode> = c.settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, c.settings.themeMode.now())
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
    val viewerPrefs: StateFlow<ViewerPrefs> =
        c.settings.viewerPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, ViewerPrefs())
    val folderPrefs: StateFlow<FolderPrefs> =
        c.settings.folderPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, FolderPrefs())

    /** null — ещё читается: блокировка не должна мигнуть содержимым до первого кадра. */
    val privacyPrefs: StateFlow<PrivacyPrefs?> =
        c.settings.privacyPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Отметка прошлого визита, снятая на старте: разделитель «Новое» не уезжает, пока листаешь. */
    private val _visitMarks = MutableStateFlow<Map<String, Long>>(emptyMap())
    val visitMarks: StateFlow<Map<String, Long>> = _visitMarks.asStateFlow()

    fun setFeedPrefs(prefs: FeedPrefs) {
        viewModelScope.launch { c.settings.setFeedPrefs(prefs) }
    }

    /** Навигация: главный экран всегда внизу стека. */
    val stack = mutableStateListOf<Route>(Route.Main)

    /** Пост, картинка которого перелетает между лентой и просмотром (см. [app.dudebooru.ui.common.sharedPost]). */
    val sharedKey = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Обновления из GitHub Releases: «О приложении», пункт в меню, окно «Вышла версия». Объявлено до init: он его вызывает. */
    val updates = UpdateController(app, c, viewModelScope)

    private val controllers = HashMap<String, FeedController>()

    init {
        viewModelScope.launch {
            // Новые работы у подписок — сразу при запуске, дальше раз в 6 часов в фоне.
            // Приложение открыто: хватает счётчика в меню, уведомление о тех же работах потом не придёт.
            runCatching { c.subscriptions.checkAll().forEach { c.subscriptions.markNotified(it) } }
        }
        updates.checkOnStart()
        viewModelScope.launch {
            _visitMarks.value = c.settings.lastSeen.first()
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
            // Сортировка читается сразу: папку могут создать раньше, чем дочитаются настройки
            // (счётчики новых, смена режима), и тогда выбор сбрасывался бы на «Новое».
            initialSort = c.settings.sort(siteId).now(),
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

    /** Случайная подборка, загруженная заранее — пока открыто боковое меню. */
    private var randomReady: FeedController? = null
    private var randomSeq = 0

    private fun newRandom(siteId: String): FeedController = FeedController(
        id = "random:$siteId:${++randomSeq}",
        site = requireNotNull(c.registry.site(siteId)),
        c = c,
        scope = viewModelScope,
        tags = emptyList(),
        initialSort = SortOrder.RANDOM,
        mode = { mode.value },
        pageSize = 20,
        minVisible = 1,
    )

    private fun FeedController.usableFor(siteId: String): Boolean {
        val s = state.value
        return site.id == siteId && s.error == null && s.key?.mode == this@MainViewModel.mode.value
    }

    /** Меню открыли — готовим случайный пост, чтобы по тапу он показался сразу. */
    fun warmRandom() {
        val siteId = _selected.value ?: return
        if (randomReady?.usableFor(siteId) == true) return
        randomReady = newRandom(siteId).also { it.ensureLoaded() }
    }

    /**
     * Случайная работа из текущего источника с учётом режима и негативных тегов.
     * Открывается просмотр; свайп — следующий случайный.
     */
    fun openRandom(): Route? {
        val siteId = _selected.value ?: return null
        val controller = randomReady?.takeIf { it.usableFor(siteId) } ?: newRandom(siteId).also { it.ensureLoaded() }
        randomReady = null
        // Прошлые случайные подборки больше не нужны: «Случайный пост» открывается только с главного экрана.
        controllers.keys.removeAll { it.startsWith("random:") }
        controllers[controller.id] = controller
        return Route.Viewer(controller.id, startKey = "", wholeFeed = true)
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

    private fun resolveTheme(id: String, custom: AppTheme?) =
        if (id == "custom") custom ?: ThemePresets.MONET else ThemePresets.byId(id) ?: ThemePresets.MONET

    val appTheme: StateFlow<AppTheme> = combine(c.settings.themeId, c.settings.customTheme, ::resolveTheme)
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolveTheme(c.settings.themeId.now(), c.settings.customTheme.now()))

    val nightSchedule: StateFlow<Pair<Int, Int>> = c.settings.nightSchedule.stateIn(viewModelScope, SharingStarted.Eagerly, c.settings.nightSchedule.now())
    val nightOverride: StateFlow<Pair<Boolean, Long>?> = c.settings.nightOverride.stateIn(viewModelScope, SharingStarted.Eagerly, c.settings.nightOverride.now())

    /** Мгновенный результат кнопки — пока DataStore не записал, чтобы круговая смена шла без задержки. */
    private val _instantDark = MutableStateFlow<Boolean?>(null)
    val instantDark: StateFlow<Boolean?> = _instantDark.asStateFlow()

    /** Тёмная ли тема по режиму: система, вручную, расписание или закат (с ручным переключением до следующей смены). */
    fun effectiveDark(systemDark: Boolean, now: java.time.ZonedDateTime): Boolean {
        _instantDark.value?.let { return it }
        val override = nightOverride.value
        return when (themeMode.value) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SCHEDULE, ThemeMode.SUNSET -> {
                if (override != null && now.toInstant().toEpochMilli() < override.second) return override.first
                val (start, end) = scheduleFor(now)
                NightSchedule.scheduleDark(now.toLocalTime(), start, end)
            }
        }
    }

    private fun scheduleFor(now: java.time.ZonedDateTime): Pair<Int, Int> =
        if (themeMode.value == ThemeMode.SUNSET) {
            val (sunrise, sunset) = NightSchedule.sunTimes(now.toLocalDate(), now.zone)
            sunset to sunrise
        } else {
            nightSchedule.value
        }

    fun toggleTheme(currentlyDark: Boolean) {
        val target = !currentlyDark
        _instantDark.value = target
        viewModelScope.launch {
            when (themeMode.value) {
                ThemeMode.SCHEDULE, ThemeMode.SUNSET -> {
                    val now = java.time.ZonedDateTime.now()
                    val (start, end) = scheduleFor(now)
                    c.settings.setNightOverride(target, NightSchedule.nextScheduleSwitch(now, start, end))
                }
                else -> c.settings.setThemeMode(if (target) ThemeMode.DARK else ThemeMode.LIGHT)
            }
            kotlinx.coroutines.delay(600)
            _instantDark.value = null
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            c.settings.setNightOverride(null, 0)
            c.settings.setThemeMode(mode)
        }
    }

    fun setNightSchedule(start: Int, end: Int) {
        viewModelScope.launch { c.settings.setNightSchedule(start, end) }
    }

    val customTheme: StateFlow<AppTheme?> = c.settings.customTheme.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Черновик для редактора (фон из поста): редактор забирает его при открытии. */
    val editorDraft = MutableStateFlow<AppTheme?>(null)

    /** Последний известный вариант темы — редактор открывается на нём. */
    var lastDark = false

    /** Тема из файла или кода ждёт «Применить». */
    val pendingTheme = MutableStateFlow<AppTheme?>(null)

    fun offerTheme(theme: AppTheme) {
        pendingTheme.value = theme
    }

    fun importThemeFile(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val theme = ThemeFiles.readImport(context, uri)
            if (theme == null) {
                android.widget.Toast.makeText(context, context.getString(app.dudebooru.R.string.themes_bad_file), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                pendingTheme.value = theme
            }
        }
    }

    fun applyPendingTheme() {
        val theme = pendingTheme.value ?: return
        pendingTheme.value = null
        saveCustomTheme(theme)
    }

    /** Картинка поста — фоном своей темы; открывается редактор с черновиком. */
    suspend fun setThemeBackgroundFrom(context: android.content.Context, url: String): Boolean {
        val path = ThemeFiles.imageFromUrl(context, url) ?: return false
        val base = appTheme.value.let { if (it.id == "custom") it else it.copy(id = "custom", name = context.getString(app.dudebooru.R.string.theme_custom)) }
        editorDraft.value = base.copy(
            background = base.background.copy(kind = app.dudebooru.ui.theme.ThemeBackground.Kind.IMAGE, imagePath = path),
        )
        return true
    }

    val gameRecord: StateFlow<Int> = c.settings.gameRecord.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun saveGameRecord(score: Int) {
        viewModelScope.launch { c.settings.setGameRecord(score) }
    }

    /** null — ещё не прочитали: не мигаем экраном первого запуска у тех, кто его прошёл. */
    val onboarded: StateFlow<Boolean?> = c.settings.onboarded.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, c.settings.onboarded.now())

    fun finishOnboarding() {
        viewModelScope.launch { c.settings.setOnboarded() }
    }

    /** Выбор с экранов первого запуска — в настройки. */
    fun applyOnboarding(context: android.content.Context, choice: app.dudebooru.ui.face.OnboardingChoice) {
        if (choice.icon != app.dudebooru.ui.face.IconManager.current(context)) app.dudebooru.ui.face.IconManager.apply(context, choice.icon)
        viewModelScope.launch {
            if (choice.mode != ContentMode.SFW) c.settings.setAdultConfirmed()
            c.settings.setContentMode(choice.mode)
            c.settings.setCensorEnabled(choice.censor)
            c.settings.setFolderOrder(choice.order)
            app.dudebooru.booru.site.Sites.builtIn.forEach { c.settings.setFolderHidden(it.id, it.id in choice.hidden) }
            when (choice.look) {
                app.dudebooru.ui.face.OnboardingChoice.Look.MONET -> {
                    c.settings.setThemeId(ThemePresets.MONET.id)
                    c.settings.setThemeMode(ThemeMode.SYSTEM)
                }
                app.dudebooru.ui.face.OnboardingChoice.Look.LIGHT -> {
                    c.settings.setThemeId(ThemePresets.CLASSIC.id)
                    c.settings.setThemeMode(ThemeMode.LIGHT)
                }
                app.dudebooru.ui.face.OnboardingChoice.Look.DARK -> {
                    c.settings.setThemeId(ThemePresets.CLASSIC.id)
                    c.settings.setThemeMode(ThemeMode.DARK)
                }
            }
            c.settings.setOnboarded()
        }
    }

    fun selectTheme(id: String) {
        viewModelScope.launch { c.settings.setThemeId(id) }
    }

    fun saveCustomTheme(theme: AppTheme) {
        viewModelScope.launch {
            c.settings.setCustomTheme(theme.copy(id = "custom"))
            c.settings.setThemeId("custom")
            // Старые картинки фона больше не нужны.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ThemeFiles.cleanup(getApplication(), theme.background.imagePath)
            }
        }
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
