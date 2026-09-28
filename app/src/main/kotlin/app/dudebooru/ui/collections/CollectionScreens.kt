@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package app.dudebooru.ui.collections

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.data.db.DownloadEntity
import app.dudebooru.data.db.DownloadStatus
import app.dudebooru.data.db.SavedFolderEntity
import app.dudebooru.data.db.SavedFolderPostEntity
import app.dudebooru.ui.feed.FeedList
import app.dudebooru.ui.feed.PostActions
import app.dudebooru.ui.feed.avatarColor
import app.dudebooru.ui.feed.formatSize
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.main.Avatar
import app.dudebooru.ui.main.CountBadge
import app.dudebooru.ui.main.MainViewModel
import app.dudebooru.ui.theme.LocalTagColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Сохранённые

/** «Сохранённые»: сетка по умолчанию, папки как favorite groups, пост может лежать в нескольких. */
@Composable
fun SavedScreen(vm: MainViewModel, actions: PostActions, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = vm.c.db.saved()
    val folders by dao.folders().collectAsStateWithLifecycle(emptyList())
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val controller = remember(folderId) {
        val id = folderId
        vm.localFeed(
            if (id == null) "saved:all" else "saved:$id",
            vm.decodePosts(if (id == null) dao.savedPosts() else dao.folderPosts(id)),
        )
    }
    val state by controller.state.collectAsStateWithLifecycle()
    var sheetPost by remember { mutableStateOf<Post?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<List<Post>?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_saved)) },
                actions = {
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { bulk = controller.posts }) { Icon(DudeIcons.Download, stringResource(R.string.saved_download_all)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = folderId == null, onClick = { folderId = null }, label = { Text(stringResource(R.string.saved_all)) }) }
                items(folders, key = { it.id }) { folder ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = folderId == folder.id,
                            onClick = { folderId = folder.id },
                            label = { Text(folder.name) },
                            modifier = Modifier.combinedClickable(onClick = { folderId = folder.id }, onLongClick = { menu = true }),
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.saved_delete_folder)) }, onClick = {
                                menu = false
                                if (folderId == folder.id) folderId = null
                                scope.launch { dao.deleteFolder(folder.id) }
                            })
                        }
                    }
                }
                item { FilterChip(selected = false, onClick = { newFolder = true }, label = { Text("+ " + stringResource(R.string.saved_new_folder)) }) }
            }
            if (state.items.isEmpty()) {
                EmptyState("(￣～￣;)", stringResource(R.string.saved_empty), stringResource(R.string.saved_empty_hint))
            } else {
                FeedList(controller, actions, grid = true, onLongPress = { sheetPost = it })
            }
        }
    }

    sheetPost?.let { post ->
        FolderSheet(vm, post, folders, onDismiss = { sheetPost = null }, onUnsave = {
            actions.toggleSave(post)
            sheetPost = null
        })
    }
    if (newFolder) {
        NameDialog(stringResource(R.string.saved_new_folder), onDismiss = { newFolder = false }) { name ->
            newFolder = false
            scope.launch { dao.createFolder(SavedFolderEntity(name = name, createdAt = System.currentTimeMillis())) }
        }
    }
    bulk?.let { posts -> BulkDownloadDialog(posts, onDismiss = { bulk = null }) { bulk = null; actions.downloadAll(posts) } }
}

@Composable
private fun FolderSheet(vm: MainViewModel, post: Post, folders: List<SavedFolderEntity>, onDismiss: () -> Unit, onUnsave: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = vm.c.db.saved()
    var member by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(post.key) { member = dao.foldersOf(post.site, post.id).toSet() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.saved_folders_title), style = MaterialTheme.typography.titleMedium)
            if (folders.isEmpty()) {
                Text(stringResource(R.string.saved_no_folders), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            folders.forEach { folder ->
                val checked = folder.id in member
                Row(
                    Modifier.fillMaxWidth().clickable {
                        member = if (checked) member - folder.id else member + folder.id
                        scope.launch {
                            if (checked) dao.removeFromFolder(folder.id, post.site, post.id)
                            else dao.addToFolder(SavedFolderPostEntity(folder.id, post.site, post.id))
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(folder.name)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onUnsave) { Text(stringResource(R.string.menu_unsave)) }
        }
    }
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onDone(name.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adult_no)) } },
    )
}

