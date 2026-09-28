@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.dudebooru.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dudebooru.DudeApp
import app.dudebooru.R
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.data.settings.ThemeMode
import app.dudebooru.ui.accounts.AccountsViewModel
import app.dudebooru.ui.feed.Collections
import app.dudebooru.ui.feed.CensorState
import app.dudebooru.ui.feed.LocalCensor
import app.dudebooru.ui.collections.ArtistsScreen
import app.dudebooru.ui.collections.DownloadsScreen
import app.dudebooru.ui.collections.HistoryScreen
import app.dudebooru.ui.collections.ProfileScreen
import app.dudebooru.ui.collections.SavedScreen
import app.dudebooru.ui.feed.LocalCollections
import app.dudebooru.ui.feed.LocalDownloaded
import app.dudebooru.ui.feed.LocalFeedPrefs
import app.dudebooru.ui.feed.LocalPostActions
import app.dudebooru.ui.filter.NegativeTagsScreen
import app.dudebooru.ui.main.ArtistScreen
import app.dudebooru.ui.main.MainShell
import app.dudebooru.ui.main.MainViewModel
import app.dudebooru.ui.main.ResultsScreen
import app.dudebooru.ui.main.Route
import app.dudebooru.ui.main.SoonScreen
import app.dudebooru.ui.search.SearchScreen
import app.dudebooru.ui.search.SearchViewModel
import app.dudebooru.ui.theme.DudeTheme
import app.dudebooru.ui.theme.ThemeImportDialog
import app.dudebooru.ui.theme.ThemeReveal
import app.dudebooru.ui.theme.ThemeRevealHost
import app.dudebooru.ui.face.OnboardingScreen
import app.dudebooru.ui.theme.LocalTagColors
import app.dudebooru.ui.viewer.ViewerScreen
import kotlin.system.exitProcess

/** Файл темы из чужого приложения ждёт, пока соберётся интерфейс. */
private val incomingTheme = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

/** Куда вести по тапу на уведомление. */
private sealed interface OpenRequest {
    data class Artist(val site: String, val name: String) : OpenRequest
    data object Artists : OpenRequest
    data object Update : OpenRequest
}

