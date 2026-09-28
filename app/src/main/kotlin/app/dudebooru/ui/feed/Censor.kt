package app.dudebooru.ui.feed

import android.content.Context
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.Rating
import app.dudebooru.data.settings.CensorPrefs
import app.dudebooru.data.settings.CensorStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as BitmapCanvas

/** Что сейчас закрыто: глаз в меню, режим, настройки цензуры и снятые вручную посты. */
@Immutable
data class CensorState(
    val enabled: Boolean = false,
    val mode: ContentMode = ContentMode.SFW,
    val prefs: CensorPrefs = CensorPrefs(),
    val revealed: Set<String> = emptySet(),
) {
    /**
     * Цензура работает в режимах «NSFW» и «Всё»; посты q/e, по желанию и s.
     * В режиме SFW откровенное приходит только из своих лент (лайки, история, сохранённые) —
     * там оно закрыто всегда, и в ленте, и в просмотре.
     *
     * В просмотре правила те же: соседние посты, до которых долистал, и «Случайный пост» закрыты,
     * пока не нажмёшь. Пост, открытый из ленты, считается показанным ([CensorPrefs.inViewer] —
     * чтобы закрыт был и он).
     */
    fun hides(post: Post): Boolean {
        if (post.key in revealed) return false
        if (mode == ContentMode.SFW) return post.rating.isNsfw
        if (!enabled) return false
        return post.rating.isNsfw || (prefs.blurSensitive && post.rating == Rating.SENSITIVE)
    }

    /** Аватарки художников — вырезки из работ: в режимах с откровенным при цензуре размываются. */
    val blursAvatars: Boolean get() = enabled && mode != ContentMode.SFW
}

val LocalCensor = staticCompositionLocalOf { CensorState() }

/**
 * Закрытая картинка: как спойлер в Telegram — размытие и медленно плывущие светлые частицы,
 * по центру «NSFW · нажмите, чтобы показать». Тап — частицы рассыпаются от места касания.
 */
@Composable
fun CensoredImage(post: Post, style: CensorStyle, strength: Float, onReveal: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Полная картинка грузится заранее: после тапа она появляется сразу, без серого плейсхолдера.
    LaunchedEffect(post.key) {
        post.sampleUrl?.let { url -> SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(url).build()) }
    }
    // Одна маленькая копия на пост: сила меняется мгновенно, картинку не нужно грузить заново.
    val source by produceState<ImageBitmap?>(null, post.key) {
        value = loadCensorSource(context, post.previewUrl ?: post.sampleUrl)
    }
    CensorCover(source, style, strength, onReveal = onReveal, modifier = modifier, seed = post.id.toInt())
}

/** Предпросмотр цензуры в настройках: закат с солнцем и холмами — видно, как меняется сила. */
@Composable
fun CensorPreview(style: CensorStyle, strength: Float, modifier: Modifier = Modifier) {
    val sample = remember { previewSample() }
    CensorCover(sample, style, strength, onReveal = null, modifier = modifier, seed = 7)
}

/**
 * Сама обложка: копия картинки, сжатая до нескольких клеток и растянутая обратно —
 * крупные пиксели или мягкое пятно (на Android 12+ ещё и настоящее размытие), сверху частицы.
 */
@Composable
private fun CensorCover(
    source: ImageBitmap?,
    style: CensorStyle,
    strength: Float,
    onReveal: (() -> Unit)?,
    modifier: Modifier = Modifier,
    seed: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val burst = remember { Animatable(0f) }
    var burstFrom by remember { mutableStateOf<Offset?>(null) }
    val reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }

    val pixelate = style == CensorStyle.PIXELATE
    // Клеток по длинной стороне: чем сильнее цензура, тем их меньше.
    val cells = if (pixelate) 48f - 36f * strength else 28f - 20f * strength
    val blurRadius = (8 + 24 * strength).dp
    val cover = remember(source, cells, pixelate) { coverBlock(source, cells, pixelate) }

    Box(
        modifier
            .fillMaxSize()
            .then(
                if (onReveal == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (burstFrom != null) return@detectTapGestures
                            burstFrom = offset
                            scope.launch {
                                if (style == CensorStyle.SPOILER && !reducedMotion) burst.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                                onReveal()
                            }
                        }
                    }
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (!pixelate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurRadius) else Modifier)
                .graphicsLayer { alpha = 1f - burst.value * 0.3f }
                .drawWithCache(cover),
        )
        if (style == CensorStyle.SPOILER) {
            SpoilerParticles(animate = !reducedMotion, burstFrom = burstFrom, burst = { burst.value }, seed = seed)
        }
        if (onReveal != null && burstFrom == null) {
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

private fun coverBlock(source: ImageBitmap?, cells: Float, pixelate: Boolean): CacheDrawScope.() -> DrawResult = {
    val small = source?.let { shrink(it, size, cells) }
    onDrawBehind {
        if (small != null) {
            drawImage(
                small,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(small.width, small.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = if (pixelate) FilterQuality.None else FilterQuality.Low,
            )
        }
    }
}

/** Маленькая программная копия (не аппаратная: её рисуем в свой холст). */
private suspend fun loadCensorSource(context: Context, url: String?): ImageBitmap? {
    if (url == null) return null
    val request = ImageRequest.Builder(context).data(url).size(SOURCE_SIZE).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
}

/** Кадр как у ContentScale.Crop, сжатый до [cells] клеток по длинной стороне. */
private fun shrink(source: ImageBitmap, target: Size, cells: Float): ImageBitmap? {
    if (target.width < 1f || target.height < 1f || source.width < 1 || source.height < 1) return null
    val cell = max(target.width, target.height) / cells.coerceAtLeast(2f)
    val w = ceil(target.width / cell).toInt().coerceIn(1, 256)
    val h = ceil(target.height / cell).toInt().coerceIn(1, 256)
    val sourceAspect = source.width.toFloat() / source.height
    val targetAspect = target.width / target.height
    val cropWidth = if (sourceAspect > targetAspect) source.height * targetAspect else source.width.toFloat()
    val cropHeight = if (sourceAspect > targetAspect) source.height.toFloat() else source.width / targetAspect
    val out = ImageBitmap(w, h)
    BitmapCanvas(out).drawImageRect(
        image = source,
        srcOffset = IntOffset(((source.width - cropWidth) / 2).roundToInt(), ((source.height - cropHeight) / 2).roundToInt()),
        srcSize = IntSize(cropWidth.roundToInt().coerceAtLeast(1), cropHeight.roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(w, h),
        paint = Paint().apply { filterQuality = FilterQuality.Low },
    )
    return out
}

private fun previewSample(): ImageBitmap {
    val bitmap = ImageBitmap(96, 64)
    val canvas = BitmapCanvas(bitmap)
    val paint = Paint()
    paint.shader = LinearGradientShader(Offset.Zero, Offset(0f, 64f), listOf(Color(0xFFFFD59E), Color(0xFFF07A7A), Color(0xFF9B6FD6)))
    canvas.drawRect(0f, 0f, 96f, 64f, paint)
    paint.shader = null
    paint.color = Color(0xFFFFF3C4)
    canvas.drawCircle(Offset(66f, 22f), 11f, paint)
    paint.color = Color(0xFF3D9B8F)
    canvas.drawCircle(Offset(18f, 82f), 38f, paint)
    paint.color = Color(0xFF1E4F6B)
    canvas.drawCircle(Offset(82f, 88f), 42f, paint)
    return bitmap
}

private const val SOURCE_SIZE = 64

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
