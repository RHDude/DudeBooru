@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.MediaType
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.ui.common.postDate
import app.dudebooru.ui.feed.CensoredImage
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.feed.LocalCensor
import app.dudebooru.ui.feed.LikeRed
import app.dudebooru.ui.feed.LocalCollections
import app.dudebooru.ui.feed.PostActions
import app.dudebooru.ui.feed.PostMenuButton
import app.dudebooru.ui.feed.formatSize
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.theme.LocalTagColors
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.abs
import kotlin.math.roundToInt

/** Где в ленте картинка: номер карточки и номер внутри карусели. */
private data class Slot(val post: Post, val card: Int, val inCard: Int, val cardSize: Int)

/**
 * Просмотр: чёрный фон, сверху «3 из 120 · 2/4» и ⋮, снизу те же кнопки, что в карточке.
 * Свайп влево/вправо — картинки подряд (вся карусель, потом следующий пост), вниз — закрыть,
 * вверх — детали, тап — спрятать панели.
 */
@Composable
fun ViewerScreen(
    controller: FeedController,
    startKey: String,
    actions: PostActions,
    onClose: () -> Unit,
    onSearchTag: (Post, String, Boolean) -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val slots = remember(state.items) {
        state.items.flatMapIndexed { card, item -> item.posts.mapIndexed { i, post -> Slot(post, card, i, item.posts.size) } }
    }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (slots.isEmpty()) {
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            return@Box
        }
        val start = remember { slots.indexOfFirst { it.post.key == startKey }.coerceAtLeast(0) }
        val pager = rememberPagerState(initialPage = start) { slots.size }
        var barsVisible by remember { mutableStateOf(true) }
        var detailsFor by remember { mutableStateOf<Post?>(null) }
        var dragY by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val closeThreshold = with(density) { 140.dp.toPx() }
        val detailsThreshold = with(density) { 90.dp.toPx() }

        // Лента догружается сама.
        LaunchedEffect(pager.currentPage, slots.size) {
            if (pager.currentPage >= slots.size - 3) controller.loadMore()
        }

        val current = slots.getOrNull(pager.currentPage) ?: slots.last()

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            dragY += amount
                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                dragY > closeThreshold -> onClose()
                                dragY < -detailsThreshold -> detailsFor = slots.getOrNull(pager.currentPage)?.post
                            }
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    )
                }
                .offset { IntOffset(0, dragY.coerceAtLeast(0f).roundToInt()) }
                .graphicsLayer { alpha = 1f - (abs(dragY) / (closeThreshold * 3)).coerceIn(0f, 0.5f) },
        ) {
            HorizontalPager(state = pager, key = { slots[it].post.key }, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                val post = slots[page].post
                val censor = LocalCensor.current
                when {
                    censor.hides(post, inViewer = true) -> CensoredImage(
                        post = post,
                        style = censor.prefs.style,
                        strength = censor.prefs.strength,
                        onReveal = { actions.reveal(post) },
                    )
                    post.mediaType == MediaType.VIDEO -> VideoPage(post, active = page == pager.currentPage, onTap = { barsVisible = !barsVisible })
                    else -> ZoomPage(post, onTap = { barsVisible = !barsVisible })
                }
            }
        }

        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(DudeIcons.Back, stringResource(R.string.back), tint = Color.White) }
                val position = stringResource(R.string.viewer_position, current.card + 1, state.items.size) +
                    if (current.cardSize > 1) " · ${current.inCard + 1}/${current.cardSize}" else ""
                Text(position, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val group = state.items.getOrNull(current.card)?.posts.orEmpty()
                PostMenuButton(current.post, group, actions, tint = Color.White)
            }
        }

        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val collections = LocalCollections.current
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).navigationBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val liked = current.post.key in collections.liked
                IconButton(onClick = { actions.toggleLike(current.post) }) {
                    Icon(if (liked) DudeIcons.HeartFilled else DudeIcons.Heart, stringResource(R.string.action_like), tint = if (liked) LikeRed else Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { detailsFor = current.post }) { Icon(DudeIcons.ChevronUp, stringResource(R.string.viewer_details), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { actions.share(current.post) }) { Icon(DudeIcons.Share, stringResource(R.string.action_share), tint = Color.White) }
                val saved = current.post.key in collections.saved
                IconButton(onClick = { actions.toggleSave(current.post) }) {
                    Icon(if (saved) DudeIcons.SaveFilled else DudeIcons.Save, stringResource(R.string.action_save), tint = Color.White)
                }
            }
        }

        detailsFor?.let { post ->
            val family = state.items.firstOrNull { item -> item.posts.any { it.key == post.key } }?.posts.orEmpty()
            DetailsSheet(post, family, actions, onDismiss = { detailsFor = null }, onSearchTag = { tag, add ->
                detailsFor = null
                onSearchTag(post, tag, add)
            })
        }
    }
}