/** Перед массовой загрузкой видно число файлов, общий вес и свободное место. */
@Composable
fun BulkDownloadDialog(posts: List<Post>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val total = posts.sumOf { it.fileSize ?: 0L }
    val unknown = posts.count { it.fileSize == null }
    val free = remember { runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bulk_title, posts.size)) },
        text = {
            Column {
                Text(stringResource(R.string.bulk_size, formatSize(total) ?: "—") + if (unknown > 0) " " + stringResource(R.string.bulk_unknown, unknown) else "")
                free?.let { Text(stringResource(R.string.bulk_free, formatSize(it) ?: "—")) }
                if (free != null && total > free) Text(stringResource(R.string.bulk_no_space), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.menu_download_original)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adult_no)) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Профиль

/** Мой профиль: аватарка, ник, статистика; вкладки «Лайки», «История», «Загрузки»; «Мои теги». */
@Composable
fun ProfileScreen(vm: MainViewModel, actions: PostActions, onBack: () -> Unit, onEdit: () -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val likes by vm.likeCount.collectAsStateWithLifecycle()
    val saved by vm.savedCount.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val likedFeed = remember { vm.localFeed("likes", vm.decodePosts(vm.c.db.saved().likedPosts())) }
    val historyFeed = remember { vm.localFeed("history", vm.decodePosts(vm.c.db.history().recentPosts())) }
    val liked by likedFeed.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_profile)) },
                actions = { IconButton(onClick = onEdit) { Icon(DudeIcons.Gear, stringResource(R.string.drawer_settings)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.avatarUrl, Modifier.size(72.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("@" + profile?.nick.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                Stat(likes, R.string.profile_likes)
                Stat(saved, R.string.profile_saved)
                Stat(downloads.count { it.status == DownloadStatus.DONE }, R.string.profile_downloaded)
            }
            // «Мои теги»: что приложение считает твоим вкусом; долгое нажатие — убрать лишнее.
            val scope = rememberCoroutineScope()
            var tasteVersion by remember { mutableIntStateOf(0) }
            val myTags by produceState(emptyList<app.dudebooru.booru.rec.TasteTag>(), liked.items.size, tasteVersion) {
                value = runCatching { vm.c.recs.profile().top(limit = 20) }.getOrDefault(emptyList())
            }
            if (myTags.isNotEmpty()) {
                val colors = LocalTagColors.current
                Text(stringResource(R.string.profile_my_tags), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
                Text(stringResource(R.string.profile_my_tags_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    myTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.of(tag.category).copy(alpha = 0.12f),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = {}, onLongClick = {
                                scope.launch {
                                    vm.c.recs.mute(tag.name)
                                    tasteVersion++
                                }
                            }),
                        ) {
                            Text(tag.name.replace('_', ' '), color = colors.of(tag.category), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                listOf(R.string.profile_likes, R.string.drawer_history, R.string.drawer_downloads).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> if (liked.items.isEmpty()) {
                    EmptyState("(´･ω･`)", stringResource(R.string.likes_empty), null)
                } else {
                    FeedList(likedFeed, actions, grid = true)
                }
                1 -> HistoryPosts(historyFeed, actions)
                else -> DownloadsList(vm, downloads)
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// История

/** «История»: просмотренные посты и поиски. */
@Composable
fun HistoryScreen(vm: MainViewModel, actions: PostActions, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val feed = remember { vm.localFeed("history", vm.decodePosts(vm.c.db.history().recentPosts())) }
    val searches by vm.c.db.searchHistory().recentAll().collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_history)) },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            if (tab == 0) vm.c.db.history().clear() else vm.c.db.searchHistory().clear()
                        }
                    }) { Text(stringResource(R.string.history_clear)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_posts)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_searches)) })
            }
            if (tab == 0) {
                HistoryPosts(feed, actions)
            } else if (searches.isEmpty()) {
                EmptyState("( ˘ω˘ )", stringResource(R.string.history_empty), null)
            } else {
                LazyColumn {
                    items(searches, key = { it.site + ":" + it.query }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.openSearchResults(entry.site, entry.query.split(' ')) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(DudeIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(entry.query, modifier = Modifier.weight(1f))
                            Text(vm.c.registry.site(entry.site)?.name ?: entry.site, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPosts(feed: app.dudebooru.ui.feed.FeedController, actions: PostActions) {
    val state by feed.state.collectAsStateWithLifecycle()
    if (state.items.isEmpty()) {
        EmptyState("( ˘ω˘ )", stringResource(R.string.history_empty), null)
    } else {
        FeedList(feed, actions, grid = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Художники

/** Подписки: новые работы — счётчиком, как непрочитанные каналы. */
@Composable
fun ArtistsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val subs by vm.c.subscriptions.all.collectAsStateWithLifecycle(emptyList())
    val mode by vm.mode.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_artists)) },
            )
        },
    ) { padding ->
        if (subs.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState("(￣ー￣)", stringResource(R.string.artists_empty), stringResource(R.string.artists_empty_hint)) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(subs, key = { it.site + ":" + it.artist }) { sub ->
                val site = vm.c.registry.site(sub.site) ?: return@items
                val avatar by produceState<String?>(vm.c.avatars.cached(site, sub.artist, mode), sub.site, sub.artist, mode) {
                    if (value == null) value = vm.c.avatars.url(site, sub.artist, mode)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openArtist(sub.site, sub.artist) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).let { m -> m }, contentAlignment = Alignment.Center) {
                        Surface(color = avatarColor(sub.artist), modifier = Modifier.matchParentSize()) {}
                        Text(sub.artist.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        if (avatar != null) AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sub.artist, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(site.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sub.newCount > 0) CountBadge(if (sub.newCount >= 20) "20+" else sub.newCount.toString(), highlighted = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Загрузки

/** «Загрузки»: идут, ошибки, готово; тап по готовому открывает файл. */
@Composable
fun DownloadsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_downloads)) },
                actions = {
                    val active = downloads.any { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
                    val paused = downloads.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
                    if (active) TextButton(onClick = { scope.launch { vm.c.downloads.pauseAll() } }) { Text(stringResource(R.string.dl_pause_all)) }
                    else if (paused) TextButton(onClick = { scope.launch { vm.c.downloads.resumeAll() } }) { Text(stringResource(R.string.dl_resume_all)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { DownloadsList(vm, downloads) }
    }
}

@Composable
private fun DownloadsList(vm: MainViewModel, downloads: List<DownloadEntity>) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    if (downloads.isEmpty()) {
        EmptyState("(・ω・)ノ", stringResource(R.string.downloads_empty), null)
        return
    }
    val running = downloads.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED }
    val done = downloads.filter { it.status == DownloadStatus.DONE }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        fun section(title: Int, list: List<DownloadEntity>, trailing: (@Composable () -> Unit)? = null) {
            if (list.isEmpty()) return
            item(key = "h$title") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title) + " · " + list.size, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            items(list, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = entry.status == DownloadStatus.DONE && entry.uri != null) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    .setDataAndType(android.net.Uri.parse(entry.uri), entry.mime)
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        }
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.relativePath.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        val sub = when (entry.status) {
                            DownloadStatus.RUNNING -> listOfNotNull(formatSize(entry.bytes), formatSize(entry.total)).joinToString(" / ")
                            DownloadStatus.QUEUED -> stringResource(R.string.dl_queued)
                            DownloadStatus.PAUSED -> stringResource(R.string.dl_paused)
                            DownloadStatus.FAILED -> entry.error ?: stringResource(R.string.dl_failed)
                            DownloadStatus.DONE -> entry.relativePath.substringBeforeLast('/')
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = if (entry.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entry.status == DownloadStatus.RUNNING) {
                            val progress = if (entry.total > 0) entry.bytes.toFloat() / entry.total else null
                            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    when (entry.status) {
                        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> TextButton(onClick = { scope.launch { vm.c.downloads.pause(entry.id) } }) { Text(stringResource(R.string.dl_pause)) }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> TextButton(onClick = { scope.launch { vm.c.downloads.resume(entry.id) } }) { Text(stringResource(R.string.retry)) }
                        DownloadStatus.DONE -> Unit
                    }
                    if (entry.status != DownloadStatus.DONE) {
                        IconButton(onClick = { scope.launch { vm.c.downloads.cancel(entry.id) } }) { Icon(DudeIcons.Close, stringResource(R.string.dl_cancel)) }
                    }
                }
            }
        }
        section(R.string.dl_section_running, running)
        section(R.string.dl_section_failed, failed)
        section(R.string.dl_section_done, done) {
            TextButton(onClick = { scope.launch { vm.c.downloads.clearDone() } }) { Text(stringResource(R.string.dl_clear_done)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------

/** Пустой экран: крупный каомодзи по центру и человеческий текст. */
@Composable
fun EmptyState(kaomoji: String, text: String, hint: String?) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(kaomoji, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
