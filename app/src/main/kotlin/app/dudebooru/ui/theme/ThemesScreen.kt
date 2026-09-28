@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.theme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.data.settings.ThemeMode
import app.dudebooru.ui.face.AppIcon
import app.dudebooru.ui.face.IconManager
import app.dudebooru.ui.face.IconPreview
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.main.MainViewModel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Темы, как в Telegram: пресеты со светлым и тёмным вариантом, редактор, обмен файлом или кодом,
 * автоночной режим и выбор иконки.
 */
@Composable
fun ThemesScreen(vm: MainViewModel, onBack: () -> Unit, onEditor: () -> Unit, onIconPicker: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by vm.appTheme.collectAsStateWithLifecycle()
    val custom by vm.customTheme.collectAsStateWithLifecycle()
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val schedule by vm.nightSchedule.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    var codeDialog by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) } // true — начало ночи, false — конец

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importThemeFile(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_themes)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Живой предпросмотр текущей темы.
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                MiniMainPreview(Modifier.height(300.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)))
            }

            SectionTitle(stringResource(R.string.themes_presets))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val list = ThemePresets.all + listOfNotNull(custom)
                items(list, key = { it.id }) { theme ->
                    PresetCard(
                        theme = theme,
                        dark = dark,
                        selected = theme.id == current.id,
                        onClick = { vm.selectTheme(theme.id) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEditor, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.themes_editor)) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { importMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.themes_import)) }
                    DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.themes_from_file)) }, onClick = {
                            importMenu = false
                            openFile.launch(arrayOf("*/*"))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.themes_from_code)) }, onClick = {
                            importMenu = false
                            codeDialog = true
                        })
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { exportMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.themes_export)) }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.themes_as_file)) }, onClick = {
                            exportMenu = false
                            scope.launch {
                                val uri = ThemeFiles.exportFile(context, current)
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/octet-stream")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.themes_export)))
                            }
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.themes_as_code)) }, onClick = {
                            exportMenu = false
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("theme", ThemeCodec.toCode(current)))
                            Toast.makeText(context, context.getString(R.string.themes_code_copied), Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.themes_night))
            val now = remember { ZonedDateTime.now() }
            val sun = remember(now) { NightSchedule.sunTimes(now.toLocalDate(), now.zone) }
            listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_mode_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_mode_light),
                ThemeMode.DARK to stringResource(R.string.theme_mode_dark),
                ThemeMode.SCHEDULE to stringResource(R.string.theme_mode_schedule),
                ThemeMode.SUNSET to stringResource(R.string.theme_mode_sunset),
            ).forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = mode == value, onClick = { vm.setThemeMode(value) }).padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == value, onClick = { vm.setThemeMode(value) })
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        if (value == ThemeMode.SUNSET) {
                            Text(
                                stringResource(R.string.theme_sun_times, hhmm(sun.second), hhmm(sun.first)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (mode == ThemeMode.SCHEDULE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.theme_night_from, hhmm(schedule.first)))
                    }
                    OutlinedButton(onClick = { pickTime = false }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.theme_night_to, hhmm(schedule.second)))
                    }
                }
            }
            if (mode == ThemeMode.SCHEDULE || mode == ThemeMode.SUNSET) {
                Text(
                    stringResource(R.string.theme_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val icon = remember { IconManager.current(context) }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onIconPicker).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPreview(icon, Modifier.size(44.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.icon_picker_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(icon.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (codeDialog) {
        var code by remember { mutableStateOf("") }
        var bad by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { codeDialog = false },
            title = { Text(stringResource(R.string.themes_from_code)) },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; bad = false },
                    placeholder = { Text("DB1:…") },
                    isError = bad,
                    supportingText = if (bad) ({ Text(stringResource(R.string.themes_bad_code)) }) else null,
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val theme = ThemeCodec.fromCode(code)
                    if (theme == null) bad = true else {
                        codeDialog = false
                        vm.offerTheme(theme)
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { codeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pickTime?.let { start ->
        val minutes = if (start) schedule.first else schedule.second
        val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val value = state.hour * 60 + state.minute
                    if (start) vm.setNightSchedule(value, schedule.second) else vm.setNightSchedule(schedule.first, value)
                    pickTime = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Карточка пресета в его собственных цветах (тот же вариант, светлый или тёмный, что сейчас). */
@Composable
fun PresetCard(theme: AppTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        DudeTheme(theme = theme, dark = dark) {
            val scheme = MaterialTheme.colorScheme
            Box(
                Modifier
                    .size(width = 88.dp, height = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        if (selected) BorderStroke(2.5.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = onClick),
            ) {
                ThemeBackdrop(theme.background, Modifier.fillMaxSize())
                Column(Modifier.padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(scheme.surfaceContainerHigh))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(width = 26.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.secondaryContainer))
                        Box(Modifier.size(width = 18.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.surfaceContainerHighest))
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape((theme.cornerRadius / 2).dp)).background(scheme.surfaceContainer))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.secondary))
                    }
                }
                if (theme.pattern == ThemePattern.LACE) app.dudebooru.ui.face.LaceEdge(Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomCenter))
                if (selected) {
                    Surface(shape = CircleShape, color = scheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)) {
                        Icon(DudeIcons.Check, null, tint = scheme.onPrimary, modifier = Modifier.padding(2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (theme.id == "custom") theme.name.ifBlank { stringResource(R.string.theme_custom) } else presetName(theme),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun presetName(theme: AppTheme): String = when (theme.id) {
    "classic" -> stringResource(R.string.theme_classic)
    "teto" -> stringResource(R.string.theme_teto)
    else -> theme.name
}

/** Файл или код темы: сначала предпросмотр, потом «Применить». */
@Composable
fun ThemeImportDialog(theme: AppTheme, onApply: () -> Unit, onDismiss: () -> Unit) {
    val dark = LocalDarkTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.themes_apply_title, theme.name.ifBlank { stringResource(R.string.theme_custom) })) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DudeTheme(theme = theme, dark = dark) {
                    MiniMainPreview(Modifier.height(260.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.themes_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal val monetAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
