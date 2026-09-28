@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.dudebooru.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.BuildConfig
import app.dudebooru.R
import app.dudebooru.booru.download.NameTemplate
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
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
import app.dudebooru.ui.update.UpdateController
import app.dudebooru.ui.update.UpdateState
import kotlin.math.roundToInt

/**
 * Настройки: список разделов с цветными значками и отдельная страница на каждый раздел.
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
    when (page) {
        null -> SettingsHome(vm, mode, updates, onBack) { onOpen(Route.Settings(it)) }
        SettingsPage.PROFILE -> ProfilePage(vm, onBack)
        SettingsPage.ACCOUNTS -> AccountsPage(vm, onBack)
        SettingsPage.FEED -> FeedPage(vm, onBack)
        SettingsPage.CONTENT -> ContentPage(vm, mode, onMode, onBack, onOpenNegativeTags = { onOpen(Route.NegativeTags) })
        SettingsPage.LOOK -> LookPage(onBack, onOpenThemes = { onOpen(Route.Themes) }, onOpenIcons = { onOpen(Route.IconPicker) })
        SettingsPage.NOTIFICATIONS -> NotificationsPage(vm, onBack, onAskNotifications)
        SettingsPage.DOWNLOADS -> DownloadsPage(vm, onBack)
        SettingsPage.NETWORK -> NetworkPage(vm, onBack)
        SettingsPage.ABOUT -> AboutPage(updates, onBack, onOpenGame = { onOpen(Route.Game) })
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
) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val proxy by vm.proxy.collectAsStateWithLifecycle()
    val censor by vm.censorEnabled.collectAsStateWithLifecycle()
    val update = updates?.state?.collectAsStateWithLifecycle()?.value
    var notificationsAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.allowed(context)
        onPauseOrDispose { }
    }

    SettingsPageScaffold(stringResource(R.string.accounts_title), onBack) {
        item(key = "profile") {
            SettingsGroup {
                Row(
                    Modifier.fillMaxWidth().clickable { open(SettingsPage.PROFILE) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(profile?.avatarUrl, Modifier.size(60.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            profile?.nick?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: stringResource(R.string.settings_profile_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Chevron()
                }
            }
        }
        item(key = "accounts") {
            val signedIn = vm.groups.filter { accounts[it.accountGroup]?.invalid == false }
            val expired = vm.groups.filter { accounts[it.accountGroup]?.invalid == true }
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_accounts),
                    subtitle = when {
                        expired.isNotEmpty() -> stringResource(R.string.settings_accounts_invalid, expired.joinToString { it.name })
                        signedIn.isNotEmpty() -> stringResource(R.string.settings_accounts_signed_in, signedIn.joinToString { it.name })
                        else -> stringResource(R.string.settings_accounts_hint)
                    },
                    subtitleColor = if (expired.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = DudeIcons.Key,
                    tint = SectionColors.Accounts,
                    onClick = { open(SettingsPage.ACCOUNTS) },
                )
            }
        }
        item(key = "browsing") {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_feed),
                    subtitle = stringResource(R.string.settings_feed_hint),
                    icon = DudeIcons.Feed,
                    tint = SectionColors.Feed,
                    onClick = { open(SettingsPage.FEED) },
                )
                SettingsDivider(inset = SectionInset)
                SettingsRow(
                    title = stringResource(R.string.settings_content),
                    subtitle = stringResource(
                        if (censor) R.string.settings_content_censor_on else R.string.settings_content_censor_off,
                        stringResource(mode.label()),
                    ),
                    icon = DudeIcons.Shield,
                    tint = SectionColors.Content,
                    onClick = { open(SettingsPage.CONTENT) },
                )
                SettingsDivider(inset = SectionInset)
                SettingsRow(
                    title = stringResource(R.string.settings_look),
                    subtitle = stringResource(R.string.settings_look_hint),
                    icon = DudeIcons.Palette,
                    tint = SectionColors.Look,
                    onClick = { open(SettingsPage.LOOK) },
                )
            }
        }
        item(key = "files") {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(if (notificationsAllowed) R.string.settings_notifications_hint else R.string.settings_notifications_blocked),
                    subtitleColor = if (notificationsAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    icon = DudeIcons.BellRing,
                    tint = SectionColors.Notifications,
                    onClick = { open(SettingsPage.NOTIFICATIONS) },
                )
                SettingsDivider(inset = SectionInset)
                SettingsRow(
                    title = stringResource(R.string.settings_downloads),
                    subtitle = stringResource(R.string.settings_downloads_hint),
                    icon = DudeIcons.Download,
                    tint = SectionColors.Downloads,
                    onClick = { open(SettingsPage.DOWNLOADS) },
                )
            }
        }
        item(key = "system") {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.network_title),
                    subtitle = if (proxy.isActive) {
                        stringResource(R.string.settings_network_proxy, "${proxyName(proxy.type)} ${proxy.host}:${proxy.port}")
                    } else {
                        stringResource(R.string.settings_network_hint)
                    },
                    icon = DudeIcons.Globe,
                    tint = SectionColors.Network,
                    onClick = { open(SettingsPage.NETWORK) },
                )
                SettingsDivider(inset = SectionInset)
                val available = update as? UpdateState.Available
                SettingsRow(
                    title = stringResource(R.string.settings_about),
                    subtitle = if (available != null) {
                        stringResource(R.string.update_available_title, available.release.version)
                    } else {
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
                    },
                    subtitleColor = if (available != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = DudeIcons.Info,
                    tint = SectionColors.About,
                    onClick = { open(SettingsPage.ABOUT) },
                )
            }
        }
    }
}

/** Разделитель под текстом, а не под плашкой: 16 отступ + 36 плашка + 16 зазор. */
private val SectionInset = 68.dp

