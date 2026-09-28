package app.dudebooru.ui.viewer

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

/** Видео играет прямо в просмотре без звука; тап — звук и панели. */
@OptIn(UnstableApi::class)
@Composable
fun VideoPage(post: Post, active: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    // Для угоиры (zip) Danbooru отдаёт webm-версию в large_file_url.
    val url = if (post.fileExt == "zip") post.lightUrl else post.fileUrl ?: post.lightUrl
    var muted by remember { mutableStateOf(true) }
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
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(active) { player.playWhenReady = active }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            muted = !muted
            onTap()
        },
    ) {
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
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small, modifier = Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.viewer_tap_for_sound), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}
