@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.dudebooru.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.ui.feed.FeedList
import app.dudebooru.ui.feed.PostActions
import app.dudebooru.ui.icons.DudeIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Главный экран по макету: ☰, название источника по центру, сортировка и поиск справа;
 * под шапкой папки-источники; ниже лента постов во всю ширину.
 */
@Composable
fun MainShell(vm: MainViewModel, actions: PostActions, dark: Boolean, onCloseApp: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val selectedId by vm.selected.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // «Назад» сначала закрывает меню.
    androidx.activity.compose.BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    // Меню открыли — заранее готовим «Случайный пост».
    LaunchedEffect(drawer.targetValue) { if (drawer.targetValue == DrawerValue.Open) vm.warmRandom() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DudeDrawer(
                vm = vm,
                dark = dark,
                // Меню не закрываем анимацией: экран с ним и так уходит под новый, а два движения сразу
                // (меню уезжает, экраны меняются) выглядели как рывок. Вернёмся — меню уже закрыто.
                onNavigate = { route -> vm.navigate(route) },
                onCloseApp = onCloseApp,
            )
        },
    ) {
        val site = folders.firstOrNull { it.id == selectedId }
        // Папки ещё читаются из настроек — доли секунды при запуске.
        if (site == null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        } else {
            MainFolders(vm, folders, site, actions, onOpenDrawer = { scope.launch { drawer.open() } })
        }
    }
}

@Composable
private fun MainFolders(vm: MainViewModel, folders: List<SiteConfig>, site: SiteConfig, actions: PostActions, onOpenDrawer: () -> Unit) {
    // Пейджер создаётся, когда папки уже известны: сразу на выбранной, без пролистывания при запуске.
    val pager = rememberPagerState(initialPage = folders.indexOfFirst { it.id == site.id }.coerceAtLeast(0)) { folders.size }
    // Шапка, папка и сортировка меняются вместе со свайпом, а не когда пейджер остановится.
    val shown = folders.getOrNull(pager.currentPage) ?: site
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // Фон ленты из темы: цвет, градиент или картинка под прозрачной лентой.
    val backdrop = app.dudebooru.ui.theme.LocalAppTheme.current.background
    val withBackdrop = backdrop.kind != app.dudebooru.ui.theme.ThemeBackground.Kind.NONE
    if (withBackdrop) app.dudebooru.ui.theme.ThemeBackdrop(backdrop, Modifier.fillMaxSize())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (withBackdrop) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(DudeIcons.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                // Только подпись: тап ничего не делает. На полпути свайпа гаснет и сменяется следующей.
                title = {
                    Text(
                        shown.name,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - 2f * kotlin.math.abs(pager.currentPageOffsetFraction).coerceIn(0f, 0.5f)
                        },
                    )
                },
                actions = {
                    SortButton(vm.folderFeed(shown.id))
                    IconButton(onClick = { vm.navigate(Route.Search(shown.id)) }) {
                        Icon(DudeIcons.Search, contentDescription = stringResource(R.string.search))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = if (withBackdrop) {
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    )
                } else {
                    steadyBarColors()
                },
            )
        },
    ) { padding ->
        // Свайп вправо, который уже некуда отдать ни ленте, ни ряду папок, открывает меню.
        androidx.compose.foundation.layout.Column(Modifier.padding(padding).fillMaxSize().nestedScroll(rememberDrawerPull(onOpenDrawer))) {
            val counts by vm.newCounts().collectAsStateWithLifecycle()
            val folderPrefs by vm.folderPrefs.collectAsStateWithLifecycle()
            FolderTabs(
                folders = folders,
                pager = pager,
                counts = if (folderPrefs.newCounts) counts else emptyMap(),
                onSelect = { id ->
                    if (id == shown.id) {
                        // Уже открытая папка: как в Telegram — лента наверх, шапка возвращается.
                        scrollBehavior.state.heightOffset = 0f
                        vm.folderFeed(id).requestScrollTop()
                    } else {
                        vm.select(id)
                    }
                },
                onMarkSeen = vm::markAllSeen,
                onHide = vm::hideFolder,
                onMove = vm::moveFolder,
                stateOf = { vm.folderFeed(it).state },
            )
            Box(Modifier.fillMaxSize()) {
                FolderPager(vm, folders, site, actions, pager, swipe = folderPrefs.swipe)
                // На остальных папках свайп вправо листает к предыдущей — меню открывается от края.
                EdgeSwipe(onOpen = onOpenDrawer, modifier = Modifier.align(Alignment.CenterStart))
            }
        }
    }
}

