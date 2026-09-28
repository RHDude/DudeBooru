@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.ui.common.errorText
import app.dudebooru.ui.face.EmptyState
import app.dudebooru.ui.face.Kaomoji
import app.dudebooru.ui.face.RunnerGame
import app.dudebooru.ui.face.rememberKaomoji
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Что нужно пустым экранам и ошибкам ленты от остального приложения. */
interface FeedEnvironment {
    val online: StateFlow<Boolean>
    val gameRecord: StateFlow<Int>
    fun saveRecord(score: Int)
    fun openNegativeTags()
    fun openSettings()
    fun openSite(site: SiteConfig)
    suspend fun similarTags(site: SiteConfig, tag: String): List<String>
    fun search(site: SiteConfig, tags: List<String>)
}

val LocalFeedEnv = staticCompositionLocalOf<FeedEnvironment?> { null }

/** Лента пуста: всё отфильтровано, поиск без результатов или просто пусто. */
@Composable
internal fun FeedEmpty(state: FeedState, controller: FeedController, actions: PostActions, emptyContent: (@Composable () -> Unit)?) {
    val env = LocalFeedEnv.current
    when {
        state.hiddenCount > 0 -> {
            var open by remember { mutableStateOf(false) }
            EmptyState(
                kaomoji = rememberKaomoji(Kaomoji.SAD),
                title = stringResource(R.string.filtered_title),
                text = stringResource(R.string.filtered_text),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.filtered_what, state.hiddenCount)) }
                    Button(onClick = { env?.openNegativeTags() }) { Text(stringResource(R.string.drawer_negative_tags)) }
                }
            }
            if (open) {
                ModalBottomSheet(onDismissRequest = { open = false }) {
                    app.dudebooru.ui.filter.HiddenSheetContent(state.hidden) { expression ->
                        actions.restoreTag(expression)
                        open = false
                        controller.refresh()
                    }
                }
            }
        }
        emptyContent != null -> emptyContent()
        controller.tags.isNotEmpty() && !controller.isLocal -> SearchEmpty(controller)
        else -> EmptyState(rememberKaomoji(Kaomoji.CONFUSED), stringResource(R.string.feed_empty))
    }
}

/** «По запросу «hatsune_mikku» ничего. Может, тег пишется иначе?» и похожие теги из словаря. */
@Composable
private fun SearchEmpty(controller: FeedController) {
    val env = LocalFeedEnv.current
    val query = controller.tags.joinToString(" ")
    val target = controller.tags.lastOrNull { !it.contains(':') && !it.startsWith("-") }
    val similar by produceState(emptyList<String>(), target) {
        value = if (target != null && env != null) runCatching { env.similarTags(controller.site, target) }.getOrDefault(emptyList()) else emptyList()
    }
    EmptyState(rememberKaomoji(Kaomoji.CONFUSED), stringResource(R.string.search_nothing, query)) {
        if (similar.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                similar.forEach { tag ->
                    AssistChip(
                        onClick = { env?.search(controller.site, controller.tags.map { if (it == target) tag else it }) },
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

/** Виды ошибок, при которых сайт недоступен целиком: объяснение, а под ним игра. */
private fun Throwable.siteDown(): Boolean =
    this is BooruException.NotResponding || this is BooruException.ServerError ||
        this is BooruException.Forbidden || this is BooruException.NotJson

/**
 * Ошибка при пустой ленте. Без сети — игра; сайт недоступен — объяснение и игра;
 * «притормозить» — только таймер. Ошибка касается только своей папки.
 */
@Composable
internal fun FeedErrorScreen(error: Throwable, controller: FeedController) {
    val context = LocalContext.current
    val env = LocalFeedEnv.current
    val online by (env?.online ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsStateWithLifecycle()
    val record by (env?.gameRecord ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsStateWithLifecycle()
    // Игра не обрывается, когда сеть вернулась: сверху плашка «Сеть вернулась».
    var wasOffline by remember { mutableStateOf(!online) }
    LaunchedEffect(online) { if (!online) wasOffline = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        when {
            wasOffline -> {
                AnimatedVisibility(visible = online) { NetworkBackBanner { controller.retry() } }
                Text(
                    stringResource(R.string.offline_text),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                )
                RunnerGame(record, onRecord = { env?.saveRecord(it) }, modifier = Modifier.padding(top = 12.dp))
            }
            error is BooruException.TooManyRequests -> {
                val left = countdown(error, error.retryAfterSeconds.toInt())
                EmptyState(
                    rememberKaomoji(Kaomoji.BORED),
                    stringResource(R.string.error_too_many, controller.site.name, left),
                )
            }
            error.siteDown() -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rememberKaomoji(Kaomoji.SAD), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(context.errorText(error), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    AutoRetry(error, controller)
                    Spacer(Modifier.height(12.dp))
                    ErrorButtons(error, controller)
                }
                RunnerGame(record, onRecord = { env?.saveRecord(it) })
            }
            else -> EmptyState(rememberKaomoji(Kaomoji.SAD), context.errorText(error)) { ErrorButtons(error, controller) }
        }
    }
}

/** Повтор сам: «не отвечает» — через 10 с, 30 с, 1 мин; ошибка сервера — с нарастающей паузой. */
@Composable
private fun AutoRetry(error: Throwable, controller: FeedController) {
    val pause = when (error) {
        is BooruException.NotResponding -> listOf(10, 30, 60).getOrNull(controller.autoRetries)
        is BooruException.ServerError -> listOf(15, 30, 60, 120, 300).getOrElse(controller.autoRetries) { 300 }
        else -> null
    } ?: return
    val left = countdown(error, pause)
    LaunchedEffect(error, left) {
        if (left == 0) {
            controller.autoRetries++
            controller.retry()
        }
    }
    Text(
        stringResource(R.string.error_retry_in, left),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun countdown(key: Any, seconds: Int): Int {
    var left by remember(key) { mutableIntStateOf(seconds) }
    LaunchedEffect(key) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    return left
}

/** Кнопки по виду ошибки: «Повторить» и «Прокси», «Настройки сети», «Войти заново», «Открыть сайт». */
@Composable
private fun ErrorButtons(error: Throwable, controller: FeedController) {
    val env = LocalFeedEnv.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (error) {
            is BooruException.Forbidden -> Button(onClick = { env?.openSettings() }) { Text(stringResource(R.string.error_btn_network)) }
            is BooruException.Unauthorized -> Button(onClick = { env?.openSettings() }) { Text(stringResource(R.string.error_btn_relogin)) }
            is BooruException.NotJson -> Button(onClick = { env?.openSite(controller.site) }) { Text(stringResource(R.string.error_btn_open_site)) }
            else -> Unit
        }
        OutlinedButton(onClick = controller::retry) { Text(stringResource(R.string.retry)) }
        if (error is BooruException.NotResponding) {
            OutlinedButton(onClick = { env?.openSettings() }) { Text(stringResource(R.string.error_btn_proxy)) }
        }
    }
}

@Composable
private fun NetworkBackBanner(onFeed: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.network_back), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onFeed) { Text(stringResource(R.string.network_back_feed)) }
        }
    }
}

/** Лента из кэша: сети нет или сайт не ответил. */
@Composable
internal fun CacheBanner(state: FeedState, controller: FeedController) {
    val env = LocalFeedEnv.current
    val online by (env?.online ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsStateWithLifecycle()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (online) stringResource(R.string.cache_site_down, controller.site.name) else stringResource(R.string.cache_offline),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = controller::retry) { Text(stringResource(if (online) R.string.network_back_feed else R.string.retry)) }
        }
    }
}
