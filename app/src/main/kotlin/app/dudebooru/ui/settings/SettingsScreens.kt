@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.BuildConfig
import app.dudebooru.R
import app.dudebooru.booru.download.NameTemplate
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.booru.site.Sites
import app.dudebooru.data.CrashLog
import app.dudebooru.data.account.StoredAccount
import app.dudebooru.data.net.DohProvider
import app.dudebooru.data.net.ProxyConfig
import app.dudebooru.data.settings.CensorStyle
import app.dudebooru.data.settings.DownloadPrefs
import app.dudebooru.notify.Notifications
import app.dudebooru.ui.accounts.AccountsViewModel
import app.dudebooru.ui.accounts.LoginForm
import app.dudebooru.ui.common.label
import app.dudebooru.ui.face.IconManager
import app.dudebooru.ui.face.IconPreview
import app.dudebooru.ui.feed.CensorPreview
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.main.Avatar
import app.dudebooru.ui.main.Route
import app.dudebooru.ui.main.SettingsPage
import app.dudebooru.ui.main.displayName
import app.dudebooru.ui.main.steadyBarColors
import app.dudebooru.ui.update.UpdateController
import app.dudebooru.ui.update.UpdateState
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Настройки: профиль по центру и разделы группами, как в exteraGram; у каждого раздела своя страница.
 * Режим SFW / NSFW / Всё живёт в [app.dudebooru.ui.main.MainViewModel] — он приходит сверху вместе
 * с проверкой «мне есть 18».
 */
@Composable
fun SettingsScreen(
    vm: AccountsViewModel,
    page: SettingsPage?,
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    mode: ContentMode,
    onMode: (ContentMode) -> Unit,
    updates: UpdateController?,
    onAskNotifications: () -> Unit,
) {
    val open: (SettingsPage) -> Unit = { onOpen(Route.Settings(it)) }
    when (page) {
        null -> SettingsHome(vm, mode, updates, onBack, open, onOpenNegativeTags = { onOpen(Route.NegativeTags) })
        SettingsPage.PROFILE -> ProfilePage(vm, onBack)
        SettingsPage.ACCOUNTS -> AccountsPage(vm, onBack)
        SettingsPage.SOURCES -> SourcesPage(vm, onBack)
        SettingsPage.FEED -> FeedPage(vm, onBack)
        SettingsPage.VIEWER -> ViewerPage(vm, onBack)
        SettingsPage.CONTENT -> ContentPage(vm, mode, onMode, onBack, onOpenNegativeTags = { onOpen(Route.NegativeTags) })
        SettingsPage.RECS -> RecsPage(vm, onBack)
        SettingsPage.LOOK -> LookPage(onBack, onOpenThemes = { onOpen(Route.Themes) }, onOpenIcons = { onOpen(Route.IconPicker) })
        SettingsPage.NOTIFICATIONS -> NotificationsPage(vm, onBack, onAskNotifications)
        SettingsPage.DOWNLOADS -> DownloadsPage(vm, onBack)
        SettingsPage.PRIVACY -> PrivacyPage(vm, onBack)
        SettingsPage.NETWORK -> NetworkPage(vm, onBack)
        SettingsPage.DATA -> DataPage(vm, onBack)
        SettingsPage.ABOUT -> AboutPage(updates, onBack, onOpenGame = { onOpen(Route.Game) }, onLicenses = { open(SettingsPage.LICENSES) })
        SettingsPage.LICENSES -> LicensesPage(onBack)
    }
}

// --- список разделов ---

