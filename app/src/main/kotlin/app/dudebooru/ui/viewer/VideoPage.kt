package app.dudebooru.ui.viewer

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.dudebooru.DudeApp
import app.dudebooru.R
import app.dudebooru.booru.model.Post
import app.dudebooru.ui.feed.formatClock
import app.dudebooru.ui.icons.DudeIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

/** Высота нижней панели просмотра без системной полосы: кнопки 40dp и отступы 10dp. */
private val ViewerBottomBar = 60.dp

/**
 * Видео играет прямо в просмотре без звука. Тап — панели; с ними над кнопками поста
 * появляется плеер: пауза, время, перемотка и звук. Без панелей внизу остаётся тонкая полоска прогресса.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPage(post: Post, active: Boolean, barsVisible: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    // Для угоиры (zip) Danbooru отдаёт webm-версию в large_file_url.
    val url = if (post.fileExt == "zip") post.lightUrl else post.fileUrl ?: post.lightUrl
    var muted by remember { mutableStateOf(true) }
    var paused by remember(post.key) { mutableStateOf(false) }
    var playing by remember(post.key) { mutableStateOf(false) }
    var position by remember(post.key) { mutableLongStateOf(0L) }
    var buffered by remember(post.key) { mutableLongStateOf(0L) }
    var duration by remember(post.key) { mutableLongStateOf(post.duration?.let { (it * 1000).roundToLong() } ?: 0L) }
    /** Куда тянут ползунок: пока палец на нём, позиция плеера не перебивает. */
    var seeking by remember(post.key) { mutableStateOf<Float?>(null) }
    val player = remember(post.key) {
        val client = (context.applicationContext as DudeApp).container.imageClient
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                url?.let { setMediaItem(MediaItem.fromUri(it)) }
                prepare()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(active, paused) { player.playWhenReady = active && !paused }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    // Позиция опрашивается, пока видео на экране: у ExoPlayer нет события «время изменилось».
    LaunchedEffect(player, active) {
        while (active && isActive) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            if (player.duration > 0) duration = player.duration
            delay(if (playing) 100 else 300)
        }
    }
    val progress = seeking ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (muted) {
            Surface(
                onClick = { muted = false },
                color = Color.Black.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DudeIcons.Mute, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.viewer_tap_for_sound), color = Color.White)
                }
            }
        }

        AnimatedVisibility(!barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().height(3.dp).drawBehind {
                    drawRect(Color.White.copy(alpha = 0.2f))
                    if (duration > 0) drawRect(Color.White.copy(alpha = 0.25f), size = Size(size.width * (buffered.toFloat() / duration).coerceIn(0f, 1f), size.height))
                    drawRect(Color.White, size = Size(size.width * progress, size.height))
                },
            )
        }

        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = ViewerBottomBar)
                    .background(Color.Black.copy(alpha = 0.45f))
                    // Промах мимо кнопок не прячет панели.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { paused = !paused }) {
                    Icon(
                        if (paused) DudeIcons.Play else DudeIcons.Pause,
                        stringResource(if (paused) R.string.video_play else R.string.video_pause),
                        tint = Color.White,
                    )
                }
                val shown = seeking?.let { (it * duration).roundToLong() } ?: position
                Text(formatClock(shown), color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
                Slider(
                    value = progress,
                    onValueChange = { seeking = it },
                    onValueChangeFinished = {
                        seeking?.let { target ->
                            val to = (target * duration).roundToLong()
                            player.seekTo(to)
                            position = to
                        }
                        seeking = null
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                    thumb = {
                        Box(Modifier.size(14.dp).background(Color.White, CircleShape))
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text(if (duration > 0) formatClock(duration) else "–:––", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
                IconButton(onClick = { muted = !muted }) {
                    Icon(
                        if (muted) DudeIcons.Mute else DudeIcons.Sound,
                        stringResource(if (muted) R.string.video_sound_on else R.string.video_sound_off),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
