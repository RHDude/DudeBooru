@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.dudebooru.ui.common.label
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.ArtistInfo
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.ui.feed.ArtistAvatar
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.feed.FeedList
import app.dudebooru.ui.feed.PostActions
import app.dudebooru.ui.icons.DudeIcons

/**
 * Шапка одного цвета с экраном и при прокрутке: по умолчанию Material 3 подкрашивает её
 * в тон поверхности, и над лентой она заметно светлеет.
 */
@Composable
fun steadyBarColors(color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background) =
    TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = color, scrolledContainerColor = color)

/** Результаты поиска — лентой поверх текущей, с той же кнопкой сортировки. */
@Composable
fun ResultsScreen(controller: FeedController, actions: PostActions, onBack: () -> Unit, onEditQuery: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = {
                    Text(
                        controller.tags.joinToString(" "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onEditQuery),
                    )
                },
                actions = {
                    SortButton(controller)
                    IconButton(onClick = onEditQuery) { Icon(DudeIcons.Search, stringResource(R.string.search)) }
                },
                scrollBehavior = scrollBehavior,
                colors = steadyBarColors(),
            )
        },
    ) { padding ->
        FeedList(controller, actions, modifier = Modifier.padding(padding), showPlan = true)
    }
}

/** Художник: имя, другие ники, ссылки; ниже его работы в текущем источнике, «новые / лучшие». */
@Composable
fun ArtistScreen(vm: MainViewModel, controller: FeedController, name: String, actions: PostActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val sort by controller.sort.collectAsStateWithLifecycle()
    val feed by controller.state.collectAsStateWithLifecycle()
    val info by produceState<ArtistInfo?>(null, controller.site.id, name) {
        value = runCatching { vm.c.registry.engine(controller.site).artist(name, vm.c.accounts.session(controller.site)) }.getOrNull()
    }
    val scope = rememberCoroutineScope()
    val subscription by remember(controller.site.id, name) { vm.c.subscriptions.observe(controller.site, name) }.collectAsStateWithLifecycle(null)
    val subscribed = subscription != null
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    val newest = feed.items.maxOfOrNull { item -> item.posts.maxOf { it.id } } ?: 0L
    // Страница открыта — новые работы просмотрены.
    LaunchedEffect(subscribed, newest, sort) {
        if (subscribed && sort == SortOrder.NEW && newest > 0) vm.c.subscriptions.markSeen(controller.site, name, newest)
    }
    var bulk by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(controller.site.name) },
                actions = {
                    if (feed.items.isNotEmpty()) {
                        IconButton(onClick = { bulk = true }) { Icon(DudeIcons.Download, stringResource(R.string.artist_download_all)) }
                    }
                },
            )
        },
    ) { padding ->
        if (bulk) {
            app.dudebooru.ui.collections.BulkDownloadDialog(controller.posts, onDismiss = { bulk = false }) {
                bulk = false
                actions.downloadAll(controller.posts)
            }
        }
        // На странице художника сетка по умолчанию.
        val mode by vm.mode.collectAsStateWithLifecycle()
        FeedList(
            controller,
            actions,
            modifier = Modifier.padding(padding),
            grid = true,
            emptyContent = {
                app.dudebooru.ui.face.EmptyState(
                    app.dudebooru.ui.face.rememberKaomoji(listOf("(・_・;)") + app.dudebooru.ui.face.Kaomoji.CONFUSED),
                    stringResource(R.string.artist_empty_mode, stringResource(mode.label())),
                )
            },
        ) {
            run {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val lead = feed.items.firstOrNull()?.lead
                        if (lead != null) ArtistAvatar(lead, size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info?.name ?: name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            val others = info?.otherNames.orEmpty()
                            if (others.isNotEmpty()) {
                                Text(others.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val urls = info?.urls.orEmpty()
                    if (urls.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            urls.take(8).forEach { url ->
                                Text(
                                    Uri.parse(url).host?.removePrefix("www.") ?: url,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Подписка, как в Twitter: «Подписаться» / «Подписан», рядом колокольчик —
                    // уведомления о новых работах можно выключить у одного художника, не отписываясь.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sub = subscription
                        if (sub != null) {
                            androidx.compose.material3.OutlinedIconButton(onClick = {
                                val enable = !sub.notify
                                if (enable) actions.askNotifications()
                                scope.launch { vm.c.subscriptions.setNotify(controller.site, name, enable) }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(if (enable) R.string.artist_notify_enabled else R.string.artist_notify_disabled, name),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }) {
                                Icon(
                                    if (sub.notify) DudeIcons.BellRing else DudeIcons.BellOff,
                                    stringResource(if (sub.notify) R.string.artist_notify_on else R.string.artist_notify_off),
                                    tint = if (sub.notify) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.FilledTonalButton(onClick = { confirmUnsubscribe = true }, modifier = Modifier.weight(1f)) {
                                Icon(DudeIcons.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.artist_subscribed))
                            }
                        } else {
                            androidx.compose.material3.Button(
                                onClick = {
                                    actions.askNotifications()
                                    scope.launch { vm.c.subscriptions.subscribe(controller.site, name, newest) }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.artist_subscribe)) }
                        }
                    }
                    if (confirmUnsubscribe) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { confirmUnsubscribe = false },
                            title = { Text(stringResource(R.string.artist_unsubscribe_title, name)) },
                            text = { Text(stringResource(R.string.artist_unsubscribe_text)) },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    confirmUnsubscribe = false
                                    scope.launch { vm.c.subscriptions.unsubscribe(controller.site, name) }
                                }) { Text(stringResource(R.string.artist_unsubscribe)) }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { confirmUnsubscribe = false }) { Text(stringResource(R.string.cancel)) }
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val options = listOf(SortOrder.NEW to R.string.artist_new, SortOrder.BEST to R.string.artist_best)
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            options.forEachIndexed { i, (order, label) ->
                                SegmentedButton(
                                    selected = sort == order,
                                    onClick = { controller.setSort(order) },
                                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                ) { Text(stringResource(label)) }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { feed.items.firstOrNull()?.lead?.let { actions.hideTag(it, name) } }) {
                            Text(stringResource(R.string.artist_hide))
                        }
                    }
                }
            }
        }
    }
}

/** Пункты меню, которые появятся на следующих шагах сборки. */
@Composable
fun SoonScreen(title: Int, step: Int, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(title)) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("( ˘ω˘ )", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.soon_text, stringResource(title), step, stringResource(stepName(step))),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun stepName(step: Int) = when (step) {
    3 -> R.string.step_filters
    4 -> R.string.step_collections
    5 -> R.string.step_recommendations
    6 -> R.string.step_face
    else -> R.string.step_release
}
