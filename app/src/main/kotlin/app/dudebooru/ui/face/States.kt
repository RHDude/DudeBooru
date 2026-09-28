package app.dudebooru.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dudebooru.ui.theme.LocalAppTheme
import app.dudebooru.ui.theme.ThemePattern
import kotlinx.coroutines.delay

/** Каомодзи пустых экранов: каждый раз случайный из набора и не повторяется два раза подряд. */
object Kaomoji {
    val SAD = listOf("(╥﹏╥)", "(｡•́︿•̀｡)", "(ಥ﹏ಥ)", "(´；ω；`)", "(｡╯︵╰｡)", "(T_T)")
    val BORED = listOf("(￣～￣;)", "( ¬_¬)", "(－_－) zzZ", "┐(￣ヘ￣)┌", "(-_-;)・・・", "( ˘_˘ )")
    val CONFUSED = listOf("(・・ ) ?", "(・_・;)", "(°ロ°) ?", "( ・◇・)?", "(⊙_⊙)?")

    private val last = HashMap<List<String>, String>()

    @Synchronized
    fun pick(set: List<String>): String {
        val choice = set.filter { it != last[set] }.random()
        last[set] = choice
        return choice
    }
}

@Composable
fun rememberKaomoji(set: List<String>): String = remember(set) { Kaomoji.pick(set) }

/**
 * Пустой экран по-человечески: крупный каомодзи по центру, текст, подсказка и кнопки.
 * У темы Maid сверху кружево.
 */
@Composable
fun EmptyState(
    kaomoji: String,
    text: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    title: String? = null,
    buttons: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (LocalAppTheme.current.pattern == ThemePattern.LACE) {
            LaceEdge(Modifier.fillMaxWidth().height(18.dp))
            Spacer(Modifier.height(20.dp))
        }
        Text(kaomoji, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(16.dp))
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        buttons()
    }
}

/** Кружево темы Maid: зубчики с дырочками по нижнему краю. */
@Composable
fun LaceEdge(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)) {
    Canvas(modifier) {
        val scallop = size.height * 1.1f
        val count = (size.width / scallop).toInt().coerceAtLeast(1)
        val step = size.width / count
        val r = step / 2
        val stroke = Stroke(width = 1.2.dp.toPx())
        drawLine(color, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 1.2.dp.toPx())
        for (i in 0 until count) {
            val cx = step * i + r
            drawArc(color, 0f, 180f, useCenter = false, topLeft = Offset(cx - r, -r + 1f), size = Size(r * 2, r * 2), style = stroke)
            drawCircle(color, radius = r * 0.22f, center = Offset(cx, r * 0.45f))
            drawCircle(color, radius = r * 0.12f, center = Offset(step * i, r * 0.2f))
        }
    }
}

/**
 * Загрузка дольше 3 секунд: под скелетонами маленький бегущий Дуди и «Стучимся в Yande.re…».
 */
@Composable
fun SlowLoadingHint(siteName: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3000)
        visible = true
    }
    if (!visible) return
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RunningDudi(Modifier.size(width = 27.dp, height = 30.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            androidx.compose.ui.res.stringResource(app.dudebooru.R.string.loading_knocking, siteName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

/** Дуди бежит: два кадра по 140 мс; при «уменьшить движение» стоит. */
@Composable
fun RunningDudi(modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        while (true) {
            delay(140)
            frame = !frame
        }
    }
    Dudi(if (frame) DudiEmotion.RUN2 else DudiEmotion.RUN1, modifier)
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
