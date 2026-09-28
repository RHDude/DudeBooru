package app.dudebooru.ui.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.site.Sites
import app.dudebooru.ui.icons.DudeIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Что выбрали на экранах первого запуска. */
data class OnboardingChoice(
    val mode: ContentMode,
    val censor: Boolean,
    val order: List<String>,
    val hidden: Set<String>,
    val look: Look,
    val icon: AppIcon,
) {
    enum class Look { MONET, LIGHT, DARK }
}

/**
 * Первый запуск: четыре экрана, любой можно пропустить, всё потом меняется в настройках.
 * Вход в аккаунты сюда не входит — он живёт в настройках.
 */
@Composable
fun OnboardingScreen(initialOrder: List<String>, initialHidden: Set<String>, onDone: (OnboardingChoice) -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ContentMode.SFW) }
    var adult by remember { mutableStateOf(false) }
    var censor by remember { mutableStateOf(true) }
    val order = remember { mutableStateListOf<String>().apply { addAll(initialOrder) } }
    val hidden = remember { mutableStateListOf<String>().apply { addAll(initialHidden) } }
    var look by remember { mutableStateOf(OnboardingChoice.Look.MONET) }
    var icon by remember { mutableStateOf(IconManager.current(context)) }

    fun finish() = onDone(
        OnboardingChoice(
            mode = if (mode != ContentMode.SFW && !adult) ContentMode.SFW else mode,
            censor = censor,
            order = order.toList(),
            hidden = hidden.toSet(),
            look = look,
            icon = icon,
        ),
    )

    fun next() {
        if (pager.currentPage == 3) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { i ->
                        val active = i == pager.currentPage
                        Box(
                            Modifier.size(width = if (active) 20.dp else 8.dp, height = 8.dp).clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
                if (pager.currentPage > 0) {
                    TextButton(onClick = ::next) { Text(stringResource(R.string.onb_skip)) }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = pager.currentPage > 0) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> HelloPage()
                        1 -> ContentPage(mode, adult, censor, onMode = { mode = it }, onAdult = { adult = it }, onCensor = { censor = it })
                        2 -> SourcesPage(order, hidden)
                        else -> LookPage(look, icon, onLook = { look = it }, onIcon = { icon = it })
                    }
                }
            }
            Button(
                onClick = ::next,
                enabled = !(pager.currentPage == 1 && mode != ContentMode.SFW && !adult),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(52.dp),
            ) {
                Text(
                    stringResource(
                        when (pager.currentPage) {
                            0 -> R.string.onb_go
                            3 -> R.string.onb_done
                            else -> R.string.onb_next
                        },
                    ),
                )
            }
        }
    }
}

/** 1. Привет: Дуди машет рукой. */
@Composable
private fun HelloPage() {
    var waving by remember { mutableStateOf(true) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        while (true) {
            delay(420)
            waving = !waving
        }
    }
    Spacer(Modifier.height(64.dp))
    Dudi(if (waving) DudiEmotion.WAVE else DudiEmotion.NORMAL, Modifier.size(200.dp))
    Spacer(Modifier.height(28.dp))
    Text("DudeBooru", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.onb_hello), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** 2. Что показывать: SFW / NSFW / Всё, «Мне есть 18» и цензура-спойлер. */
@Composable
private fun ContentPage(
    mode: ContentMode,
    adult: Boolean,
    censor: Boolean,
    onMode: (ContentMode) -> Unit,
    onAdult: (Boolean) -> Unit,
    onCensor: (Boolean) -> Unit,
) {
    PageTitle(stringResource(R.string.onb_content_title), stringResource(R.string.onb_content_text))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = listOf(ContentMode.SFW to R.string.mode_sfw, ContentMode.NSFW to R.string.mode_nsfw, ContentMode.ALL to R.string.mode_all)
        modes.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected = mode == value, onClick = { onMode(value) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) {
                Text(stringResource(label))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (mode != ContentMode.SFW) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAdult(!adult) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = adult, onCheckedChange = onAdult)
            Text(stringResource(R.string.onb_adult), style = MaterialTheme.typography.bodyLarge)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCensor(!censor) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.onb_censor), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.onb_censor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = censor, onCheckedChange = onCensor)
        }
    }
}

