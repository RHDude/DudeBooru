package app.dudebooru.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.dudebooru.R
import app.dudebooru.booru.model.MediaType
import app.dudebooru.booru.model.Post
import app.dudebooru.ui.common.artistLabel
import app.dudebooru.ui.common.postDate
import app.dudebooru.ui.icons.DudeIcons
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

/**
 * Пост во всю ширину по макету: аватарка, художник и дата, ⋮; картинка или карусель;
 * снизу ♥ без счётчика, «поделиться», «сохранить».
 */
@Composable
fun PostCard(item: FeedItem, controller: FeedController, actions: PostActions, modifier: Modifier = Modifier) {
    var page by remember(item.key) { mutableStateOf(0) }
    val current = item.posts.getOrElse(page) { item.lead }
    val collections = LocalCollections.current
    val context = LocalContext.current

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { actions.openArtist(current) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtistAvatar(current)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        context.artistLabel(current),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        context.postDate(current.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PostMenuButton(current, item.posts, actions)
        }

        PostMedia(
            item = item,
            onPage = { page = it },
            onTap = { actions.open(controller, it) },
            onDoubleTap = { actions.doubleTapLike(it) },
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val liked = current.key in collections.liked
            IconButton(onClick = { actions.toggleLike(current) }) {
                Icon(
                    if (liked) DudeIcons.HeartFilled else DudeIcons.Heart,
                    stringResource(R.string.action_like),
                    tint = if (liked) LikeRed else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { actions.share(current) }) {
                Icon(DudeIcons.Share, stringResource(R.string.action_share), tint = MaterialTheme.colorScheme.primary)
            }
            val saved = current.key in collections.saved
            IconButton(onClick = { actions.toggleSave(current) }) {
                Icon(if (saved) DudeIcons.SaveFilled else DudeIcons.Save, stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

val LikeRed = Color(0xFFE5484D)

/**
 * Картинка во всю ширину, высота по пропорциям — известна до загрузки, лента не прыгает.
 * Очень длинные (комиксы, сканы) обрезаются по высоте экрана с затуханием и пометкой «целиком».
 */
@Composable
private fun PostMedia(item: FeedItem, onPage: (Int) -> Unit, onTap: (Post) -> Unit, onDoubleTap: (Post) -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val natural = maxWidth / item.lead.aspectRatio.coerceAtLeast(0.05f)
        val maxHeight = screenHeight * 0.8f
        val height: Dp = min(natural, maxHeight)
        val isLong = natural > maxHeight * 1.05f
        val placeholder = placeholderColor(item.lead)

        if (item.posts.size == 1) {
            TapImage(item.lead, height, isLong, placeholder, onTap, onDoubleTap)
        } else {
            Carousel(item, height, placeholder, onPage, onTap, onDoubleTap)
        }
    }
}

@Composable
private fun Carousel(
    item: FeedItem,
    height: Dp,
    placeholder: Color,
    onPage: (Int) -> Unit,
    onTap: (Post) -> Unit,
    onDoubleTap: (Post) -> Unit,
) {
    val pager = rememberPagerState { item.posts.size }
    LaunchedEffect(pager.currentPage) { onPage(pager.currentPage) }
    // Свайп в карусели листает картинки, а не источники: на краях жест дальше не уходит.
    val blockParent = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available.copy(y = 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available.copy(y = 0f)
        }
    }
    Box(Modifier.fillMaxWidth().height(height)) {
        HorizontalPager(state = pager, key = { item.posts[it].key }, modifier = Modifier.fillMaxSize().nestedScroll(blockParent)) { index ->
            val post = item.posts[index]
            // Высота карточки — по первой картинке; остальные вписываются, чтобы не резать.
            TapImage(post, height, isLong = false, placeholder, onTap, onDoubleTap, fit = index > 0)
        }
        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        ) {
            Text(
                "${pager.currentPage + 1}/${item.posts.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (item.posts.size <= 12) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(item.posts.size) { i ->
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TapImage(
    post: Post,
    height: Dp,
    isLong: Boolean,
    placeholder: Color,
    onTap: (Post) -> Unit,
    onDoubleTap: (Post) -> Unit,
    fit: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = LocalFeedPrefs.current
    val actions = LocalPostActions.current
    val heart = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(placeholder)
            .pointerInput(post.key) {
                detectTapGestures(
                    onTap = { onTap(post) },
                    onDoubleTap = double@{
                        if (!prefs.doubleTapLike) return@double
                        onDoubleTap(post)
                        scope.launch {
                            heart.snapTo(0.6f)
                            heart.animateTo(1.15f, tween(160))
                            heart.animateTo(1f, tween(90))
                            heart.animateTo(0f, tween(300, delayMillis = 250))
                        }
                    },
                )
            },
    ) {
        val censor = LocalCensor.current
        if (censor.hides(post)) {
            CensoredImage(
                post = post,
                style = censor.prefs.style,
                strength = censor.prefs.strength,
                onReveal = { actions?.reveal(post) },
            )
        } else {
            AsyncImage(
                // GIF в ленте анимируются из оригинала (опция в настройках).
                model = ImageRequest.Builder(context)
                    .data(if (post.mediaType == MediaType.GIF && prefs.animateGifs) post.fileUrl else post.sampleUrl ?: post.previewUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = post.tags.character.joinToString(", ").ifEmpty { null },
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isLong) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))),
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                Text(stringResource(R.string.post_long_whole), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        if (post.mediaType != MediaType.IMAGE) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            ) {
                Text(
                    if (post.mediaType == MediaType.GIF) "GIF" else "▶ " + stringResource(R.string.post_video),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (heart.value > 0f) {
            Icon(
                DudeIcons.HeartFilled,
                null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(96.dp).scale(heart.value).graphicsLayer { alpha = heart.value.coerceIn(0f, 1f) },
            )
        }
    }
}

/** Плейсхолдер нужной высоты, пока грузится картинка; цвет стабильный по md5. */
fun placeholderColor(post: Post): Color {
    val seed = post.md5?.take(6)?.toIntOrNull(16) ?: post.id.toInt()
    val shade = 0x60 + Math.floorMod(seed, 0x30)
    return Color(shade, shade, shade + 10, 0x55)
}
