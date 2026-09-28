@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.filter

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.TagInfo
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.filter.SiteCheck
import app.dudebooru.ui.common.errorText
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.main.MainViewModel
import app.dudebooru.ui.theme.LocalTagColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Негативные теги: один список на все источники (или тег только для одного).
 * Комбинация `tag_a tag_b` скрывает пост, только если на нём оба тега.
 */
@Composable
fun NegativeTagsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by vm.c.negative.entries.collectAsStateWithLifecycle()
    val selectedSite by vm.selected.collectAsStateWithLifecycle()
    val dictionarySite = vm.c.registry.site(selectedSite ?: "") ?: vm.c.registry.sites.first()
    val sites = vm.c.registry.sites
    val colors = LocalTagColors.current

    var text by rememberSaveable { mutableStateOf("") }
    var scopeSite by remember { mutableStateOf<SiteConfig?>(null) }
    var suggestions by remember { mutableStateOf<List<TagInfo>>(emptyList()) }
    var checks by remember { mutableStateOf<Pair<String, List<SiteCheck>>?>(null) }
    var suggestJob by remember { mutableStateOf<Job?>(null) }
    var scopeMenu by remember { mutableStateOf(false) }

    fun add(expression: String, site: SiteConfig?) {
        scope.launch {
            val saved = vm.c.negative.add(expression, site, dictionarySite)
            text = ""
            suggestions = emptyList()
            if (' ' !in saved && ':' !in saved && site == null) {
                checks = saved to vm.c.negative.checkAcrossSites(saved, sites.filter { !it.safeOnly && it.supportsLogin })
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_negative_tags)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.negative_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { value ->
                                text = value
                                suggestJob?.cancel()
                                val word = value.substringAfterLast(' ').removePrefix("-")
                                suggestJob = scope.launch {
                                    if (word.length < 2) {
                                        suggestions = emptyList()
                                        return@launch
                                    }
                                    suggestions = vm.c.tags.localSuggestions(dictionarySite, word, 8)
                                    delay(250)
                                    runCatching { vm.c.tags.remoteSuggestions(dictionarySite, word, 8) }.getOrNull()?.let { if (it.isNotEmpty()) suggestions = it }
                                }
                            },
                            placeholder = { Text(stringResource(R.string.negative_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) add(text, scopeSite) }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { add(text, scopeSite) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.negative_add)) }
                    }
                    Box {
                        AssistChip(
                            onClick = { scopeMenu = true },
                            label = { Text(scopeSite?.name ?: stringResource(R.string.negative_all_sites)) },
                            trailingIcon = { Icon(DudeIcons.ChevronDown, null, Modifier.size(16.dp)) },
                        )
                        DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.negative_all_sites)) }, onClick = { scopeSite = null; scopeMenu = false })
                            sites.forEach { site -> DropdownMenuItem(text = { Text(site.name) }, onClick = { scopeSite = site; scopeMenu = false }) }
                        }
                    }
                    suggestions.forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val head = text.substringBeforeLast(' ', "")
                                val sign = if (text.substringAfterLast(' ').startsWith("-")) "-" else ""
                                text = (if (head.isEmpty()) "" else "$head ") + sign + tag.name
                                suggestions = emptyList()
                            }.padding(vertical = 8.dp),
                        ) {
                            Text(tag.name, color = colors.of(tag.category), modifier = Modifier.weight(1f))
                            tag.postCount?.let { Text(app.dudebooru.ui.search.formatCount(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    checks?.let { (tag, list) ->
                        if (list.any { !it.found }) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.negative_check_title, tag), style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { check ->
                                    val label = when {
                                        check.found -> "✓ ${check.site.name}"
                                        check.suggestion != null -> "✗ ${check.site.name}: ${check.suggestion}"
                                        else -> "✗ ${check.site.name}"
                                    }
                                    AssistChip(
                                        onClick = { check.suggestion?.let { add(it, check.site) } },
                                        label = { Text(label) },
                                        enabled = !check.found,
                                    )
                                }
                            }
                            Text(stringResource(R.string.negative_check_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                val added = vm.c.negative.importFromDanbooru()
                                val message = if (added < 0) context.getString(R.string.negative_import_login) else context.getString(R.string.negative_import_done, added)
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, context.errorText(e), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text(stringResource(R.string.negative_import)) }
                }
                HorizontalDivider()
            }
            if (entries.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("(￣ー￣)", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.negative_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(entries, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.expression, fontWeight = FontWeight.Medium)
                        val scopeLabel = entry.site?.let { id -> sites.firstOrNull { it.id == id }?.name ?: id } ?: stringResource(R.string.negative_all_sites)
                        Text(scopeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { vm.c.negative.remove(entry.id) } }) {
                        Icon(DudeIcons.Close, stringResource(R.string.negative_remove))
                    }
                }
            }
        }
    }
}

/** «скрыто 12»: что и по какому тегу скрыто; тег можно вернуть. */
@Composable
fun HiddenSheetContent(hidden: Map<String, Int>, onRestore: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text(stringResource(R.string.hidden_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        hidden.entries.sortedByDescending { it.value }.forEach { (expression, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(expression, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.hidden_posts, count, count), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onRestore(expression) }) { Text(stringResource(R.string.hidden_restore)) }
            }
        }
    }
}