/**
 * Сначала облегчённая версия, оригинал подгружается при зуме; огромные файлы декодируются тайлами.
 * Двойной тап — «вписать / увеличить».
 */
@Composable
private fun ZoomPage(post: Post, onTap: () -> Unit) {
    val context = LocalContext.current
    val zoomState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f))
    val imageState = rememberZoomableImageState(zoomState)
    val zoomed = (zoomState.zoomFraction ?: 0f) > 0.05f
    var wantOriginal by remember(post.key) { mutableStateOf(false) }
    if (zoomed) wantOriginal = true
    val light = if (post.mediaType == MediaType.GIF) post.fileUrl else post.sampleUrl ?: post.fileUrl
    val url = if (wantOriginal) post.fileUrl ?: light else light
    ZoomableAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .apply { if (url != light && light != null) placeholderMemoryCacheKey(light) }
            .build(),
        contentDescription = null,
        state = imageState,
        contentScale = ContentScale.Fit,
        onClick = { onTap() },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Детали: теги по категориям цветом, размер и вес, дата, score, первоисточник, родитель и дети. */
@Composable
private fun DetailsSheet(
    post: Post,
    family: List<Post>,
    actions: PostActions,
    onDismiss: () -> Unit,
    onSearchTag: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalTagColors.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            val info = listOfNotNull(
                "${post.width}×${post.height}",
                listOfNotNull(post.fileExt?.uppercase(), formatSize(post.fileSize)).joinToString(" · ").ifEmpty { null },
                context.postDate(post.createdAt),
                "score ${post.score}",
                post.favCount?.let { stringResource(R.string.details_favs, it) },
            )
            Text(info.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            post.source?.let { source ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.details_source) + ": " + source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(source)))
                        }
                    },
                )
            }
            if (family.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.details_family), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(family, key = { it.key }) { member ->
                        AsyncImage(
                            model = member.previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
                        )
                    }
                }
            }
            TagCategory.entries.sortedBy { order(it) }.forEach { category ->
                val tags = post.tags.byCategory(category)
                if (tags.isEmpty()) return@forEach
                Spacer(Modifier.height(16.dp))
                Text(stringResource(categoryTitle(category)), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> TagChip(post, tag, colors.of(category), actions, onSearchTag) }
                }
            }
        }
    }
}

/** Тап по тегу: «Искать», «Добавить к поиску», «Не показывать», «Вики». */
@Composable
private fun TagChip(post: Post, tag: String, color: Color, actions: PostActions, onSearchTag: (String, Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { menu = true },
        ) {
            Text(tag.replace('_', ' '), color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.tag_search)) }, onClick = { menu = false; onSearchTag(tag, false) })
            DropdownMenuItem(text = { Text(stringResource(R.string.tag_add_to_search)) }, onClick = { menu = false; onSearchTag(tag, true) })
            DropdownMenuItem(text = { Text(stringResource(R.string.tag_hide)) }, onClick = { menu = false; actions.hideTag(post, tag) })
            DropdownMenuItem(text = { Text(stringResource(R.string.tag_wiki)) }, onClick = {
                menu = false
                val base = actions.postUrlBase(post)
                val url = if (post.site == "danbooru" || post.site == "safebooru") "$base/wiki_pages/$tag" else "$base/wiki/show?title=$tag"
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            })
        }
    }
}

private fun order(category: TagCategory) = when (category) {
    TagCategory.ARTIST -> 0
    TagCategory.COPYRIGHT -> 1
    TagCategory.CHARACTER -> 2
    TagCategory.GENERAL -> 3
    TagCategory.META -> 4
}

private fun categoryTitle(category: TagCategory) = when (category) {
    TagCategory.ARTIST -> R.string.category_artist
    TagCategory.COPYRIGHT -> R.string.category_copyright
    TagCategory.CHARACTER -> R.string.category_character
    TagCategory.GENERAL -> R.string.category_general
    TagCategory.META -> R.string.category_meta
}
