@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.dudebooru.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DudeDrawer(
                vm = vm,
                dark = dark,
                onNavigate = { route ->
                    scope.launch { drawer.close() }
                    vm.navigate(route)
                },
                onCloseApp = onCloseApp,
            )
        },
    ) {
        val site = folders.firstOrNull { it.id == selectedId }
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(DudeIcons.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    // Только подпись: тап ничего не делает.
                    title = { Text(site?.name ?: stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                    actions = {
                        if (site != null) {
                            SortButton(vm.folderFeed(site.id))
                            IconButton(onClick = { vm.navigate(Route.Search(site.id)) }) {
                                Icon(DudeIcons.Search, contentDescription = stringResource(R.string.search))
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            if (folders.isEmpty() || site == null) return@Scaffold
            androidx.compose.foundation.layout.Column(Modifier.padding(padding).fillMaxSize()) {
                val counts by vm.newCounts().collectAsStateWithLifecycle()
                FolderChips(
                    folders = folders,
                    selectedId = site.id,
                    counts = counts,
                    onSelect = vm::select,
                    onMarkSeen = vm::markAllSeen,
                    onHide = vm::hideFolder,
                    onMove = vm::moveFolder,
                )
                FolderPager(vm, folders, site, actions)
            }
        }
    }
}

/** Источники параллельны: у каждого своя лента; переключение свайпом, как папки в Telegram. */
@Composable
private fun FolderPager(vm: MainViewModel, folders: List<SiteConfig>, site: SiteConfig, actions: PostActions) {
    val index = folders.indexOfFirst { it.id == site.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = index) { folders.size }

    // Тап по папке → листаем пейджер; свайп пейджера → выбираем папку.
    LaunchedEffect(index) { if (pager.currentPage != index) pager.animateScrollToPage(index) }
    LaunchedEffect(pager, folders) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            folders.getOrNull(page)?.let { if (it.id != vm.selected.value) vm.select(it.id) }
        }
    }

    HorizontalPager(state = pager, key = { folders[it].id }, beyondViewportPageCount = 0) { page ->
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

@Composable
private fun FolderChips(
    folders: List<SiteConfig>,
    selectedId: String,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
    onMarkSeen: (String) -> Unit,
    onHide: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val index = folders.indexOfFirst { it.id == selectedId }
    LaunchedEffect(index) { if (index >= 0) listState.animateScrollToItem(index) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(folders, key = { it.id }) { folder ->
            var menu by remember { mutableStateOf(false) }
            val selected = folder.id == selectedId
            Box {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.combinedClickable(onClick = { onSelect(folder.id) }, onLongClick = { menu = true }),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            folder.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        val count = counts[folder.id] ?: 0
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            CountBadge(if (count >= 99) "99+" else count.toString(), highlighted = selected)
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.folder_mark_seen)) }, onClick = { menu = false; onMarkSeen(folder.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.folder_move_left)) }, onClick = { menu = false; onMove(folder.id, -1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.folder_move_right)) }, onClick = { menu = false; onMove(folder.id, 1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.folder_hide)) }, onClick = { menu = false; onHide(folder.id) })
                }
            }
        }
    }
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
