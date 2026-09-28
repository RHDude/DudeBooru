package app.dudebooru.ui.feed

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.Rating
import app.dudebooru.data.settings.CensorPrefs
import app.dudebooru.data.settings.CensorStyle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Что сейчас закрыто: глаз в меню, режим, настройки цензуры и снятые вручную посты. */
@Immutable
data class CensorState(
    val enabled: Boolean = false,
    val mode: ContentMode = ContentMode.SFW,
    val prefs: CensorPrefs = CensorPrefs(),
    val revealed: Set<String> = emptySet(),
) {
    /** Цензура работает в режимах «NSFW» и «Всё»; посты q/e, по желанию и s. */
    fun hides(post: Post, inViewer: Boolean = false): Boolean {
        if (!enabled || mode == ContentMode.SFW) return false
        if (inViewer && !prefs.inViewer) return false
        if (post.key in revealed) return false
        return post.rating.isNsfw || (prefs.blurSensitive && post.rating == Rating.SENSITIVE)
    }
}

val LocalCensor = staticCompositionLocalOf { CensorState() }

/**
 * Закрытая картинка: как спойлер в Telegram — размытие и медленно плывущие светлые частицы,
 * по центру «NSFW · нажмите, чтобы показать». Тап — частицы рассыпаются от места касания.
 */
@Composable
fun CensoredImage(post: Post, style: CensorStyle, strength: Float, onReveal: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val burst = remember { Animatable(0f) }
    var burstFrom by remember { mutableStateOf<Offset?>(null) }
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }

    // Полная картинка грузится заранее: после тапа она появляется сразу, без серого плейсхолдера.
    LaunchedEffect(post.key) {
        post.sampleUrl?.let { url -> coil3.SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(url).build()) }
    }

    // Маленькая версия, растянутая на всю площадь: размытие на любом Android, пиксели — без сглаживания.
    val pixelate = style == CensorStyle.PIXELATE
    val tiny = if (pixelate) (48 - 36 * strength).toInt().coerceAtLeast(8) else (28 - 20 * strength).toInt().coerceAtLeast(6)
    val blurRadius = (8 + 24 * strength).dp

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(post.key) {
                detectTapGestures { offset ->
                    if (burstFrom != null) return@detectTapGestures
                    burstFrom = offset
                    scope.launch {
                        if (style == CensorStyle.SPOILER && !reducedMotion) burst.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                        onReveal()
                    }
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(post.previewUrl ?: post.sampleUrl).size(tiny).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = if (pixelate) FilterQuality.None else FilterQuality.Low,
            modifier = Modifier
                .fillMaxSize()
                .then(if (!pixelate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius) else Modifier)
                .graphicsLayer { alpha = 1f - burst.value * 0.3f },
        )
        if (style == CensorStyle.SPOILER) {
            SpoilerParticles(animate = !reducedMotion, burstFrom = burstFrom, burst = { burst.value }, seed = post.id.toInt())
        }
        if (burstFrom == null) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    stringResource(R.string.censor_tap_to_show),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

private class Particle(val x: Float, val y: Float, val speed: Float, val angle: Float, val radius: Float, val phase: Float)

@Composable
private fun SpoilerParticles(animate: Boolean, burstFrom: Offset?, burst: () -> Float, seed: Int) {
    val particles = remember(seed) {
        val random = Random(seed)
        List(140) {
            Particle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                speed = 0.004f + random.nextFloat() * 0.012f,
                angle = random.nextFloat() * 2f * PI.toFloat(),
                radius = 0.6f + random.nextFloat() * 1.1f,
                phase = random.nextFloat() * 2f * PI.toFloat(),
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time = (it - start) / 1000f }
    }
    Canvas(Modifier.fillMaxSize()) {
        val b = burst()
        val density = 1.dp.toPx()
        for (p in particles) {
            // Медленный дрейф по кругу, частицы «дышат» яркостью.
            var x = ((p.x + cos(p.angle) * p.speed * time) % 1f + 1f) % 1f * size.width
            var y = ((p.y + sin(p.angle) * p.speed * time) % 1f + 1f) % 1f * size.height
            var alpha = 0.45f + 0.45f * sin(time * 1.6f + p.phase)
            if (burstFrom != null && b > 0f) {
                val dx = x - burstFrom.x
                val dy = y - burstFrom.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                val push = b * size.minDimension * 0.7f * (1.2f - (d / size.maxDimension).coerceIn(0f, 1f))
                x += dx / d * push
                y += dy / d * push
                alpha *= 1f - b
            }
            drawCircle(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), radius = p.radius * density, center = Offset(x, y))
        }
    }
}
