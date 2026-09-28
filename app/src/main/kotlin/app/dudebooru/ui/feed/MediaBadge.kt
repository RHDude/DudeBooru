package app.dudebooru.ui.feed

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dudebooru.R
import app.dudebooru.booru.model.MediaType
import app.dudebooru.booru.model.Post
import app.dudebooru.ui.icons.DudeIcons
import kotlin.math.roundToLong

/**
 * Отметка вида поста. Видео — тёмная плашка с ▶ и длительностью (если сайт её знает),
 * GIF — светлая плашка «GIF»: их не спутать ни между собой, ни с картинкой.
 */
@Composable
fun MediaBadge(post: Post, modifier: Modifier = Modifier, small: Boolean = false) {
    val shape = RoundedCornerShape(if (small) 6.dp else 8.dp)
    val style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    when (post.mediaType) {
        MediaType.IMAGE -> Unit
        MediaType.VIDEO -> Surface(color = Color.Black.copy(alpha = 0.6f), shape = shape, modifier = modifier) {
            Row(
                Modifier.padding(start = if (small) 2.dp else 4.dp, end = if (small) 5.dp else 8.dp, top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DudeIcons.Play, null, tint = Color.White, modifier = Modifier.size(if (small) 14.dp else 18.dp))
                Text(post.duration?.let { formatClock((it * 1000).roundToLong()) } ?: stringResource(R.string.post_video), color = Color.White, style = style)
            }
        }
        MediaType.GIF -> Surface(color = Color.White.copy(alpha = 0.92f), shape = shape, modifier = modifier) {
            Text(
                "GIF",
                color = Color.Black,
                style = style,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = if (small) 5.dp else 7.dp, vertical = 1.dp),
            )
        }
    }
}

/** 0:07, 1:23, 1:02:03. */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