@Composable
private fun proxyName(type: ProxyConfig.Type): String = when (type) {
    ProxyConfig.Type.NONE -> stringResource(R.string.network_direct)
    ProxyConfig.Type.HTTP -> "HTTP"
    ProxyConfig.Type.SOCKS -> "SOCKS5"
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
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(profile?.avatarUrl, Modifier.size(112.dp).clip(RoundedCornerShape(50)).clickable(onClick = pick))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = pick) { Text(stringResource(R.string.profile_avatar_pick)) }
                    if (profile?.avatarUrl != null) {
                        TextButton(onClick = vm::resetAvatar) { Text(stringResource(R.string.profile_avatar_reset)) }
                    }
                }
            }
        }
        item(key = "fields") {
            SettingsGroup(footer = stringResource(R.string.profile_avatar_hint)) {
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

// --- аккаунты ---

@Composable
private fun AccountsPage(vm: AccountsViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val forms by vm.forms.collectAsStateWithLifecycle()
    val sync by vm.syncPrefs.collectAsStateWithLifecycle()

    SettingsPageScaffold(stringResource(R.string.settings_accounts), onBack) {
        item(key = "note") { SettingsFooter(stringResource(R.string.accounts_note)) }
        items(vm.groups, key = { it.accountGroup }) { site ->
            val shared = vm.sharedWith(site)
            SettingsGroup(
                title = site.name,
                footer = shared.takeIf { it.isNotEmpty() }?.let { list -> stringResource(R.string.account_shared_with, list.joinToString { it.name }) },
            ) {
                AccountBlock(
                    site = site,
                    account = accounts[site.accountGroup],
                    form = forms[site.accountGroup] ?: LoginForm(),
                    onEdit = { transform -> vm.edit(site, transform) },
                    onLogin = { vm.login(site) },
                    onLogout = { vm.logout(site) },
                )
            }
        }
        item(key = "sync") {
            SettingsGroup(title = stringResource(R.string.settings_sync)) {
                SettingsSwitch(
                    stringResource(R.string.sync_saved),
                    sync.syncSaved,
                    { vm.setSyncPrefs(sync.copy(syncSaved = it)) },
                    subtitle = stringResource(R.string.sync_saved_hint),
                )
                SettingsDivider()
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

@Composable
private fun AccountBlock(
    site: SiteConfig,
    account: StoredAccount?,
    form: LoginForm,
    onEdit: ((LoginForm) -> LoginForm) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    if (account != null && !account.invalid) {
        val limit = account.info.tagLimit?.toString() ?: stringResource(R.string.account_tag_limit_unlimited)
        SettingsRow(
            title = stringResource(R.string.account_signed_in_as, account.info.login),
            subtitle = listOfNotNull(
                account.info.levelName?.let { stringResource(R.string.account_level, it) },
                stringResource(R.string.account_tag_limit, limit),
            ).joinToString(" · "),
            icon = DudeIcons.Check,
            trailing = { TextButton(onClick = onLogout) { Text(stringResource(R.string.account_sign_out)) } },
        )
        return
    }
    SettingsBlock {
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
                Text(stringResource(R.string.account_checking), style = MaterialTheme.typography.bodySmall)
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
        item(key = "gestures") {
            SettingsGroup {
                SettingsSwitch(
                    stringResource(R.string.settings_double_tap),
                    prefs.doubleTapLike,
                    { vm.setFeedPrefs(prefs.copy(doubleTapLike = it)) },
                    subtitle = stringResource(R.string.settings_double_tap_hint),
                )
                SettingsDivider()
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

    SettingsPageScaffold(stringResource(R.string.settings_content), onBack) {
        item(key = "mode") {
            SettingsGroup(title = stringResource(R.string.settings_mode), footer = stringResource(R.string.settings_mode_hint)) {
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
        item(key = "censor") {
            SettingsGroup(title = stringResource(R.string.settings_censor_group)) {
                SettingsSwitch(
                    stringResource(R.string.settings_censor),
                    enabled,
                    vm::setCensorEnabled,
                    subtitle = stringResource(R.string.settings_censor_hint),
                )
                AnimatedVisibility(enabled) {
                    Column {
                        SettingsDivider()
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
                                modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp)),
                            )
                            Text(stringResource(R.string.censor_strength), style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = strength,
                                onValueChange = { strength = it },
                                onValueChangeFinished = { vm.setCensorPrefs(prefs.copy(strength = strength)) },
                            )
                        }
                        SettingsDivider()
                        SettingsSwitch(stringResource(R.string.censor_in_viewer), prefs.inViewer, { vm.setCensorPrefs(prefs.copy(inViewer = it)) })
                        SettingsDivider()
                        SettingsSwitch(stringResource(R.string.censor_sensitive), prefs.blurSensitive, { vm.setCensorPrefs(prefs.copy(blurSensitive = it)) })
                    }
                }
            }
        }
        item(key = "hidden") {
            SettingsGroup(title = stringResource(R.string.settings_hidden)) {
                SettingsRow(
                    title = stringResource(R.string.drawer_negative_tags),
                    subtitle = stringResource(R.string.settings_negative_tags_hint),
                    icon = DudeIcons.Hide,
                    onClick = onOpenNegativeTags,
                )
                SettingsDivider()
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

// --- оформление ---

@Composable
private fun LookPage(onBack: () -> Unit, onOpenThemes: () -> Unit, onOpenIcons: () -> Unit) {
    val context = LocalContext.current
    // Страница собирается заново после выбора иконки или языка — значение всегда свежее.
    val icon = remember { IconManager.current(context) }
    SettingsPageScaffold(stringResource(R.string.settings_look), onBack) {
        item(key = "look") {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.drawer_themes),
                    subtitle = stringResource(R.string.settings_look_themes_hint),
                    icon = DudeIcons.Palette,
                    tint = SectionColors.Look,
                    onClick = onOpenThemes,
                )
                SettingsDivider(inset = SectionInset)
                SettingsRow(
                    title = stringResource(R.string.icon_picker_title),
                    subtitle = stringResource(icon.label),
                    leading = { IconPreview(icon, Modifier.size(36.dp), RoundedCornerShape(11.dp)) },
                    onClick = onOpenIcons,
                )
                // Язык приложения отдельно от системного — с Android 13, в системном окне.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SettingsDivider(inset = SectionInset)
                    SettingsRow(
                        title = stringResource(R.string.settings_language),
                        subtitle = appLanguage(context) ?: stringResource(R.string.settings_language_system),
                        icon = DudeIcons.Translate,
                        tint = SectionColors.Language,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Язык, выбранный для приложения в Android 13+, или null — «как в системе». */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun appLanguage(context: android.content.Context): String? {
    val locales = context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales ?: return null
    if (locales.isEmpty) return null
    val locale = locales[0]
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
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
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = openSystem),
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
                SettingsSwitch(
                    stringResource(R.string.notify_artists_switch),
                    artists,
                    {
                        if (it) onAskNotifications()
                        vm.setNotifyArtists(it)
                    },
                    subtitle = stringResource(R.string.notify_artists_switch_hint),
                )
                if (BuildConfig.UPDATE_CHECK) {
                    SettingsDivider()
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
        item(key = "system") {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.notify_system_settings),
                    subtitle = stringResource(R.string.notify_system_hint),
                    icon = DudeIcons.Out,
                    onClick = openSystem,
                    chevron = false,
                )
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
                val folderLabel = prefs.treeUri?.let { Uri.parse(it).lastPathSegment?.substringAfter(':') } ?: stringResource(R.string.dl_folder_default)
                SettingsRow(
                    title = stringResource(R.string.dl_folder),
                    subtitle = folderLabel,
                    icon = DudeIcons.Folder,
                    onClick = { treePicker.launch(null) },
                    trailing = if (prefs.treeUri != null) {
                        { TextButton(onClick = { vm.setDownloadPrefs(prefs.copy(treeUri = null)) }) { Text(stringResource(R.string.dl_folder_reset)) } }
                    } else {
                        null
                    },
                )
            }
        }
        item(key = "name") {
            SettingsGroup(title = stringResource(R.string.dl_template), footer = stringResource(R.string.dl_template_vars)) {
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
        item(key = "rules") {
            SettingsGroup(title = stringResource(R.string.dl_group_download)) {
                SettingsSwitch(
                    stringResource(R.string.dl_wifi_only),
                    prefs.wifiOnly,
                    { vm.setDownloadPrefs(prefs.copy(wifiOnly = it)) },
                    subtitle = stringResource(R.string.dl_wifi_only_hint),
                )
                SettingsDivider()
                SettingsSwitch(
                    stringResource(R.string.dl_write_tags),
                    prefs.writeTags,
                    { vm.setDownloadPrefs(prefs.copy(writeTags = it)) },
                    subtitle = stringResource(R.string.dl_write_tags_hint),
                )
                SettingsDivider()
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
        item(key = "doh") {
            SettingsGroup(title = stringResource(R.string.doh_title), footer = stringResource(R.string.doh_note)) {
                DohProvider.entries.forEach { provider ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = provider == doh, role = Role.RadioButton, onClick = { vm.setDoh(provider) })
                            .padding(horizontal = 16.dp, vertical = 6.dp),
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

// --- о приложении ---

/** Иконка и версия (семь тапов по ним открывают игру), проверка обновлений, что нового. */
@Composable
private fun AboutPage(updates: UpdateController?, onBack: () -> Unit, onOpenGame: () -> Unit) {
    val context = LocalContext.current
    val icon = remember { IconManager.current(context) }
    var taps by remember { mutableIntStateOf(0) }
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
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconPreview(
                    icon,
                    Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = egg),
                    RoundedCornerShape(26.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = egg).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (updates != null && BuildConfig.UPDATE_CHECK) {
            item(key = "updates") {
                val state by updates.state.collectAsStateWithLifecycle()
                val busy = state is UpdateState.Checking || state is UpdateState.Downloading
                val available = state as? UpdateState.Available
                SettingsGroup {
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
                        icon = if (available != null) DudeIcons.Download else DudeIcons.Refresh,
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
                        chevron = false,
                    )
                }
            }
        }
        item(key = "news") {
            SettingsGroup(title = stringResource(R.string.about_whats_new, BuildConfig.VERSION_NAME)) {
                SettingsBlock {
                    Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
