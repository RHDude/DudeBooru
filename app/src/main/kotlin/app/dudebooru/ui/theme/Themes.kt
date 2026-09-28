package app.dudebooru.ui.theme

import android.util.Base64
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import app.dudebooru.booru.net.BooruJson
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Цвета одного варианта темы (светлого или тёмного). [monet] — брать цвета от обоев. */
@Serializable
data class ThemePalette(
    val accent: Long,
    val background: Long,
    val surface: Long,
    val monet: Boolean = false,
    /** Акцент для деталей (розовые детали у Miku). */
    val detail: Long? = null,
)

@Serializable
enum class UiDensity { COMPACT, NORMAL, COMFY }

@Serializable
enum class UiFont { DEFAULT, SERIF, MONO }

@Serializable
enum class ThemePattern { NONE, LACE }

/** Фон ленты и меню: цвет, градиент или картинка с размытием и затемнением (как обои чатов в Telegram). */
@Serializable
data class ThemeBackground(
    val kind: Kind = Kind.NONE,
    val color1: Long = 0,
    val color2: Long = 0,
    /** Локальный файл картинки; в файл темы упаковывается внутрь (base64). */
    val imagePath: String? = null,
    val blur: Float = 0.4f,
    val dim: Float = 0.35f,
) {
    @Serializable
    enum class Kind { NONE, COLOR, GRADIENT, IMAGE }
}

/** Тема: пресет или своя из редактора. */
@Serializable
data class AppTheme(
    val id: String,
    val name: String,
    val light: ThemePalette,
    val dark: ThemePalette,
    val cornerRadius: Int = 0,
    val gridSpacing: Int = 2,
    val density: UiDensity = UiDensity.NORMAL,
    val font: UiFont = UiFont.DEFAULT,
    val pattern: ThemePattern = ThemePattern.NONE,
    val background: ThemeBackground = ThemeBackground(),
)

object ThemePresets {
    private fun c(hex: Long) = hex

    val MONET = AppTheme(
        id = "monet", name = "Monet",
        light = ThemePalette(c(0xFF2F6FDE), c(0xFFF7F8FB), c(0xFFFFFFFF), monet = true),
        dark = ThemePalette(c(0xFF6F9CF2), c(0xFF12131A), c(0xFF1A1C26), monet = true),
    )
    val CLASSIC = AppTheme(
        id = "classic", name = "Классическая",
        light = ThemePalette(c(0xFF2F6FDE), c(0xFFF7F8FB), c(0xFFFFFFFF)),
        dark = ThemePalette(c(0xFF6F9CF2), c(0xFF12131A), c(0xFF1C1E29)),
    )
    val AMOLED = AppTheme(
        id = "amoled", name = "AMOLED",
        light = ThemePalette(c(0xFF2F6FDE), c(0xFFFFFFFF), c(0xFFF3F4F8)),
        dark = ThemePalette(c(0xFF7FA9F5), c(0xFF000000), c(0xFF0E0E12)),
    )
    /** Бирюзовый акцент, тёмно-серый фон, розовые детали. */
    val MIKU = AppTheme(
        id = "miku", name = "Miku",
        light = ThemePalette(c(0xFF139C94), c(0xFFF2F7F7), c(0xFFFFFFFF), detail = c(0xFFE12885)),
        dark = ThemePalette(c(0xFF39C5BB), c(0xFF1E2126), c(0xFF272B31), detail = c(0xFFFF5FA8)),
        cornerRadius = 12,
    )
    /** Чёрно-белая с кружевным узором в шапке меню и на пустых экранах. */
    val MAID = AppTheme(
        id = "maid", name = "Maid",
        light = ThemePalette(c(0xFF1A1A1A), c(0xFFFFFFFF), c(0xFFF4F4F4)),
        dark = ThemePalette(c(0xFFF2F2F2), c(0xFF0D0D0D), c(0xFF1A1A1A)),
        pattern = ThemePattern.LACE,
        font = UiFont.SERIF,
    )
    /** Светлая, бледно-розовая. */
    val SAKURA = AppTheme(
        id = "sakura", name = "Sakura",
        light = ThemePalette(c(0xFFD9577E), c(0xFFFFF4F7), c(0xFFFFFFFF)),
        dark = ThemePalette(c(0xFFFF9EBB), c(0xFF22161B), c(0xFF2E1F25)),
        cornerRadius = 16,
    )