private val incomingOpen = kotlinx.coroutines.flow.MutableStateFlow<OpenRequest?>(null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as DudeApp
        if (savedInstanceState == null) {
            takeThemeIntent(intent)
            takeOpenIntent(intent)
        }
        setContent {
            val vm = viewModel { MainViewModel(app, app.container) }
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val theme by vm.appTheme.collectAsStateWithLifecycle()
            val instant by vm.instantDark.collectAsStateWithLifecycle()
            val override by vm.nightOverride.collectAsStateWithLifecycle()
            val schedule by vm.nightSchedule.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            // Расписание и закат: пересчёт раз в минуту.
            val minute by produceState(System.currentTimeMillis() / 60_000) {
                while (true) {
                    kotlinx.coroutines.delay(60_000)
                    value = System.currentTimeMillis() / 60_000
                }
            }
            val dark = remember(themeMode, instant, override, schedule, systemDark, minute) {
                vm.effectiveDark(systemDark, java.time.ZonedDateTime.now())
            }
            val reveal = remember { ThemeReveal() }
            // Холодный старт (сплэш) — в теме приложения, а не системы: Android 12+ запоминает её сам.
            LaunchedEffect(themeMode, dark) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val mode = when {
                        themeMode == ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                        dark -> android.app.UiModeManager.MODE_NIGHT_YES
                        else -> android.app.UiModeManager.MODE_NIGHT_NO
                    }
                    runCatching { getSystemService(android.app.UiModeManager::class.java)?.setApplicationNightMode(mode) }
                }
            }
            LaunchedEffect(dark) {
                val transparent = AndroidColor.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ThemeRevealHost(reveal) {
                DudeTheme(theme = theme, dark = dark) {
                    vm.lastDark = dark
                    val incoming by incomingTheme.collectAsStateWithLifecycle()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(incoming) {
                        incoming?.let {
                            vm.importThemeFile(context, it)
                            incomingTheme.value = null
                        }
                    }
                    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
                    when (onboarded) {
                        null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                        false -> {
                            val folders by vm.c.settings.folders.collectAsStateWithLifecycle(null)
                            folders?.let { f ->
                                OnboardingScreen(f.order, f.hidden) { choice -> vm.applyOnboarding(context, choice) }
                            }
                        }
                        true -> DudeRoot(vm, dark, onCloseApp = ::closeApp)
                    }
                    val pending by vm.pendingTheme.collectAsStateWithLifecycle()
                    pending?.let { t ->
                        ThemeImportDialog(t, onApply = vm::applyPendingTheme, onDismiss = { vm.pendingTheme.value = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        takeThemeIntent(intent)
        takeOpenIntent(intent)
    }

    /** Тап по уведомлению: новые работы художника, сводка по художникам или новая версия. */
    private fun takeOpenIntent(intent: android.content.Intent?) {
        incomingOpen.value = when (intent?.action) {
            app.dudebooru.notify.Notifications.ACTION_OPEN_ARTIST -> {
                val site = intent.getStringExtra(app.dudebooru.notify.Notifications.EXTRA_SITE)
                val name = intent.getStringExtra(app.dudebooru.notify.Notifications.EXTRA_ARTIST)
                if (site != null && name != null) OpenRequest.Artist(site, name) else OpenRequest.Artists
            }
            app.dudebooru.notify.Notifications.ACTION_OPEN_ARTISTS -> OpenRequest.Artists
            app.dudebooru.notify.Notifications.ACTION_SHOW_UPDATE -> OpenRequest.Update
            else -> return
        }
    }

    /** Файл темы, открытый из Telegram или файлового менеджера, сразу предлагает применить тему. */
    private fun takeThemeIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW && intent?.action != android.content.Intent.ACTION_SEND) return
        val uri = intent.data ?: androidx.core.content.IntentCompat.getParcelableExtra(intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java) ?: return
        incomingTheme.value = uri
    }

    /** «Закрыть приложение» полностью выгружает его; блокировка (когда появится) спросит PIN заново. */
    private fun closeApp() {
        finishAndRemoveTask()
        Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 300)
    }
}

@Composable
private fun DudeRoot(vm: MainViewModel, dark: Boolean, onCloseApp: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    val actions = remember {
        DudeActions(context, vm, scope) {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val downloaded by vm.downloadedMd5.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val feedPrefs by vm.feedPrefs.collectAsStateWithLifecycle()
    val showHidden by vm.showHiddenCount.collectAsStateWithLifecycle()
    val censorOn by vm.censor.collectAsStateWithLifecycle()
    val censorPrefs by vm.censorPrefs.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val activity = context as? ComponentActivity

    CompositionLocalProvider(
        LocalCollections provides Collections(liked, saved),
        LocalPostActions provides actions,
        LocalFeedPrefs provides feedPrefs.copy(showHiddenCount = showHidden),
        LocalCensor provides CensorState(censorOn, mode, censorPrefs, revealed),
        LocalDownloaded provides downloaded,
        app.dudebooru.ui.feed.LocalFeedEnv provides actions,
        app.dudebooru.ui.common.LocalSharedKey provides vm.sharedKey,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            BackHandler { if (!vm.back()) activity?.finish() }
            // Переходы между экранами; из ленты в пост картинка перелетает (общий элемент).
            val reduced = app.dudebooru.ui.face.rememberReducedMotion()
            androidx.compose.animation.SharedTransitionLayout {
                androidx.compose.animation.AnimatedContent(
                    targetState = vm.stack.last(),
                    transitionSpec = {
                        if (reduced) {
                            androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        } else {
                            // Новый экран проявляется поверх, старый гаснет, когда его уже почти не видно:
                            // без «провала» через фон, пока оба экрана полупрозрачные.
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) togetherWith
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120, delayMillis = 160))
                        }
                    },
                    label = "routes",
                ) { route ->
                    CompositionLocalProvider(
                        app.dudebooru.ui.common.LocalSharedScope provides if (reduced) null else this@SharedTransitionLayout,
                        app.dudebooru.ui.common.LocalRouteScope provides this@AnimatedContent,
                    ) {
                        when (route) {
                        Route.Main -> MainShell(vm, actions, dark, onCloseApp)
                        is Route.Search -> {
                            val site = vm.c.registry.site(route.siteId) ?: return@CompositionLocalProvider
                            val searchVm = viewModel(key = "search:${route.siteId}") { SearchViewModel(vm.c, site) }
                            SearchScreen(
                                vm = searchVm,
                                initial = route.initial,
                                onBack = { vm.back() },
                                onSearch = { tags ->
                                    vm.back()
                                    vm.openSearchResults(route.siteId, tags)
                                },
                            )
                        }
                        is Route.Results -> {
                            val controller = vm.controller(route.controllerId) ?: return@CompositionLocalProvider
                            ResultsScreen(
                                controller = controller,
                                actions = actions,
                                onBack = { vm.back() },
                                onEditQuery = { vm.navigate(Route.Search(controller.site.id, controller.tags.joinToString(" "))) },
                            )
                        }
                        is Route.Viewer -> {
                            val controller = vm.controller(route.controllerId) ?: return@CompositionLocalProvider
                            ViewerScreen(
                                controller = controller,
                                startKey = route.startKey,
                                actions = actions,
                                onClose = { vm.back() },
                                onSearchTag = { post, tag, add ->
                                    if (add) {
                                        vm.navigate(Route.Search(post.site, (controller.tags + tag).distinct().joinToString(" ")))
                                    } else {
                                        vm.openSearchResults(post.site, listOf(tag))
                                    }
                                },
                            )
                        }
                        is Route.Artist -> {
                            val controller = vm.controller(route.controllerId) ?: return@CompositionLocalProvider
                            ArtistScreen(vm, controller, route.name, actions, onBack = { vm.back() })
                        }
                        is Route.Settings -> {
                            // Одна модель на все разделы: формы входа и черновики не теряются при переходах.
                            val accountsVm = viewModel { AccountsViewModel(context.applicationContext as android.app.Application, vm.c) }
                            app.dudebooru.ui.settings.SettingsScreen(
                                vm = accountsVm,
                                page = route.page,
                                onBack = { vm.back() },
                                onOpen = vm::navigate,
                                mode = mode,
                                onMode = vm::setMode,
                                updates = vm.updates,
                                onAskNotifications = actions::askNotifications,
                            )
                        }
                        Route.NegativeTags -> NegativeTagsScreen(vm, onBack = { vm.back() })
                        is Route.Soon -> SoonScreen(route.title, route.step, onBack = { vm.back() })
                        Route.Saved -> SavedScreen(vm, actions, onBack = { vm.back() })
                        Route.Profile -> ProfileScreen(vm, actions, onBack = { vm.back() }, onEdit = { vm.navigate(Route.Settings(app.dudebooru.ui.main.SettingsPage.PROFILE)) })
                        Route.History -> HistoryScreen(vm, actions, onBack = { vm.back() })
                        Route.Artists -> ArtistsScreen(vm, onBack = { vm.back() })
                        Route.Downloads -> DownloadsScreen(vm, onBack = { vm.back() })
                        Route.Recs -> app.dudebooru.ui.rec.RecsScreen(vm, actions, onBack = { vm.back() })
                        Route.IconPicker -> app.dudebooru.ui.face.IconPickerScreen(onBack = { vm.back() })
                        Route.Themes -> app.dudebooru.ui.theme.ThemesScreen(
                            vm,
                            onBack = { vm.back() },
                            onEditor = { vm.navigate(Route.ThemeEditor) },
                            onIconPicker = { vm.navigate(Route.IconPicker) },
                        )
                        Route.ThemeEditor -> app.dudebooru.ui.theme.ThemeEditorScreen(vm, onBack = { vm.back() })
                        Route.Game -> app.dudebooru.ui.face.GameScreen(vm, onBack = { vm.back() })
                        is Route.Similar -> {
                            val controller = vm.controller(route.controllerId) ?: return@CompositionLocalProvider
                            app.dudebooru.ui.rec.SimilarScreen(vm, controller, route.post, actions, onBack = { vm.back() })
                        }
                        }
                    }
                }
            }
            NotInterestedSheet(actions)
            AdultDialog(vm)
            app.dudebooru.ui.update.UpdateDialog(vm.updates)
            // Тап по уведомлению: к художнику, в «Художники» или в окно обновления.
            val open by incomingOpen.collectAsStateWithLifecycle()
            LaunchedEffect(open) {
                when (val request = open) {
                    is OpenRequest.Artist -> if (vm.c.registry.site(request.site) != null) vm.openArtist(request.site, request.name)
                    OpenRequest.Artists -> vm.navigate(Route.Artists)
                    OpenRequest.Update -> vm.updates.openDialog()
                    null -> return@LaunchedEffect
                }
                incomingOpen.value = null
            }
        }
    }
}

