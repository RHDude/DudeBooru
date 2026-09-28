package app.dudebooru.ui.collections

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

private val AvatarSize = 104.dp
private val CardCorner = 28.dp

/**
 * Растяжка шапки профиля, как в Telegram: тянешь ленту вниз у самого верха — аватарка растёт
 * до квадрата во всю карточку, отпустил — пружиной возвращается в круг.
 * [max] — сколько пикселей растяжки нужно, чтобы аватарка заняла всю ширину.
 */
@Stable
internal class AvatarStretch(private val max: Float) {
    var value by mutableFloatStateOf(0f)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Назад вверх сначала сжимается аватарка, потом едет лента.
            if (source != NestedScrollSource.UserInput || available.y >= 0f || value <= 0f) return Offset.Zero
            val used = maxOf(available.y, -value)
            value += used
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            // Резинка: чем больше аватарка, тем туже тянется.
            val resistance = 0.8f * (1f - value / max).coerceAtLeast(0.2f)
            value = (value + available.y * resistance).coerceAtMost(max)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (value <= 0f) return Velocity.Zero
            animate(value, 0f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                value = v.coerceAtLeast(0f)
            }
            return available
        }
    }
}

/**
 * Карточка профиля: аватарка по центру, имя и ник под ней. Фон — та же аватарка, размытая
 * и притушенная, в скруглённой карточке: шапка отделена от ленты и окрашена в цвета картинки.
 */
@Composable
internal fun ProfileCard(avatarUrl: String?, name: String, nick: String, stretch: Float, onClick: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val grow = with(density) { stretch.toDp() }
        val size = (AvatarSize + grow).coerceAtMost(maxWidth)
        // 0 — круг, 1 — квадрат во всю ширину карточки, прижатый к её верху.
        val t = if (maxWidth > AvatarSize) ((size - AvatarSize) / (maxWidth - AvatarSize)).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner))
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarUrl).size(64).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier)
                        .graphicsLayer { alpha = 0.6f },
                )
            } else {
                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.35f), scheme.tertiary.copy(alpha = 0.2f)))))
            }
            // Низ карточки темнее к цвету фона — имя читается на любой картинке.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(scheme.surfaceContainerHigh.copy(alpha = 0.1f), scheme.surfaceContainerHigh.copy(alpha = 0.8f))),
                ),
            )
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(lerp(20.dp, 0.dp, t)))
                Box(
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(lerp(size / 2, CardCorner, t)))
                        .background(Brush.linearGradient(listOf(Color(0xFF7FD6CC), Color(0xFF7B4FD1)))),
                ) {
                    if (avatarUrl != null) {
                        // Сразу крупная копия: растянутая аватарка не мылится.
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(avatarUrl).size(1080).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text("@$nick", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}