    val all = listOf(MONET, CLASSIC, AMOLED, MIKU, MAID, SAKURA)

    fun byId(id: String?) = all.firstOrNull { it.id == id }
}

/** Цветовая схема Material 3 из трёх цветов темы. */
fun ThemePalette.toScheme(dark: Boolean): ColorScheme {
    val accent = Color(accent)
    val bg = Color(background)
    val surface = Color(surface)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val onBg = if (bg.luminance() > 0.5f) Color(0xFF15161C) else Color(0xFFE9EAF0)
    val variant = onBg.copy(alpha = 0.66f).compositeOver(bg)
    val container = accent.copy(alpha = if (dark) 0.28f else 0.16f).compositeOver(bg)
    val high = surface.copy(alpha = 0.9f).compositeOver(onBg.copy(alpha = 0.06f).compositeOver(bg))
    val highest = onBg.copy(alpha = if (dark) 0.14f else 0.09f).compositeOver(bg)
    val detail = detail?.let(::Color) ?: accent
    return if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent, secondary = detail, tertiary = detail,
            primaryContainer = container, onPrimaryContainer = onBg,
            secondaryContainer = container, onSecondaryContainer = onBg,
            background = bg, onBackground = onBg, surface = bg, onSurface = onBg,
            surfaceVariant = highest, onSurfaceVariant = variant,
            surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
            surfaceContainerHigh = high, surfaceContainerHighest = highest,
            outline = onBg.copy(alpha = 0.35f).compositeOver(bg), outlineVariant = onBg.copy(alpha = 0.15f).compositeOver(bg),
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent, secondary = detail, tertiary = detail,
            primaryContainer = container, onPrimaryContainer = onBg,
            secondaryContainer = container, onSecondaryContainer = onBg,
            background = bg, onBackground = onBg, surface = bg, onSurface = onBg,
            surfaceVariant = highest, onSurfaceVariant = variant,
            surfaceContainerLowest = Color.White, surfaceContainerLow = surface, surfaceContainer = surface,
            surfaceContainerHigh = high, surfaceContainerHighest = highest,
            outline = onBg.copy(alpha = 0.35f).compositeOver(bg), outlineVariant = onBg.copy(alpha = 0.15f).compositeOver(bg),
        )
    }
}

fun Color.toLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Обмен темами: файл `.booru-theme` (JSON, картинка фона внутри) и короткий код без картинки. */
object ThemeCodec {
    @Serializable
    data class ThemeFile(val format: String = "dudebooru-theme", val version: Int = 1, val theme: AppTheme, val imageBase64: String? = null)

    fun toFile(theme: AppTheme, image: ByteArray?): String =
        BooruJson.encodeToString(ThemeFile.serializer(), ThemeFile(theme = theme.copy(background = theme.background.copy(imagePath = null)), imageBase64 = image?.let { Base64.encodeToString(it, Base64.NO_WRAP) }))

    fun fromFile(text: String): Pair<AppTheme, ByteArray?>? = runCatching {
        val file = BooruJson.decodeFromString(ThemeFile.serializer(), text)
        if (file.format != "dudebooru-theme") return null
        file.theme to file.imageBase64?.let { Base64.decode(it, Base64.NO_WRAP) }
    }.getOrNull()

    fun toCode(theme: AppTheme): String {
        val json = BooruJson.encodeToString(AppTheme.serializer(), theme.copy(background = theme.background.copy(imagePath = null)))
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply {
            setInput(json.toByteArray())
            finish()
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        return "DB1:" + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun fromCode(code: String): AppTheme? = runCatching {
        val body = code.trim().removePrefix("DB1:")
        val inflater = Inflater().apply { setInput(Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE)) }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && inflater.needsInput()) break
            out.write(buffer, 0, n)
        }
        BooruJson.decodeFromString(AppTheme.serializer(), out.toString(Charsets.UTF_8.name()))
    }.getOrNull()
}