/** «Не интересно…»: лист с тегами поста, тапнул тег — он ушёл в негативные. */
@Composable
private fun NotInterestedSheet(actions: DudeActions) {
    val post by actions.notInterestedPost.collectAsStateWithLifecycle()
    val current = post ?: return
    val colors = LocalTagColors.current
    ModalBottomSheet(onDismissRequest = { actions.notInterestedPost.value = null }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.not_interested_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.not_interested_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TagCategory.ARTIST, TagCategory.COPYRIGHT, TagCategory.CHARACTER, TagCategory.GENERAL).forEach { category ->
                    current.tags.byCategory(category).forEach { tag ->
                        val color = colors.of(category)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.12f),
                            modifier = Modifier.clickable {
                                actions.hideTag(current, tag)
                                actions.notInterestedPost.value = null
                            },
                        ) {
                            Text(tag.replace('_', ' '), color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** «Мне есть 18» перед первым включением NSFW или «Всё». */
@Composable
private fun AdultDialog(vm: MainViewModel) {
    val pending by vm.pendingMode.collectAsStateWithLifecycle()
    if (pending == null) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.confirmAdult(false) },
        title = { Text(stringResource(R.string.adult_title)) },
        text = { Text(stringResource(R.string.adult_text)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.confirmAdult(true) }) { Text(stringResource(R.string.adult_yes)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.confirmAdult(false) }) { Text(stringResource(R.string.adult_no)) } },
    )
}