/** 3. Источники: какие папки показывать и в каком порядке — перетаскиванием за ручку. */
@Composable
private fun SourcesPage(order: MutableList<String>, hidden: MutableList<String>) {
    PageTitle(stringResource(R.string.onb_sources_title), stringResource(R.string.onb_sources_text))
    val rowHeight = 60.dp
    val density = LocalDensity.current
    val rowPx = with(density) { rowHeight.toPx() }
    val handlePx = with(density) { 72.dp.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val shown = order.filter { Sites.byId(it) != null }
    // Жест ловит весь список: строки переставляются под пальцем, а сам список стоит на месте.
    Column(
        Modifier.fillMaxWidth().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { start ->
                    val visible = order.filter { Sites.byId(it) != null }
                    dragging = if (start.x >= size.width - handlePx) visible.getOrNull((start.y / rowPx).toInt()) else null
                    dragOffset = 0f
                },
                onDragEnd = { dragging = null; dragOffset = 0f },
                onDragCancel = { dragging = null; dragOffset = 0f },
                onDrag = { change, amount ->
                    val id = dragging ?: return@detectDragGestures
                    change.consume()
                    dragOffset += amount.y
                    val visible = order.filter { Sites.byId(it) != null }
                    val pos = visible.indexOf(id)
                    val from = order.indexOf(id)
                    if (dragOffset > rowPx / 2 && pos < visible.lastIndex) {
                        order.add(order.indexOf(visible[pos + 1]), order.removeAt(from))
                        dragOffset -= rowPx
                    } else if (dragOffset < -rowPx / 2 && pos > 0) {
                        order.add(order.indexOf(visible[pos - 1]), order.removeAt(from))
                        dragOffset += rowPx
                    }
                },
            )
        },
    ) {
        shown.forEach { id ->
            androidx.compose.runtime.key(id) {
                val site = checkNotNull(Sites.byId(id))
                val isDragged = dragging == id
                val elevation by animateFloatAsState(if (isDragged) 8f else 0f, label = "drag")
                Box(Modifier.height(rowHeight).zIndexIf(isDragged)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = elevation.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight - 6.dp)
                            .offset { IntOffset(0, if (isDragged) dragOffset.roundToInt() else 0) }
                            .graphicsLayer { if (isDragged) { scaleX = 1.02f; scaleY = 1.02f } },
                    ) {
                        Row(Modifier.padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id !in hidden, onCheckedChange = { on -> if (on) hidden.remove(id) else hidden.add(id) })
                            Text(site.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(DudeIcons.Menu, stringResource(R.string.onb_drag), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.onb_sources_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Перетаскиваемая строка рисуется поверх соседей. */
private fun Modifier.zIndexIf(on: Boolean): Modifier = if (on) this.then(Modifier.zIndex(1f)) else this

/** 4. Вид: Monet / светлая / тёмная и иконка. */
@Composable
private fun LookPage(look: OnboardingChoice.Look, icon: AppIcon, onLook: (OnboardingChoice.Look) -> Unit, onIcon: (AppIcon) -> Unit) {
    PageTitle(stringResource(R.string.onb_look_title), null)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            OnboardingChoice.Look.MONET to R.string.onb_look_monet,
            OnboardingChoice.Look.LIGHT to R.string.theme_variant_light,
            OnboardingChoice.Look.DARK to R.string.theme_variant_dark,
        ).forEach { (value, label) ->
            val selected = look == value
            val (bg, fg) = when (value) {
                OnboardingChoice.Look.MONET -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                OnboardingChoice.Look.LIGHT -> androidx.compose.ui.graphics.Color(0xFFF7F8FB) to androidx.compose.ui.graphics.Color(0xFF2F6FDE)
                OnboardingChoice.Look.DARK -> androidx.compose.ui.graphics.Color(0xFF12131A) to androidx.compose.ui.graphics.Color(0xFF6F9CF2)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onLook(value) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(fg))
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.icon_picker_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    val choices = AppIcon.entries.filter { it.set == IconSet.DUDI } + AppIcon.NEUTRAL
    choices.chunked(5).forEach { rowIcons ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowIcons.forEach { candidate ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (candidate == icon) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onIcon(candidate) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconPreview(candidate, Modifier.size(48.dp))
                }
            }
            repeat(5 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(6.dp))
    }
    if (icon == AppIcon.NEUTRAL) {
        Text(
            stringResource(R.string.icon_neutral_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.onb_accounts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun PageTitle(title: String, text: String?) {
    Spacer(Modifier.height(32.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(24.dp))
    Spacer(Modifier.width(0.dp))
}
