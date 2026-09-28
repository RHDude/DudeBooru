@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.engine.QueryPlan
import app.dudebooru.booru.model.Post
import androidx.compose.foundation.combinedClickable
import app.dudebooru.booru.net.BooruException
import app.dudebooru.ui.common.errorText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Лента постов во всю ширину (или сеткой): бесконечная прокрутка, pull-to-refresh, скелетоны,
 * разделитель «Новое с прошлого визита» и кнопка «↑ N новых», как непрочитанные в Telegram.
 */
@Composable
fun FeedList(
    controller: FeedController,
    actions: PostActions,
    modifier: Modifier = Modifier,
    showPlan: Boolean = false,
    grid: Boolean = LocalFeedPrefs.current.grid,
    visitMark: Long? = null,
    /** Долгое нажатие в сетке; по умолчанию — «скачать оригинал». */
    onLongPress: ((Post) -> Unit)? = null,
    /** Только у видимой папки: верх ленты на экране — новое просмотрено. */
    onTopSeen: ((Long) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val listState = controller.listState
    val gridState = controller.gridState
    val scope = rememberCoroutineScope()
    val showHiddenMark = LocalFeedPrefs.current.showHiddenCount

    // Следующая порция — заранее, на 70% прокрутки.
    LaunchedEffect(controller, grid) {
        snapshotFlow {
            val total: Int
            val last: Int
            if (grid) {
                total = gridState.layoutInfo.totalItemsCount
                last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            } else {
                total = listState.layoutInfo.totalItemsCount
                last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }
            total > 3 && last >= (total * 0.7f).toInt()
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) controller.loadMore() }
    }

    // Верх ленты на экране — значит, новое просмотрено.
    LaunchedEffect(controller, state.items.firstOrNull()?.key, grid, onTopSeen != null) {
        val report = onTopSeen ?: return@LaunchedEffect
        // «Увидел верх» = первая карточка ленты на экране.
        snapshotFlow {
            val first = state.items.firstOrNull()?.key
            first != null && if (grid) {
                gridState.layoutInfo.visibleItemsInfo.any { it.key == first }
            } else {
                listState.layoutInfo.visibleItemsInfo.any { it.key == first }
            }
        }
            .distinctUntilChanged()
            .collect { atTop -> if (atTop) state.items.take(3).flatMap { it.posts }.maxOfOrNull { it.id }?.let(report) }
    }

    // «Просит притормозить»: ждём сколько сказали и повторяем сами.
    val error = state.error
    if (error is BooruException.TooManyRequests) {
        LaunchedEffect(error) {
            delay(error.retryAfterSeconds * 1000)
            controller.retry()
        }
    }

    // Где кончается новое с прошлого визита.
    val dividerIndex = remember(state.items, visitMark) {
        if (visitMark == null || visitMark <= 0) -1
        else state.items.indexOfFirst { item -> item.posts.maxOf { it.id } <= visitMark }
    }
    val newCount = remember(state.items, dividerIndex) {
        if (dividerIndex <= 0) 0 else state.items.take(dividerIndex).sumOf { it.posts.size }
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = controller::refresh, modifier = modifier.fillMaxSize()) {
        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                val plan = state.plan
                if (showPlan && plan != null && plan.serverTerms.isNotEmpty()) {
                    item(key = "plan", span = { GridItemSpan(maxLineSpan) }) { PlanRow(plan) }
                }
                gridItems(state.items, key = { it.key }) { item ->
                    LaunchedEffect(item.key) { controller.ensureFamily(item) }
                    GridCell(
                        item = item,
                        onClick = { actions.open(controller, item.lead) },
                        onLongClick = { onLongPress?.invoke(item.lead) ?: actions.download(item.lead, original = true) },
                    )
                }
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { Footer(state, controller, context) }
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                if (header != null) item(key = "header") { header() }
                if (state.hiddenCount > 0 && showHiddenMark) {
                    item(key = "hidden") { HiddenMark(state.hidden, actions) }
                }
                val plan = state.plan
                if (showPlan && plan != null && plan.serverTerms.isNotEmpty()) {
                    item(key = "plan") { PlanRow(plan) }
                }
                if (state.authDropped) {
                    item(key = "auth") {
                        Text(
                            stringResource(R.string.auth_dropped, controller.site.name),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
                    LaunchedEffect(item.key) { controller.ensureFamily(item) }
                    if (index == dividerIndex && index > 0) NewDivider()
                    PostCard(item, controller, actions)
                    if (index < state.items.lastIndex && index + 1 != dividerIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                if (state.items.isEmpty() && state.loading) {
                    items(3) { SkeletonCard() }
                }
                item(key = "footer") { Footer(state, controller, context) }
            }
        }

        // «↑ 24 новых»: ушёл ниже нового — одним тапом наверх.
        val belowNew by remember(dividerIndex, grid) {
            derivedStateOf { dividerIndex > 0 && !grid && listState.firstVisibleItemIndex > dividerIndex + 1 }
        }
        AnimatedVisibility(
            visible = belowNew && newCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { scope.launch { listState.animateScrollToItem(0) } },
            ) {
                Text(
                    stringResource(R.string.feed_new_button, newCount),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Footer(state: FeedState, controller: FeedController, context: android.content.Context) {
    val error = state.error
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        when {
            state.loading && state.items.isNotEmpty() -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(context.errorText(error), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                if (error !is BooruException.TooManyRequests) {
                    OutlinedButton(onClick = controller::retry, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            state.endReached && state.items.isEmpty() -> EmptyFeed()
            state.endReached && !controller.isLocal -> Text(stringResource(R.string.feed_end), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Скрытые не мешают: маленькая отметка «скрыто 12», по тапу — что и по какому тегу. */
@Composable
private fun HiddenMark(hidden: Map<String, Int>, actions: PostActions) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
        Text(
            stringResource(R.string.hidden_mark, hidden.values.sum()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    if (open) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = false }) {
            app.dudebooru.ui.filter.HiddenSheetContent(hidden) { expression ->
                actions.restoreTag(expression)
                open = false
            }
        }
    }
}

@Composable
private fun NewDivider() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Text(
            stringResource(R.string.feed_new_divider),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    }
}

/** Клетка сетки: квадратное превью; у карусели — отметка с числом картинок. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GridCell(item: FeedItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val censor = LocalCensor.current
    Box(
        Modifier.aspectRatio(1f).background(placeholderColor(item.lead))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (censor.hides(item.lead)) {
            CensoredImage(item.lead, censor.prefs.style, censor.prefs.strength, onReveal = onClick)
            return@Box
        }
        coil3.compose.AsyncImage(
            model = coil3.request.ImageRequest.Builder(context).data(item.lead.previewUrl ?: item.lead.sampleUrl).build(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.posts.size > 1) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Text("${item.posts.size}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
    }
}

private fun LazyListScope.items(count: Int, content: @Composable () -> Unit) {
    repeat(count) { i -> item(key = "skeleton$i") { content() } }
}

/** Скелетон карточки нужной высоты — без крутилки посреди экрана. */
@Composable
private fun SkeletonCard() {
    val shade = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(shade))
            Spacer(Modifier.width(10.dp))
            Column {
                Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shade))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(80.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(shade))
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(shade))
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun EmptyFeed() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 48.dp)) {
        Text("(・・ ) ?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.feed_empty), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Что ушло на сервер (сплошная обводка), что проверяется в приложении (пунктир). */
@Composable
fun PlanRow(plan: QueryPlan) {
    val outline = MaterialTheme.colorScheme.outline
    val hidden = setOf("rating:", "order:", "age:", "date:")
    val server = plan.serverTerms.filterNot { term -> hidden.any { term.startsWith(it) || term.startsWith("-$it") } }
    if (server.isEmpty() && plan.localTerms.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            server.forEach { TermChip(it, dashed = false, color = outline) }
            plan.localTerms.forEach { TermChip(it, dashed = true, color = outline) }
        }
        if (plan.localTerms.isNotEmpty()) {
            Text(
                stringResource(R.string.plan_legend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (plan.sortDropped) {
            Text(stringResource(R.string.plan_sort_dropped), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TermChip(text: String, dashed: Boolean, color: Color) {
    val shape = RoundedCornerShape(8.dp)
    val border = if (dashed) {
        Modifier.drawBehind {
            drawRoundRect(
                color = color,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }
    } else {
        Modifier.border(BorderStroke(1.dp, color), shape)
    }
    Surface(color = Color.Transparent, shape = shape, modifier = border) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}