@Composable
private fun SettingsHome(
    vm: AccountsViewModel,
    mode: ContentMode,
    updates: UpdateController?,
    onBack: () -> Unit,
    open: (SettingsPage) -> Unit,
    onOpenNegativeTags: () -> Unit,
) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val proxy by vm.proxy.collectAsStateWithLifecycle()
    val negativeCount by vm.negativeCount.collectAsStateWithLifecycle()
    val update = updates?.state?.collectAsStateWithLifecycle()?.value
    var notificationsAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.allowed(context)
        onPauseOrDispose { }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setAvatarFromGallery) }
    val pickAvatar = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    SettingsFrame(
        title = null,
        onBack = onBack,
        actions = { RoundIconButton(DudeIcons.Edit, stringResource(R.string.settings_profile_edit)) { open(SettingsPage.PROFILE) } },
    ) { padding ->
        SettingsList(padding) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Avatar(profile?.avatarUrl, Modifier.size(104.dp).clip(CircleShape).clickable(onClick = pickAvatar))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.background),
                            modifier = Modifier.align(Alignment.BottomEnd).size(38.dp).clip(CircleShape).clickable(onClick = pickAvatar),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    DudeIcons.Camera,
                                    stringResource(R.string.profile_avatar_pick),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        profile.displayName(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open(SettingsPage.PROFILE) }.padding(horizontal = 6.dp),
                    )
                    Text(
                        "@" + profile?.nick.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "accounts") {
                val signedIn = vm.groups.count { accounts[it.accountGroup]?.invalid == false }
                val expired = vm.groups.any { accounts[it.accountGroup]?.invalid == true }
                SettingsGroup {
                    item {
                        SettingsRow(
                            title = stringResource(R.string.settings_accounts),
                            icon = DudeIcons.TileAccount,
                            value = when {
                                expired -> stringResource(R.string.settings_value_relogin)
                                signedIn > 0 -> signedIn.toString()
                                else -> null
                            },
                            onClick = { open(SettingsPage.ACCOUNTS) },
                        )
                    }
                }
            }
            item(key = "browse") {
                SettingsGroup {
                    item { SettingsRow(stringResource(R.string.settings_sources), icon = DudeIcons.TileSources, onClick = { open(SettingsPage.SOURCES) }) }
                    item { SettingsRow(stringResource(R.string.settings_feed), icon = DudeIcons.TileFeed, onClick = { open(SettingsPage.FEED) }) }
                    item { SettingsRow(stringResource(R.string.settings_viewer), icon = DudeIcons.TileViewer, onClick = { open(SettingsPage.VIEWER) }) }
                    item {
                        SettingsRow(
                            stringResource(R.string.settings_content),
                            icon = DudeIcons.TileContent,
                            value = stringResource(mode.label()),
                            onClick = { open(SettingsPage.CONTENT) },
                        )
                    }
                    item {
                        SettingsRow(
                            stringResource(R.string.drawer_negative_tags),
                            icon = DudeIcons.TileNegative,
                            value = negativeCount.takeIf { it > 0 }?.toString(),
                            onClick = onOpenNegativeTags,
                        )
                    }
                    item { SettingsRow(stringResource(R.string.settings_recs), icon = DudeIcons.TileRecs, onClick = { open(SettingsPage.RECS) }) }
                }
            }
            item(key = "look") {
                SettingsGroup {
                    item { SettingsRow(stringResource(R.string.settings_look), icon = DudeIcons.TileLook, onClick = { open(SettingsPage.LOOK) }) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        item {
                            SettingsRow(
                                stringResource(R.string.settings_language),
                                icon = DudeIcons.TileLanguage,
                                value = appLanguage(context) ?: stringResource(R.string.settings_language_system),
                                onClick = { openLanguageSettings(context) },
                            )
                        }
                    }
                }
            }
            item(key = "system") {
                SettingsGroup {
                    item {
                        SettingsRow(
                            stringResource(R.string.settings_notifications),
                            icon = DudeIcons.TileNotifications,
                            value = if (notificationsAllowed) null else stringResource(R.string.settings_value_off),
                            onClick = { open(SettingsPage.NOTIFICATIONS) },
                        )
                    }
                    item { SettingsRow(stringResource(R.string.settings_downloads), icon = DudeIcons.TileDownloads, onClick = { open(SettingsPage.DOWNLOADS) }) }
                    item { SettingsRow(stringResource(R.string.settings_privacy), icon = DudeIcons.TilePrivacy, onClick = { open(SettingsPage.PRIVACY) }) }
                    item {
                        SettingsRow(
                            stringResource(R.string.network_title),
                            icon = DudeIcons.TileNetwork,
                            value = if (proxy.isActive) proxyName(proxy.type) else null,
                            onClick = { open(SettingsPage.NETWORK) },
                        )
                    }
                    item { SettingsRow(stringResource(R.string.settings_data), icon = DudeIcons.TileData, onClick = { open(SettingsPage.DATA) }) }
                }
            }
            item(key = "about") {
                val available = update as? UpdateState.Available
                SettingsGroup {
                    item {
                        SettingsRow(
                            stringResource(R.string.settings_about),
                            icon = DudeIcons.TileAbout,
                            value = if (available != null) stringResource(R.string.settings_value_update, available.release.version) else BuildConfig.VERSION_NAME,
                            onClick = { open(SettingsPage.ABOUT) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun proxyName(type: ProxyConfig.Type): String = when (type) {
    ProxyConfig.Type.NONE -> stringResource(R.string.network_direct)
    ProxyConfig.Type.HTTP -> "HTTP"
    ProxyConfig.Type.SOCKS -> "SOCKS5"
}

/** Значок сайта: его favicon, пока грузится или если не открылся — первая буква на плашке. */
@Composable
private fun SiteBadge(site: SiteConfig, size: androidx.compose.ui.unit.Dp = 34.dp) {
    var loaded by remember(site.id) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .then(Modifier.clip(RoundedCornerShape(size * 0.3f))),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = if (loaded) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.matchParentSize()) {}
        if (!loaded) {
            Text(site.name.take(1), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        AsyncImage(
            model = "${site.baseUrl}/favicon.ico",
            contentDescription = null,
            onSuccess = { loaded = true },
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun openLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    runCatching {
        context.startActivity(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

/** Язык, выбранный для приложения в Android 13+, или null — «как в системе». */
private fun appLanguage(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return languageOf(context)
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun languageOf(context: Context): String? {
    val locales = context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales ?: return null
    if (locales.isEmpty) return null
    val locale = locales[0]
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

// --- профиль ---

/** Локальные имя, @ник и аватарка — на сайты не отправляются. */
@Composable
private fun ProfilePage(vm: AccountsViewModel, onBack: () -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }
    var nick by rememberSaveable(profile?.nick) { mutableStateOf(profile?.nick.orEmpty()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setAvatarFromGallery) }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    SettingsPageScaffold(stringResource(R.string.settings_profile), onBack) {
        item(key = "avatar") {
            SettingsGroup(footer = stringResource(R.string.profile_avatar_hint)) {
                item {
                    SettingsRow(
                        stringResource(R.string.profile_avatar_pick),
                        leading = { Avatar(profile?.avatarUrl, Modifier.size(40.dp)) },
                        onClick = pick,
                    )
                }
                if (profile?.avatarUrl != null) {
                    item {
                        SettingsRow(
                            stringResource(R.string.profile_avatar_reset),
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = vm::resetAvatar,
                        )
                    }
                }
            }
        }
        item(key = "fields") {
            SettingsGroup(footer = stringResource(R.string.profile_local_hint)) {
                item {
                    SettingsBlock {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.profile_name)) },
                            placeholder = { Text(stringResource(R.string.profile_default_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = nick,
                            onValueChange = { nick = it.removePrefix("@") },
                            label = { Text(stringResource(R.string.profile_nick)) },
                            prefix = { Text("@") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { vm.setProfile(name, nick) },
                            enabled = name != profile?.name.orEmpty() || nick != profile?.nick.orEmpty(),
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
}

// --- аккаунты ---

/** Сайты списком: статус входа одной строкой; тап раскрывает вход или подробности со «Выйти». */
@Composable
private fun AccountsPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val forms by vm.forms.collectAsStateWithLifecycle()
    val sync by vm.syncPrefs.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsPageScaffold(stringResource(R.string.settings_accounts), onBack) {
        item(key = "sites") {
            SettingsGroup(title = stringResource(R.string.accounts_sites), footer = stringResource(R.string.accounts_note)) {
                vm.groups.forEach { site ->
                    item {
                        AccountEntry(
                            site = site,
                            shared = vm.sharedWith(site),
                            account = accounts[site.accountGroup],
                            form = forms[site.accountGroup] ?: LoginForm(),
                            expanded = expanded == site.accountGroup,
                            onToggle = { expanded = if (expanded == site.accountGroup) null else site.accountGroup },
                            onEdit = { transform -> vm.edit(site, transform) },
                            onLogin = { vm.login(site) },
                            onLogout = { vm.logout(site) },
                        )
                    }
                }
            }
        }
        item(key = "sync") {
            SettingsGroup(title = stringResource(R.string.settings_sync)) {
                item {
                    SettingsSwitch(
                        stringResource(R.string.sync_saved),
                        sync.syncSaved,
                        { vm.setSyncPrefs(sync.copy(syncSaved = it)) },
                        subtitle = stringResource(R.string.sync_saved_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.mirror_likes),
                        sync.mirrorLikes,
                        { vm.setSyncPrefs(sync.copy(mirrorLikes = it)) },
                        subtitle = stringResource(R.string.mirror_likes_hint),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountEntry(
    site: SiteConfig,
    shared: List<SiteConfig>,
    account: StoredAccount?,
    form: LoginForm,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: ((LoginForm) -> LoginForm) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val signedIn = account != null && !account.invalid
    Column {
        SettingsRow(
            title = site.name,
            subtitle = when {
                signedIn -> "@" + account?.info?.login.orEmpty()
                account?.invalid == true -> stringResource(R.string.account_expired)
                else -> stringResource(R.string.drawer_not_signed_in)
            },
            subtitleColor = when {
                signedIn -> MaterialTheme.colorScheme.primary
                account?.invalid == true -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            leading = { SiteBadge(site) },
            onClick = onToggle,
            trailing = {
                Icon(
                    if (expanded) DudeIcons.ChevronUp else DudeIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        AnimatedVisibility(expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shared.isNotEmpty()) {
                    Text(
                        stringResource(R.string.account_shared_with, shared.joinToString { it.name }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (signedIn && account != null) {
                    account.info.levelName?.let {
                        Text(stringResource(R.string.account_level, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    val limit = account.info.tagLimit?.toString() ?: stringResource(R.string.account_tag_limit_unlimited)
                    Text(stringResource(R.string.account_tag_limit, limit), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = onLogout,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.account_sign_out)) }
                } else {
                    if (account?.invalid == true) {
                        Text(stringResource(R.string.account_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    val isDanbooru = site.engine == EngineType.DANBOORU
                    OutlinedTextField(
                        value = form.login.ifEmpty { account?.info?.login.orEmpty() },
                        onValueChange = { value -> onEdit { it.copy(login = value, error = null) } },
                        label = { Text(stringResource(R.string.account_login)) },
                        singleLine = true,
                        enabled = !form.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.secret,
                        onValueChange = { value -> onEdit { it.copy(secret = value, error = null) } },
                        label = { Text(stringResource(if (isDanbooru) R.string.account_api_key else R.string.account_password)) },
                        supportingText = { Text(stringResource(if (isDanbooru) R.string.account_api_key_help else R.string.account_password_help)) },
                        singleLine = true,
                        enabled = !form.busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onLogin, enabled = !form.busy) { Text(stringResource(R.string.account_sign_in)) }
                        if (form.busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (isDanbooru) {
                            TextButton(onClick = { openUrl(context, "${site.baseUrl}/profile") }) {
                                Text(stringResource(R.string.account_api_key_where))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- источники и папки ---

@Composable
private fun SourcesPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val prefs by vm.folderPrefs.collectAsStateWithLifecycle()
    val current = folders ?: return
    val sites = current.order.mapNotNull { Sites.byId(it) }

    SettingsPageScaffold(stringResource(R.string.settings_sources), onBack) {
        item(key = "sites") {
            SettingsGroup(title = stringResource(R.string.sources_folders), footer = stringResource(R.string.sources_hint)) {
                sites.forEachIndexed { index, site ->
                    item {
                        val visible = site.id !in current.hidden
                        SettingsRow(
                            title = site.name,
                            subtitle = site.baseUrl.removePrefix("https://").removePrefix("www.") +
                                if (site.safeOnly) " · " + stringResource(R.string.sources_sfw_only) else "",
                            leading = { SiteBadge(site) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { vm.moveFolder(site.id, -1) }, enabled = index > 0, modifier = Modifier.size(40.dp)) {
                                        Icon(DudeIcons.ChevronUp, stringResource(R.string.folder_move_left))
                                    }
                                    IconButton(onClick = { vm.moveFolder(site.id, 1) }, enabled = index < sites.lastIndex, modifier = Modifier.size(40.dp)) {
                                        Icon(DudeIcons.ChevronDown, stringResource(R.string.folder_move_right))
                                    }
                                    Switch(checked = visible, onCheckedChange = { vm.setFolderVisible(site.id, it) })
                                }
                            },
                        )
                    }
                }
            }
        }
        item(key = "behaviour") {
            SettingsGroup {
                item {
                    SettingsSwitch(
                        stringResource(R.string.sources_counts),
                        prefs.newCounts,
                        { vm.setFolderPrefs(prefs.copy(newCounts = it)) },
                        subtitle = stringResource(R.string.sources_counts_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.sources_swipe),
                        prefs.swipe,
                        { vm.setFolderPrefs(prefs.copy(swipe = it)) },
                        subtitle = stringResource(R.string.sources_swipe_hint),
                    )
                }
            }
        }
    }
}

// --- лента ---

@Composable
private fun FeedPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val prefs by vm.feedPrefs.collectAsStateWithLifecycle()
    SettingsPageScaffold(stringResource(R.string.settings_feed), onBack) {
        item(key = "layout") {
            SettingsGroup(title = stringResource(R.string.settings_feed_layout)) {
                item {
                    SettingsBlock {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !prefs.grid,
                                onClick = { vm.setFeedPrefs(prefs.copy(grid = false)) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text(stringResource(R.string.settings_feed_posts)) }
                            SegmentedButton(
                                selected = prefs.grid,
                                onClick = { vm.setFeedPrefs(prefs.copy(grid = true)) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text(stringResource(R.string.settings_feed_grid)) }
                        }
                    }
                }
            }
        }
        item(key = "gestures") {
            SettingsGroup {
                item {
                    SettingsSwitch(
                        stringResource(R.string.settings_double_tap),
                        prefs.doubleTapLike,
                        { vm.setFeedPrefs(prefs.copy(doubleTapLike = it)) },
                        subtitle = stringResource(R.string.settings_double_tap_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.settings_animate_gifs),
                        prefs.animateGifs,
                        { vm.setFeedPrefs(prefs.copy(animateGifs = it)) },
                        subtitle = stringResource(R.string.settings_animate_gifs_hint),
                    )
                }
            }
        }
    }
}

// --- просмотр ---

@Composable
private fun ViewerPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val prefs by vm.viewerPrefs.collectAsStateWithLifecycle()
    SettingsPageScaffold(stringResource(R.string.settings_viewer), onBack) {
        item(key = "viewer") {
            SettingsGroup {
                item {
                    SettingsSwitch(
                        stringResource(R.string.viewer_original),
                        prefs.originalAtOnce,
                        { vm.setViewerPrefs(prefs.copy(originalAtOnce = it)) },
                        subtitle = stringResource(R.string.viewer_original_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.viewer_volume),
                        prefs.volumeKeys,
                        { vm.setViewerPrefs(prefs.copy(volumeKeys = it)) },
                        subtitle = stringResource(R.string.viewer_volume_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.viewer_screen_on),
                        prefs.keepScreenOn,
                        { vm.setViewerPrefs(prefs.copy(keepScreenOn = it)) },
                        subtitle = stringResource(R.string.viewer_screen_on_hint),
                    )
                }
            }
        }
    }
}

// --- контент и цензура ---

@Composable
private fun ContentPage(
    vm: AccountsViewModel,
    mode: ContentMode,
    onMode: (ContentMode) -> Unit,
    onBack: () -> Unit,
    onOpenNegativeTags: () -> Unit,
) {
    val enabled by vm.censorEnabled.collectAsStateWithLifecycle()
    val prefs by vm.censorPrefs.collectAsStateWithLifecycle()
    val showHidden by vm.showHiddenCount.collectAsStateWithLifecycle()
    val negativeCount by vm.negativeCount.collectAsStateWithLifecycle()

    SettingsPageScaffold(stringResource(R.string.settings_content), onBack) {
        item(key = "mode") {
            SettingsGroup(title = stringResource(R.string.settings_mode), footer = stringResource(R.string.settings_mode_hint)) {
                item {
                    SettingsBlock {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ContentMode.entries.forEachIndexed { i, entry ->
                                SegmentedButton(
                                    selected = entry == mode,
                                    onClick = { onMode(entry) },
                                    shape = SegmentedButtonDefaults.itemShape(i, ContentMode.entries.size),
                                ) { Text(stringResource(entry.label())) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "censor") {
            SettingsGroup(title = stringResource(R.string.settings_censor_group)) {
                item {
                    SettingsSwitch(
                        stringResource(R.string.settings_censor),
                        enabled,
                        vm::setCensorEnabled,
                        subtitle = stringResource(R.string.settings_censor_hint),
                    )
                }
                if (enabled) {
                    item {
                        SettingsBlock {
                            val styles = CensorStyle.entries
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                styles.forEachIndexed { i, style ->
                                    SegmentedButton(
                                        selected = prefs.style == style,
                                        onClick = { vm.setCensorPrefs(prefs.copy(style = style)) },
                                        shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                                    ) {
                                        Text(
                                            stringResource(
                                                when (style) {
                                                    CensorStyle.SPOILER -> R.string.censor_style_spoiler
                                                    CensorStyle.BLUR -> R.string.censor_style_blur
                                                    CensorStyle.PIXELATE -> R.string.censor_style_pixel
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                            // Ползунок откликается сразу: значение живёт здесь, в настройки уходит, когда палец отпущен.
                            var strength by remember(prefs.strength) { mutableFloatStateOf(prefs.strength) }
                            CensorPreview(
                                style = prefs.style,
                                strength = strength,
                                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp)),
                            )
                            Text(stringResource(R.string.censor_strength), style = MaterialTheme.typography.bodyLarge)
                            Slider(
                                value = strength,
                                onValueChange = { strength = it },
                                onValueChangeFinished = { vm.setCensorPrefs(prefs.copy(strength = strength)) },
                            )
                        }
                    }
                    item {
                        SettingsSwitch(
                            stringResource(R.string.censor_in_viewer),
                            prefs.inViewer,
                            { vm.setCensorPrefs(prefs.copy(inViewer = it)) },
                            subtitle = stringResource(R.string.censor_in_viewer_hint),
                        )
                    }
                    item {
                        SettingsSwitch(
                            stringResource(R.string.censor_sensitive),
                            prefs.blurSensitive,
                            { vm.setCensorPrefs(prefs.copy(blurSensitive = it)) },
                        )
                    }
                }
            }
        }
        item(key = "hidden") {
            SettingsGroup(title = stringResource(R.string.settings_hidden)) {
                item {
                    SettingsRow(
                        stringResource(R.string.drawer_negative_tags),
                        subtitle = stringResource(R.string.settings_negative_tags_hint),
                        value = negativeCount.takeIf { it > 0 }?.toString(),
                        onClick = onOpenNegativeTags,
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.settings_show_hidden),
                        showHidden,
                        vm::setShowHiddenCount,
                        subtitle = stringResource(R.string.settings_show_hidden_hint),
                    )
                }
            }
        }
    }
}

// --- рекомендации ---

@Composable
private fun RecsPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.recPrefs.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val shares = listOf(0f, 0.15f, 0.3f)

    SettingsPageScaffold(stringResource(R.string.settings_recs), onBack) {
        item(key = "explore") {
            SettingsGroup(title = stringResource(R.string.recs_explore_title), footer = stringResource(R.string.recs_explore_hint)) {
                item {
                    SettingsBlock {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            shares.forEachIndexed { i, share ->
                                SegmentedButton(
                                    selected = kotlin.math.abs(prefs.exploreShare - share) < 0.01f,
                                    onClick = { vm.setRecPrefs(prefs.copy(exploreShare = share)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, shares.size),
                                ) {
                                    Text(if (share == 0f) stringResource(R.string.recs_explore_none) else "${(share * 100).roundToInt()}%")
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "signals") {
            SettingsGroup {
                item {
                    SettingsSwitch(
                        stringResource(R.string.recs_use_saved),
                        prefs.useSaved,
                        { vm.setRecPrefs(prefs.copy(useSaved = it)) },
                        subtitle = stringResource(R.string.recs_use_saved_hint),
                    )
                }
                item {
                    SettingsRow(
                        stringResource(R.string.recs_reset),
                        subtitle = stringResource(R.string.recs_reset_hint),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.recs_reset_title)) },
            text = { Text(stringResource(R.string.recs_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetRecs()
                    Toast.makeText(context, context.getString(R.string.recs_reset_done), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.recs_reset_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// --- оформление ---

@Composable
private fun LookPage(onBack: () -> Unit, onOpenThemes: () -> Unit, onOpenIcons: () -> Unit) {
    val context = LocalContext.current
    // Страница собирается заново после выбора иконки или языка — значение всегда свежее.
    val icon = remember { IconManager.current(context) }
    SettingsPageScaffold(stringResource(R.string.settings_look), onBack) {
        item(key = "look") {
            SettingsGroup {
                item {
                    SettingsRow(
                        stringResource(R.string.drawer_themes),
                        subtitle = stringResource(R.string.settings_look_themes_hint),
                        onClick = onOpenThemes,
                    )
                }
                item {
                    SettingsRow(
                        stringResource(R.string.icon_picker_title),
                        leading = { IconPreview(icon, Modifier.size(34.dp), RoundedCornerShape(10.dp)) },
                        value = stringResource(icon.label),
                        onClick = onOpenIcons,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        SettingsRow(
                            stringResource(R.string.settings_language),
                            value = appLanguage(context) ?: stringResource(R.string.settings_language_system),
                            onClick = { openLanguageSettings(context) },
                        )
                    }
                }
            }
        }
    }
}

// --- уведомления ---

/** Новые работы у подписок и новые версии. Колокольчик на странице художника выключает только его. */
@Composable
private fun NotificationsPage(vm: AccountsViewModel, onBack: () -> Unit, onAskNotifications: () -> Unit) {
    val context = LocalContext.current
    val artists by vm.notifyArtists.collectAsStateWithLifecycle()
    val releases by vm.notifyUpdates.collectAsStateWithLifecycle()
    // Разрешение могли выдать в системном окне — перечитываем, когда экран снова на виду.
    var allowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        allowed = Notifications.allowed(context)
        onPauseOrDispose { }
    }
    val openSystem = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        runCatching { context.startActivity(intent) }
        Unit
    }

    SettingsPageScaffold(stringResource(R.string.settings_notifications), onBack) {
        if (!allowed) {
            item(key = "blocked") {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = openSystem),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DudeIcons.BellOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(R.string.notify_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
        item(key = "switches") {
            SettingsGroup {
                item {
                    SettingsSwitch(
                        stringResource(R.string.notify_artists_switch),
                        artists,
                        {
                            if (it) onAskNotifications()
                            vm.setNotifyArtists(it)
                        },
                        subtitle = stringResource(R.string.notify_artists_switch_hint),
                    )
                }
                if (BuildConfig.UPDATE_CHECK) {
                    item {
                        SettingsSwitch(
                            stringResource(R.string.notify_updates_switch),
                            releases,
                            {
                                if (it) onAskNotifications()
                                vm.setNotifyUpdates(it)
                            },
                            subtitle = stringResource(R.string.notify_updates_hint),
                        )
                    }
                }
            }
        }
        item(key = "system") {
            SettingsGroup {
                item {
                    SettingsRow(
                        stringResource(R.string.notify_system_settings),
                        subtitle = stringResource(R.string.notify_system_hint),
                        onClick = openSystem,
                    )
                }
            }
        }
    }
}

// --- скачивание ---

/** Папка (можно на SD-карту), шаблон имени с предпросмотром, Wi-Fi, параллельность, теги в файле. */
@Composable
private fun DownloadsPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.downloadPrefs.collectAsStateWithLifecycle()
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.setDownloadPrefs(prefs.copy(treeUri = uri.toString()))
        }
    }
    // Поле с курсором: переменная из подсказок встаёт туда, где курсор, а не в конец.
    var template by remember(prefs.template) { mutableStateOf(TextFieldValue(prefs.template, TextRange(prefs.template.length))) }
    val preview = remember(template.text) {
        runCatching { NameTemplate.render(template.text, vm.samplePost(), "jpg") }.getOrDefault("")
    }

    SettingsPageScaffold(stringResource(R.string.settings_downloads), onBack) {
        item(key = "folder") {
            SettingsGroup {
                item {
                    val folderLabel = prefs.treeUri?.let { Uri.parse(it).lastPathSegment?.substringAfter(':') } ?: stringResource(R.string.dl_folder_default)
                    SettingsRow(
                        stringResource(R.string.dl_folder),
                        subtitle = folderLabel,
                        onClick = { treePicker.launch(null) },
                        trailing = if (prefs.treeUri != null) {
                            { TextButton(onClick = { vm.setDownloadPrefs(prefs.copy(treeUri = null)) }) { Text(stringResource(R.string.dl_folder_reset)) } }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        item(key = "name") {
            SettingsGroup(title = stringResource(R.string.dl_template), footer = stringResource(R.string.dl_template_vars)) {
                item {
                    SettingsBlock {
                        OutlinedTextField(
                            value = template,
                            onValueChange = { template = it },
                            supportingText = { Text(stringResource(R.string.dl_template_preview, preview)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NameTemplate.VARIABLES.forEach { variable ->
                                SuggestionChip(
                                    onClick = {
                                        val insert = "{$variable}"
                                        val text = template.text.replaceRange(template.selection.min, template.selection.max, insert)
                                        template = TextFieldValue(text, TextRange(template.selection.min + insert.length))
                                    },
                                    label = { Text("{$variable}") },
                                )
                            }
                        }
                        if (template.text != prefs.template) {
                            Button(
                                onClick = { vm.setDownloadPrefs(prefs.copy(template = template.text.ifBlank { DownloadPrefs.DEFAULT_TEMPLATE })) },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
            }
        }
        item(key = "rules") {
            SettingsGroup(title = stringResource(R.string.dl_group_download)) {
                item {
                    SettingsSwitch(
                        stringResource(R.string.dl_wifi_only),
                        prefs.wifiOnly,
                        { vm.setDownloadPrefs(prefs.copy(wifiOnly = it)) },
                        subtitle = stringResource(R.string.dl_wifi_only_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.dl_write_tags),
                        prefs.writeTags,
                        { vm.setDownloadPrefs(prefs.copy(writeTags = it)) },
                        subtitle = stringResource(R.string.dl_write_tags_hint),
                    )
                }
                item {
                    SettingsBlock {
                        var parallel by remember(prefs.parallel) { mutableFloatStateOf(prefs.parallel.toFloat()) }
                        Text(stringResource(R.string.dl_parallel, parallel.roundToInt()), style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = parallel,
                            onValueChange = { parallel = it },
                            onValueChangeFinished = { vm.setDownloadPrefs(prefs.copy(parallel = parallel.roundToInt().coerceIn(1, 4))) },
                            valueRange = 1f..4f,
                            steps = 2,
                        )
                    }
                }
            }
        }
    }
}

// --- приватность ---

@Composable
private fun PrivacyPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.privacyPrefs.collectAsStateWithLifecycle()
    SettingsPageScaffold(stringResource(R.string.settings_privacy), onBack) {
        item(key = "privacy") {
            SettingsGroup(footer = stringResource(R.string.privacy_footer)) {
                item {
                    SettingsSwitch(
                        stringResource(R.string.privacy_lock),
                        prefs.appLock,
                        { on ->
                            val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
                            if (on && keyguard?.isDeviceSecure != true) {
                                Toast.makeText(context, context.getString(R.string.privacy_lock_no_screen_lock), Toast.LENGTH_LONG).show()
                            } else {
                                vm.setPrivacyPrefs(prefs.copy(appLock = on))
                            }
                        },
                        subtitle = stringResource(R.string.privacy_lock_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.privacy_secure),
                        prefs.secureScreen,
                        { vm.setPrivacyPrefs(prefs.copy(secureScreen = it)) },
                        subtitle = stringResource(R.string.privacy_secure_hint),
                    )
                }
                item {
                    SettingsSwitch(
                        stringResource(R.string.privacy_history),
                        prefs.keepHistory,
                        { vm.setPrivacyPrefs(prefs.copy(keepHistory = it)) },
                        subtitle = stringResource(R.string.privacy_history_hint),
                    )
                }
            }
        }
    }
}

// --- сеть ---

/** Свой HTTP/SOCKS5-прокси и DNS-over-HTTPS — если сайт не открывается напрямую из вашей сети. */
@Composable
private fun NetworkPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val current by vm.proxy.collectAsStateWithLifecycle()
    val doh by vm.doh.collectAsStateWithLifecycle()
    var type by rememberSaveable(current) { mutableStateOf(current.type) }
    var host by rememberSaveable(current) { mutableStateOf(current.host) }
    var port by rememberSaveable(current) { mutableStateOf(if (current.port > 0) current.port.toString() else "") }
    val draft = ProxyConfig(type, host.trim(), port.toIntOrNull() ?: 0)

    SettingsPageScaffold(stringResource(R.string.network_title), onBack) {
        item(key = "proxy") {
            SettingsGroup(title = stringResource(R.string.network_proxy), footer = stringResource(R.string.network_note)) {
                item {
                    SettingsBlock {
                        val types = ProxyConfig.Type.entries
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            types.forEachIndexed { i, entry ->
                                SegmentedButton(
                                    selected = entry == type,
                                    onClick = { type = entry },
                                    shape = SegmentedButtonDefaults.itemShape(i, types.size),
                                ) { Text(proxyName(entry)) }
                            }
                        }
                        if (type != ProxyConfig.Type.NONE) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text(stringResource(R.string.network_host)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                                label = { Text(stringResource(R.string.network_port)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val valid = type == ProxyConfig.Type.NONE || draft.isActive
                        if (draft != current) {
                            Button(onClick = { vm.saveProxy(draft) }, enabled = valid, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(R.string.network_save))
                            }
                        }
                    }
                }
            }
        }
        item(key = "doh") {
            SettingsGroup(title = stringResource(R.string.doh_title), footer = stringResource(R.string.doh_note)) {
                DohProvider.entries.forEach { provider ->
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = provider == doh, role = Role.RadioButton, onClick = { vm.setDoh(provider) })
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                when (provider) {
                                    DohProvider.NONE -> stringResource(R.string.doh_system)
                                    DohProvider.CLOUDFLARE -> "Cloudflare"
                                    DohProvider.GOOGLE -> "Google"
                                    DohProvider.QUAD9 -> "Quad9"
                                    DohProvider.ADGUARD -> "AdGuard"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            RadioButton(selected = provider == doh, onClick = null)
                        }
                    }
                }
            }
        }
    }
}

// --- данные и память ---

@Composable
private fun DataPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val cache by vm.cache.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshCache() }
    val size = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }

    SettingsPageScaffold(stringResource(R.string.settings_data), onBack) {
        item(key = "cache") {
            SettingsGroup(title = stringResource(R.string.data_cache), footer = stringResource(R.string.data_cache_hint)) {
                item {
                    SettingsRow(
                        stringResource(R.string.data_images),
                        subtitle = stringResource(R.string.data_images_hint),
                        value = cache?.let { size(it.images) } ?: "…",
                    )
                }
                item {
                    SettingsRow(
                        stringResource(R.string.data_other),
                        subtitle = stringResource(R.string.data_other_hint),
                        value = cache?.let { size(it.other) } ?: "…",
                    )
                }
                item {
                    SettingsRow(
                        stringResource(R.string.data_clear),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            vm.clearCache()
                            Toast.makeText(context, context.getString(R.string.data_cleared), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

// --- о приложении ---

/** Иконка и версия (семь тапов по ним открывают игру), обновления, что нового, журнал ошибок, лицензии. */
@Composable
private fun AboutPage(updates: UpdateController?, onBack: () -> Unit, onOpenGame: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val icon = remember { IconManager.current(context) }
    var taps by remember { mutableIntStateOf(0) }
    var crashes by remember { mutableIntStateOf(CrashLog.count(context)) }
    var news by rememberSaveable { mutableStateOf(false) }
    val egg = {
        taps++
        if (taps >= 7) {
            taps = 0
            onOpenGame()
        } else if (taps >= 4) {
            Toast.makeText(context, context.getString(R.string.game_egg_steps, 7 - taps), Toast.LENGTH_SHORT).show()
        }
    }

    SettingsPageScaffold(stringResource(R.string.settings_about), onBack) {
        item(key = "app") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconPreview(icon, Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).clickable(onClick = egg), RoundedCornerShape(28.dp))
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = egg),
                ) {
                    Text(
                        stringResource(R.string.about_version_chip, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        item(key = "updates") {
            SettingsGroup {
                if (updates != null && BuildConfig.UPDATE_CHECK) {
                    item {
                        val state by updates.state.collectAsStateWithLifecycle()
                        val busy = state is UpdateState.Checking || state is UpdateState.Downloading
                        val available = state as? UpdateState.Available
                        SettingsRow(
                            title = if (available != null) {
                                stringResource(R.string.update_available_title, available.release.version)
                            } else {
                                stringResource(R.string.update_check)
                            },
                            subtitle = when (val s = state) {
                                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                                is UpdateState.Available -> stringResource(R.string.update_notify_text)
                                is UpdateState.Downloading -> stringResource(R.string.update_downloading)
                                is UpdateState.Failed -> s.message
                                else -> null
                            },
                            subtitleColor = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = DudeIcons.TileUpdate,
                            onClick = if (busy) {
                                null
                            } else {
                                { if (available != null) updates.openDialog() else updates.check() }
                            },
                            trailing = if (busy) {
                                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                            } else {
                                null
                            },
                        )
                    }
                }
                item {
                    Column {
                        SettingsRow(
                            stringResource(R.string.about_whats_new, BuildConfig.VERSION_NAME),
                            icon = DudeIcons.TileNews,
                            onClick = { news = !news },
                            trailing = {
                                Icon(
                                    if (news) DudeIcons.ChevronUp else DudeIcons.ChevronDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        AnimatedVisibility(news) {
                            Text(
                                stringResource(R.string.whats_new),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 66.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                }
            }
        }
        item(key = "support") {
            SettingsGroup(footer = stringResource(R.string.about_crash_hint)) {
                item {
                    SettingsRow(
                        stringResource(R.string.about_crash_log),
                        subtitle = stringResource(if (crashes > 0) R.string.about_crash_send else R.string.about_crash_empty),
                        icon = DudeIcons.TileBug,
                        value = crashes.takeIf { it > 0 }?.toString(),
                        onClick = if (crashes > 0) {
                            { CrashLog.shareIntent(context)?.let { runCatching { context.startActivity(it) } } }
                        } else {
                            null
                        },
                    )
                }
                if (crashes > 0) {
                    item {
                        SettingsRow(
                            stringResource(R.string.about_crash_clear),
                            icon = DudeIcons.Delete,
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                CrashLog.clear(context)
                                crashes = 0
                            },
                        )
                    }
                }
                item { SettingsRow(stringResource(R.string.about_licenses), icon = DudeIcons.TileLicense, onClick = onLicenses) }
            }
        }
        item(key = "sites") {
            SettingsGroup(title = stringResource(R.string.about_sites), footer = stringResource(R.string.about_disclaimer)) {
                Sites.builtIn.forEach { site ->
                    item {
                        SettingsRow(
                            site.name,
                            subtitle = site.baseUrl.removePrefix("https://").removePrefix("www."),
                            leading = { SiteBadge(site) },
                            onClick = { openUrl(context, site.baseUrl) },
                        )
                    }
                }
            }
        }
    }
}

/** Открытые библиотеки, из которых собрано приложение. */
@Composable
private fun LicensesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries = listOf(
        Triple("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains · Apache 2.0", "https://kotlinlang.org"),
        Triple("Jetpack Compose, Material 3", "Google · Apache 2.0", "https://developer.android.com/jetpack/compose"),
        Triple("AndroidX: Room, DataStore, WorkManager, Activity, Lifecycle", "Google · Apache 2.0", "https://developer.android.com/jetpack/androidx"),
        Triple("Media3 ExoPlayer", "Google · Apache 2.0", "https://github.com/androidx/media"),
        Triple("Material Symbols", "Google · Apache 2.0", "https://github.com/google/material-design-icons"),
        Triple("OkHttp", "Square · Apache 2.0", "https://square.github.io/okhttp/"),
        Triple("Coil", "Coil Contributors · Apache 2.0", "https://coil-kt.github.io/coil/"),
        Triple("Telephoto", "Saket Narayan · Apache 2.0", "https://github.com/saket/telephoto"),
    )
    SettingsPageScaffold(stringResource(R.string.about_licenses), onBack) {
        // Лицензия самого приложения — первой.
        item(key = "app") {
            SettingsGroup(footer = stringResource(R.string.about_app_license_hint)) {
                item {
                    SettingsRow(
                        "DudeBooru",
                        subtitle = stringResource(R.string.about_app_license),
                        onClick = { openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.html") },
                    )
                }
            }
        }
        item(key = "libs") {
            SettingsGroup(footer = stringResource(R.string.about_licenses_hint)) {
                libraries.forEach { (name, license, url) ->
                    item { SettingsRow(name, subtitle = license, onClick = { openUrl(context, url) }) }
                }
            }
        }
    }
}
