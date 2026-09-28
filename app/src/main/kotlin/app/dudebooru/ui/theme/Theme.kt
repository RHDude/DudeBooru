package app.dudebooru.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import app.dudebooru.booru.model.TagCategory
import kotlin.math.hypot

/** Цвета категорий тегов из ТЗ; в тёмной теме светлее. */
@Immutable
data class TagColors(
    val artist: Color,
    val copyright: Color,
    val character: Color,
    val general: Color,
    val meta: Color,
) {
    fun of(category: TagCategory): Color = when (category) {
        TagCategory.ARTIST -> artist
        TagCategory.COPYRIGHT -> copyright
        TagCategory.CHARACTER -> character
        TagCategory.GENERAL -> general
        TagCategory.META -> meta
    }
}

private val LightTagColors = TagColors(Color(0xFFC0392B), Color(0xFF8E44AD), Color(0xFF1E8A4C), Color(0xFF2F6FDE), Color(0xFFD9822B))
private val DarkTagColors = TagColors(Color(0xFFFF7B6B), Color(0xFFC792EA), Color(0xFF5FD08D), Color(0xFF6F9CF2), Color(0xFFF2A65A))

val LocalTagColors = staticCompositionLocalOf { LightTagColors }

/** Текущая тема целиком: скругления, отступы сетки, узор, фон — не только цвета. */
val LocalAppTheme = staticCompositionLocalOf { ThemePresets.MONET }

/** Тёмный ли сейчас вариант темы. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Тема приложения: Monet по умолчанию (цвета от обоев, Android 12+; на старых — синяя),
 * пресеты и своя тема из редактора. У каждой — светлый и тёмный вариант.
 */
@Composable
fun DudeTheme(theme: AppTheme = ThemePresets.MONET, dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = if (dark) theme.dark else theme.light
    val scheme = when {
        palette.monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> palette.toScheme(dark)
    }
    val family = when (theme.font) {
        UiFont.DEFAULT -> FontFamily.Default
        UiFont.SERIF -> FontFamily.Serif
        UiFont.MONO -> FontFamily.Monospace
    }
    val typography = remember(family) { Typography().withFamily(family) }
    val base = LocalDensity.current
    val factor = when (theme.density) {
        UiDensity.COMPACT -> 0.92f
        UiDensity.NORMAL -> 1f
        UiDensity.COMFY -> 1.08f
    }
    CompositionLocalProvider(
        LocalTagColors provides if (dark) DarkTagColors else LightTagColors,
        LocalAppTheme provides theme,
        LocalDarkTheme provides dark,
        LocalDensity provides Density(base.density * factor, base.fontScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

/**
 * Смена светлой и тёмной темы кругом, расходящимся от кнопки: снимок старого экрана,
 * под ним уже новая тема, в снимке растёт дыра. Без мигания и перезапуска экрана.
 */
class ThemeReveal {
    internal var layer: GraphicsLayer? = null
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal var center by mutableStateOf(Offset.Zero)
    internal val radius = Animatable(0f)
    internal var reducedMotion = false

    suspend fun run(from: Offset, apply: () -> Unit) {
        val current = layer
        if (current == null || reducedMotion) {
            apply()
            return
        }
        snapshot = runCatching { current.toImageBitmap() }.getOrNull()
        center = from
        radius.snapTo(0f)
        apply()
        val bitmap = snapshot ?: return
        val max = maxOf(
            hypot(from.x, from.y),
            hypot(bitmap.width - from.x, from.y),
            hypot(from.x, bitmap.height - from.y),
            hypot(bitmap.width - from.x, bitmap.height - from.y),
        )
        radius.animateTo(max, tween(480, easing = FastOutSlowInEasing))
        snapshot = null
    }
}

val LocalThemeReveal = staticCompositionLocalOf { ThemeReveal() }

@Composable
fun ThemeRevealHost(reveal: ThemeReveal, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    reveal.layer = layer
    reveal.reducedMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
            CompositionLocalProvider(LocalThemeReveal provides reveal, content = content)
        }
        val shot = reveal.snapshot
        if (shot != null) {
            Canvas(Modifier.fillMaxSize()) {
                val r = reveal.radius.value
                val hole = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addOval(Rect(reveal.center, r))
                }
                clipPath(hole) { drawImage(shot) }
            }
        }
    }
}
