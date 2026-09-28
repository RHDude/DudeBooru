package app.dudebooru.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.dudebooru.booru.model.TagCategory

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

private val LightTagColors = TagColors(
    artist = Color(0xFFC0392B),
    copyright = Color(0xFF8E44AD),
    character = Color(0xFF1E8A4C),
    general = Color(0xFF2F6FDE),
    meta = Color(0xFFD9822B),
)

private val DarkTagColors = TagColors(
    artist = Color(0xFFFF7B6B),
    copyright = Color(0xFFC792EA),
    character = Color(0xFF5FD08D),
    general = Color(0xFF6F9CF2),
    meta = Color(0xFFF2A65A),
)

val LocalTagColors = staticCompositionLocalOf { LightTagColors }

/** Monet по умолчанию (Android 12+), на старых версиях — синяя тема. Пресеты и редактор — шаг «лицо». */
@Composable
fun DudeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF6F9CF2), secondary = Color(0xFFC792EA))
        else -> lightColorScheme(primary = Color(0xFF2F6FDE), secondary = Color(0xFF8E44AD))
    }
    CompositionLocalProvider(LocalTagColors provides if (dark) DarkTagColors else LightTagColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
