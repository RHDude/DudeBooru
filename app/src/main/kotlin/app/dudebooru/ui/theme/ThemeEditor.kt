@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.theme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.main.MainViewModel
import kotlinx.coroutines.launch

/**
 * Редактор темы с живым предпросмотром на макете главного экрана: три цвета для светлого и тёмного
 * варианта, фон ленты и меню, скругление, отступы сетки, плотность и шрифт.
 */
@Composable
fun ThemeEditorScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by vm.appTheme.collectAsStateWithLifecycle()
    val customName = stringResource(R.string.theme_custom)
    // Черновик переживает поворот экрана: храним JSON.
    var draftJson by rememberSaveable {
        val base = vm.editorDraft.value ?: if (current.id == "custom") current else current.copy(id = "custom", name = customName)
        vm.editorDraft.value = null
        mutableStateOf(BooruJson.encodeToString(AppTheme.serializer(), base))
    }
    val draft = remember(draftJson) { BooruJson.decodeFromString(AppTheme.serializer(), draftJson) }
    fun update(transform: (AppTheme) -> AppTheme) {
        draftJson = BooruJson.encodeToString(AppTheme.serializer(), transform(draft))
    }
    var editDark by rememberSaveable { mutableStateOf(vm.lastDark) }
    var picking by remember { mutableStateOf<ColorTarget?>(null) }
    val palette = if (editDark) draft.dark else draft.light
    fun updatePalette(transform: (ThemePalette) -> ThemePalette) = update { t ->
        if (editDark) t.copy(dark = transform(t.dark)) else t.copy(light = transform(t.light))
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = ThemeFiles.imageFromUri(context, uri) ?: return@launch
            update { it.copy(background = it.background.copy(kind = ThemeBackground.Kind.IMAGE, imagePath = path)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.themes_editor)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = {
                        vm.saveCustomTheme(draft)
                        onBack()
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Предпросмотр: вариант, который сейчас правим.
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DudeTheme(theme = draft, dark = editDark) {
                    MiniMainPreview(Modifier.height(230.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)))
                }
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(selected = !editDark, onClick = { editDark = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                            Icon(DudeIcons.Sun, stringResource(R.string.theme_variant_light), Modifier.size(18.dp))
                        }
                        SegmentedButton(selected = editDark, onClick = { editDark = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                            Icon(DudeIcons.Moon, stringResource(R.string.theme_variant_dark), Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (editDark) R.string.theme_variant_dark else R.string.theme_variant_light),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { name -> update { it.copy(name = name.take(40)) } },
                    label = { Text(stringResource(R.string.theme_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                Section(stringResource(R.string.theme_colors))
                if (monetAvailable) {
                    SwitchLine(stringResource(R.string.theme_monet), palette.monet) { on -> updatePalette { it.copy(monet = on) } }
                }
                ColorLine(stringResource(R.string.theme_accent), palette.accent, enabled = !palette.monet) { picking = ColorTarget.ACCENT }
                ColorLine(stringResource(R.string.theme_background), palette.background, enabled = !palette.monet) { picking = ColorTarget.BACKGROUND }
                ColorLine(stringResource(R.string.theme_surface), palette.surface, enabled = !palette.monet) { picking = ColorTarget.SURFACE }

                Section(stringResource(R.string.theme_backdrop))
                val bg = draft.background
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    val kinds = listOf(
                        ThemeBackground.Kind.NONE to R.string.theme_bg_none,
                        ThemeBackground.Kind.COLOR to R.string.theme_bg_color,
                        ThemeBackground.Kind.GRADIENT to R.string.theme_bg_gradient,
                        ThemeBackground.Kind.IMAGE to R.string.theme_bg_image,
                    )
                    kinds.forEachIndexed { i, (kind, label) ->
                        SegmentedButton(
                            selected = bg.kind == kind,
                            onClick = {
                                update { t ->
                                    val seeded = if (t.background.color1 == 0L) {
                                        t.background.copy(color1 = palette.accent, color2 = palette.background)
                                    } else {
                                        t.background
                                    }
                                    t.copy(background = seeded.copy(kind = kind))
                                }
                                if (kind == ThemeBackground.Kind.IMAGE && bg.imagePath == null) {
                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                when (bg.kind) {
                    ThemeBackground.Kind.COLOR -> ColorLine(stringResource(R.string.theme_bg_color), bg.color1) { picking = ColorTarget.BG1 }
                    ThemeBackground.Kind.GRADIENT -> {
                        ColorLine(stringResource(R.string.theme_bg_top), bg.color1) { picking = ColorTarget.BG1 }
                        ColorLine(stringResource(R.string.theme_bg_bottom), bg.color2) { picking = ColorTarget.BG2 }
                    }
                    ThemeBackground.Kind.IMAGE -> {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text(stringResource(R.string.theme_bg_gallery))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.theme_bg_from_post), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SliderLine(stringResource(R.string.theme_bg_blur), bg.blur, 0f..1f) { v -> update { it.copy(background = it.background.copy(blur = v)) } }
                    }
                    ThemeBackground.Kind.NONE -> Unit
                }
                if (bg.kind != ThemeBackground.Kind.NONE) {
                    SliderLine(stringResource(R.string.theme_bg_dim), bg.dim, 0f..0.9f) { v -> update { it.copy(background = it.background.copy(dim = v)) } }
                }

                Section(stringResource(R.string.theme_shape))
                SliderLine(stringResource(R.string.theme_corners, draft.cornerRadius), draft.cornerRadius.toFloat(), 0f..24f, steps = 11) { v ->
                    update { it.copy(cornerRadius = v.toInt()) }
                }
                SliderLine(stringResource(R.string.theme_grid_spacing, draft.gridSpacing), draft.gridSpacing.toFloat(), 0f..8f, steps = 7) { v ->
                    update { it.copy(gridSpacing = v.toInt()) }
                }
                Text(stringResource(R.string.theme_density), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                Segments(
                    listOf(UiDensity.COMPACT to R.string.theme_density_compact, UiDensity.NORMAL to R.string.theme_density_normal, UiDensity.COMFY to R.string.theme_density_comfy),
                    draft.density,
                ) { d -> update { it.copy(density = d) } }
                Text(stringResource(R.string.theme_font), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                Segments(
                    listOf(UiFont.DEFAULT to R.string.theme_font_default, UiFont.SERIF to R.string.theme_font_serif, UiFont.MONO to R.string.theme_font_mono),
                    draft.font,
                ) { f -> update { it.copy(font = f) } }
                SwitchLine(stringResource(R.string.theme_lace), draft.pattern == ThemePattern.LACE) { on ->
                    update { it.copy(pattern = if (on) ThemePattern.LACE else ThemePattern.NONE) }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.saveCustomTheme(draft)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                ) { Text(stringResource(R.string.theme_save_apply)) }
            }
        }
    }

    picking?.let { target ->
        val initial = when (target) {
            ColorTarget.ACCENT -> palette.accent
            ColorTarget.BACKGROUND -> palette.background
            ColorTarget.SURFACE -> palette.surface
            ColorTarget.BG1 -> draft.background.color1
            ColorTarget.BG2 -> draft.background.color2
        }
        ColorPickerSheet(initial, onDismiss = { picking = null }) { color ->
            when (target) {
                ColorTarget.ACCENT -> updatePalette { it.copy(accent = color, monet = false) }
                ColorTarget.BACKGROUND -> updatePalette { it.copy(background = color, monet = false) }
                ColorTarget.SURFACE -> updatePalette { it.copy(surface = color, monet = false) }
                ColorTarget.BG1 -> update { it.copy(background = it.background.copy(color1 = color)) }
                ColorTarget.BG2 -> update { it.copy(background = it.background.copy(color2 = color)) }
            }
        }
    }
}

private enum class ColorTarget { ACCENT, BACKGROUND, SURFACE, BG1, BG2 }

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun ColorLine(label: String, color: Long, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hex(color), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderLine(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun <T> Segments(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected = selected == value, onClick = { onSelect(value) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

private fun hex(color: Long) = "#%06X".format(color and 0xFFFFFF)

private val Swatches = listOf(
    0xFF2F6FDE, 0xFF6F9CF2, 0xFF139C94, 0xFF39C5BB, 0xFF1E8A4C, 0xFF5FD08D, 0xFFD9822B, 0xFFF2A65A,
    0xFFC0392B, 0xFFE5484D, 0xFFD9577E, 0xFFFF9EBB, 0xFF8E44AD, 0xFFC792EA, 0xFF5B5FC7, 0xFF9B6FD6,
    0xFFFFFFFF, 0xFFF7F8FB, 0xFFFFF4F7, 0xFFF2F7F7, 0xFF1E2126, 0xFF12131A, 0xFF0D0D0D, 0xFF000000,
)

/** Цвет: готовые образцы, ползунки тона, насыщенности и яркости, HEX. */
@Composable
private fun ColorPickerSheet(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)
    var hexText by remember(color) { mutableStateOf(hex(color.toLong()).drop(1)) }

    fun set(c: Long) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toInt(), out)
        hue = out[0]; sat = out[1]; value = out[2]
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        if (hexText.length == 6) hexText.toLongOrNull(16)?.let { set(0xFF000000 or it) }
                    },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatches.forEach { swatch ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(swatch))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { set(swatch) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GradientSlider(hue, 0f..360f, (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }) { hue = it }
            GradientSlider(sat, 0f..1f, listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))) { sat = it }
            GradientSlider(value, 0f..1f, listOf(Color.Black, Color.hsv(hue, sat, 1f))) { value = it }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onPick(color.toLong()); onDismiss() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun GradientSlider(value: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().drawBehind {
            val h = 10.dp.toPx()
            drawRoundRect(
                Brush.horizontalGradient(colors),
                topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - h) / 2),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
            )
        },
    )
}
