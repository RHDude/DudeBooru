@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package app.dudebooru.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.TagInfo
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.theme.LocalTagColors
import kotlinx.coroutines.launch
import java.util.Locale

/** Поиск по тегам в текущем источнике. Выбранные теги — чипы, свободный текст — последним. */
@Composable
fun SearchScreen(vm: SearchViewModel, initial: String, onBack: () -> Unit, onSearch: (List<String>) -> Unit) {
    val chips = remember { mutableStateListOf<String>().apply { addAll(initial.split(' ').filter { it.isNotBlank() }) } }
    var text by rememberSaveable { mutableStateOf("") }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle(emptyList())
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val tagColors = LocalTagColors.current

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        scope.launch {
            val tags = (chips + vm.normalize(text)).distinct()
            if (tags.isEmpty()) return@launch
            vm.remember(tags.joinToString(" "))
            onSearch(tags)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) }
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    vm.onInput(it)
                },
                placeholder = { Text(stringResource(R.string.search_in, vm.site.name)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (text.isNotEmpty() || chips.isNotEmpty()) {
                IconButton(onClick = { text = ""; chips.clear(); vm.onInput("") }) { Icon(DudeIcons.Close, stringResource(R.string.clear)) }
            }
            IconButton(onClick = { submit() }) { Icon(DudeIcons.Search, stringResource(R.string.search), tint = MaterialTheme.colorScheme.primary) }
        }
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { chips.remove(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(DudeIcons.Close, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
        HorizontalDivider()

        LazyColumn(Modifier.fillMaxSize()) {
            if (text.isBlank()) {
                if (history.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.search_recent)) }
                }
                items(history, key = { "h:" + it.query }) { entry ->
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { vm.remember(entry.query); onSearch(entry.query.split(' ')) }, onLongClick = { menu = true })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (entry.pinned) DudeIcons.Star else DudeIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(entry.query, modifier = Modifier.weight(1f))
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (entry.pinned) R.string.search_unpin else R.string.search_pin)) },
                                onClick = { menu = false; vm.togglePin(entry) },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.search_forget)) }, onClick = { menu = false; vm.forget(entry) })
                        }
                    }
                }
            } else {
                items(suggestions, key = { "s:" + it.name }) { tag ->
                    SuggestionRow(tag, tagColors.of(tag.category)) {
                        val typed = text
                        text = ""
                        vm.onInput("")
                        scope.launch { vm.accept(typed, tag.name).forEach { if (it !in chips) chips += it } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionRow(tag: TagInfo, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(4.dp), color = color, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tag.name.replace('_', ' '), color = color, fontWeight = FontWeight.Medium)
            tag.antecedent?.takeIf { it != tag.name }?.let {
                Text("$it → ${tag.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        tag.postCount?.let { Text(formatCount(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** 146574 → «146k», 1244880 → «1.2M». */
fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> String.format(Locale.ROOT, "%.1fk", n / 1000.0)
    else -> n.toString()
}