/** Источники параллельны: у каждого своя лента; переключение свайпом, как папки в Telegram. */
@Composable
private fun FolderPager(
    vm: MainViewModel,
    folders: List<SiteConfig>,
    site: SiteConfig,
    actions: PostActions,
    pager: androidx.compose.foundation.pager.PagerState,
    swipe: Boolean,
) {
    val index = folders.indexOfFirst { it.id == site.id }.coerceAtLeast(0)

    // Тап по папке → листаем пейджер; свайп пейджера → выбираем папку.
    LaunchedEffect(index) { if (pager.currentPage != index) pager.animateScrollToPage(index) }
    LaunchedEffect(pager, folders) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            folders.getOrNull(page)?.let { if (it.id != vm.selected.value) vm.select(it.id) }
        }
    }

    HorizontalPager(state = pager, key = { folders[it].id }, beyondViewportPageCount = 0, userScrollEnabled = swipe) { page ->
        val folder = folders[page]
        val controller = vm.folderFeed(folder.id)
        val marks by vm.visitMarks.collectAsStateWithLifecycle()
        FeedList(
            controller = controller,
            actions = actions,
            visitMark = marks[folder.id],
            onTopSeen = if (folder.id == site.id) ({ topId -> vm.markSeen(folder.id, topId) }) else null,
        )
    }
}

/**
 * Папки-источники во всю ширину: если помещаются, делят свободное место поровну; если нет — листаются.
 * Подсветка едет за пальцем вместе с пейджером, а не прыгает после свайпа.
 */
@Composable
private fun FolderTabs(
    folders: List<SiteConfig>,
    pager: androidx.compose.foundation.pager.PagerState,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
    onMarkSeen: (String) -> Unit,
    onHide: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    stateOf: (String) -> kotlinx.coroutines.flow.StateFlow<app.dudebooru.ui.feed.FeedState>,
) {
    val scroll = rememberScrollState()
    val bounds = remember { TabBounds() }
    val indicator = MaterialTheme.colorScheme.secondaryContainer
    val density = LocalDensity.current
    val edge = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }

    // Выбранная папка всегда на виду, если все не помещаются.
    LaunchedEffect(pager.currentPage, bounds.version) {
        val i = pager.currentPage
        if (i < bounds.lefts.size && scroll.maxValue > 0) {
            val center = bounds.lefts[i] + bounds.widths[i] / 2 - bounds.viewport / 2
            scroll.animateScrollTo(center.toInt().coerceIn(0, scroll.maxValue))
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = constraints.maxWidth
        Layout(
            content = {
                folders.forEachIndexed { i, folder ->
                    androidx.compose.runtime.key(folder.id) {
                        FolderTab(
                            folder = folder,
                            selected = pager.currentPage == i,
                            count = counts[folder.id] ?: 0,
                            state = stateOf(folder.id),
                            onSelect = { onSelect(folder.id) },
                            onMarkSeen = { onMarkSeen(folder.id) },
                            onHide = { onHide(folder.id) },
                            onMove = { delta -> onMove(folder.id, delta) },
                        )
                    }
                }
            },
            modifier = Modifier
                // Всё помещается — ряд не прокручивается: ни свечения у края, ни перехвата свайпа у меню.
                .horizontalScroll(scroll, enabled = scroll.maxValue > 0)
                .drawBehind {
                    val n = bounds.lefts.size
                    if (n == 0) return@drawBehind
                    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (n - 1).toFloat())
                    val i = position.toInt().coerceAtMost(n - 1)
                    val j = (i + 1).coerceAtMost(n - 1)
                    val f = position - i
                    val left = bounds.lefts[i] + (bounds.lefts[j] - bounds.lefts[i]) * f
                    val width = bounds.widths[i] + (bounds.widths[j] - bounds.widths[i]) * f
                    val h = size.height - 8.dp.toPx()
                    drawRoundRect(
                        color = indicator,
                        topLeft = androidx.compose.ui.geometry.Offset(left, 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(width, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
                    )
                },
        ) { measurables, constraints ->
            val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val used = natural.sum() + gap * (natural.size - 1).coerceAtLeast(0) + edge * 2
            val extra = if (used < viewport && natural.isNotEmpty()) (viewport - used) / natural.size else 0
            val placeables = measurables.mapIndexed { i, m ->
                val w = natural[i] + extra
                m.measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = constraints.maxHeight))
            }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            var x = edge
            val lefts = FloatArray(placeables.size)
            val widths = FloatArray(placeables.size)
            placeables.forEachIndexed { i, p ->
                lefts[i] = x.toFloat()
                widths[i] = p.width.toFloat()
                x += p.width + gap
            }
            val total = maxOf(viewport, x - gap + edge)
            bounds.update(lefts, widths, viewport.toFloat())
            layout(total, height + with(density) { 8.dp.roundToPx() }) {
                placeables.forEachIndexed { i, p -> p.placeRelative(lefts[i].toInt(), with(density) { 4.dp.roundToPx() }) }
            }
        }
    }
}

/** Где стоят вкладки: пишется при раскладке, читается при рисовании подсветки. */
private class TabBounds {
    var lefts = FloatArray(0)
        private set
    var widths = FloatArray(0)
        private set
    var viewport = 0f
        private set
    var version by mutableStateOf(0)
        private set

    fun update(lefts: FloatArray, widths: FloatArray, viewport: Float) {
        val changed = !lefts.contentEquals(this.lefts) || !widths.contentEquals(this.widths)
        this.lefts = lefts
        this.widths = widths
        this.viewport = viewport
        if (changed) androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { version++ }
    }
}

@Composable
private fun FolderTab(
    folder: SiteConfig,
    selected: Boolean,
    count: Int,
    state: kotlinx.coroutines.flow.StateFlow<app.dudebooru.ui.feed.FeedState>,
    onSelect: () -> Unit,
    onMarkSeen: () -> Unit,
    onHide: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .combinedClickable(onClick = onSelect, onLongClick = { menu = true })
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                folder.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            // Ошибка касается только своей папки: на ней маленькая красная точка.
            val folderState by state.collectAsStateWithLifecycle()
            if (folderState.error != null) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.error))
            }
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                CountBadge(if (count >= 99) "99+" else count.toString(), highlighted = selected)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.folder_mark_seen)) }, onClick = { menu = false; onMarkSeen() })
            DropdownMenuItem(text = { Text(stringResource(R.string.folder_move_left)) }, onClick = { menu = false; onMove(-1) })
            DropdownMenuItem(text = { Text(stringResource(R.string.folder_move_right)) }, onClick = { menu = false; onMove(1) })
            DropdownMenuItem(text = { Text(stringResource(R.string.folder_hide)) }, onClick = { menu = false; onHide() })
        }
    }
}

/**
 * Свайп вправо, который лента уже не может забрать (первая папка, первая картинка карусели), открывает меню —
 * из любого места экрана, как в Telegram. Остаток жеста забираем, чтобы лента не тянулась растяжением.
 */
@Composable
private fun rememberDrawerPull(onOpen: () -> Unit): NestedScrollConnection {
    val density = LocalDensity.current
    val open by rememberUpdatedState(onOpen)
    return remember(density) {
        val distance = with(density) { 56.dp.toPx() }
        val flingDistance = with(density) { 16.dp.toPx() }
        val flingVelocity = with(density) { 700.dp.toPx() }
        object : NestedScrollConnection {
            var pulled = 0f
            var opened = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x <= 0f) return Offset.Zero
                pulled += available.x
                if (!opened && pulled > distance) {
                    opened = true
                    open()
                }
                return Offset(available.x, 0f)
            }

            // Короткий быстрый рывок тоже открывает: как у самого меню.
            override suspend fun onPreFling(available: Velocity): Velocity {
                val pulling = pulled > 0f
                if (!opened && pulled > flingDistance && available.x > flingVelocity) open()
                pulled = 0f
                opened = false
                // Жест ушёл в меню — скорость забираем, иначе лента дорисует у левого края
                // свечение или растяжение (на части прошивок — белую полосу на секунду).
                return if (pulling && available.x > 0f) Velocity(available.x, 0f) else Velocity.Zero
            }
        }
    }
}

@Composable
private fun EdgeSwipe(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    Box(
        modifier
            .fillMaxHeight()
            .width(20.dp)
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragged += amount
                        change.consume()
                        if (dragged > threshold) {
                            dragged = Float.NEGATIVE_INFINITY
                            onOpen()
                        }
                    },
                )
            },
    )
}

@Composable
fun CountBadge(text: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
