@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.account.StoredAccount
import app.dudebooru.data.net.ProxyConfig
import app.dudebooru.data.settings.CensorPrefs
import app.dudebooru.data.settings.CensorStyle
import app.dudebooru.data.settings.DownloadPrefs
import app.dudebooru.data.settings.FeedPrefs
import app.dudebooru.data.settings.SyncPrefs
import app.dudebooru.ui.icons.DudeIcons

@Composable
fun AccountsScreen(vm: AccountsViewModel, onBack: () -> Unit, onOpenNegativeTags: () -> Unit = {}) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val forms by vm.forms.collectAsStateWithLifecycle()
    val proxy by vm.proxy.collectAsStateWithLifecycle()
    val feedPrefs by vm.feedPrefs.collectAsStateWithLifecycle()
    val censorEnabled by vm.censorEnabled.collectAsStateWithLifecycle()
    val censorPrefs by vm.censorPrefs.collectAsStateWithLifecycle()
    val showHidden by vm.showHiddenCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile") { ProfileCard(vm) }
            item(key = "sync") {
                val sync by vm.syncPrefs.collectAsStateWithLifecycle()
                SyncCard(sync, vm::setSyncPrefs)
            }
            item(key = "downloads") {
                val dl by vm.downloadPrefs.collectAsStateWithLifecycle()
                DownloadsCard(vm, dl, vm::setDownloadPrefs)
            }
            item(key = "content") {
                ContentCard(censorEnabled, censorPrefs, showHidden, vm::setCensorEnabled, vm::setCensorPrefs, vm::setShowHiddenCount, onOpenNegativeTags)
            }
            item(key = "feed") { FeedCard(feedPrefs, vm::setFeedPrefs) }
            item {
                Text(
                    stringResource(R.string.accounts_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(vm.groups, key = { it.accountGroup }) { site ->
                AccountCard(
                    site = site,
                    shared = vm.sharedWith(site),
                    account = accounts[site.accountGroup],
                    form = forms[site.accountGroup] ?: LoginForm(),
                    onEdit = { transform -> vm.edit(site, transform) },
                    onLogin = { vm.login(site) },
                    onLogout = { vm.logout(site) },
                )
            }
            item(key = "network") { NetworkCard(proxy, vm::saveProxy) }
            item(key = "doh") {
                val doh by vm.doh.collectAsStateWithLifecycle()
                DohCard(doh, vm::setDoh)
            }
        }
    }
}

@Composable
private fun AccountCard(
    site: SiteConfig,
    shared: List<SiteConfig>,
    account: StoredAccount?,
    form: LoginForm,
    onEdit: ((LoginForm) -> LoginForm) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(site.name, style = MaterialTheme.typography.titleMedium)
            if (shared.isNotEmpty()) {
                Text(
                    stringResource(R.string.account_shared_with, shared.joinToString { it.name }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (account != null && !account.invalid) {
                Text(stringResource(R.string.account_signed_in_as, account.info.login))
                account.info.levelName?.let { Text(stringResource(R.string.account_level, it), style = MaterialTheme.typography.bodySmall) }
                val limit = account.info.tagLimit?.toString() ?: stringResource(R.string.account_tag_limit_unlimited)
                Text(stringResource(R.string.account_tag_limit, limit), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.account_sign_out)) }
            } else {
                if (account?.invalid == true) {
                    Text(stringResource(R.string.account_invalid), color = MaterialTheme.colorScheme.error)
                }
                LoginFields(site, form.copy(login = form.login.ifEmpty { account?.info?.login.orEmpty() }), onEdit, onLogin)
            }
        }
    }
}

@Composable
private fun LoginFields(site: SiteConfig, form: LoginForm, onEdit: ((LoginForm) -> LoginForm) -> Unit, onLogin: () -> Unit) {
    val isDanbooru = site.engine == EngineType.DANBOORU
    OutlinedTextField(
        value = form.login,
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

/** Свой HTTP/SOCKS5-прокси для всех сайтов — если сайт не открывается напрямую из вашей сети. */
@Composable
private fun NetworkCard(current: ProxyConfig, onSave: (ProxyConfig) -> Unit) {
    var type by rememberSaveable(current) { mutableStateOf(current.type) }
    var host by rememberSaveable(current) { mutableStateOf(current.host) }
    var port by rememberSaveable(current) { mutableStateOf(if (current.port > 0) current.port.toString() else "") }
    val types = remember { ProxyConfig.Type.entries }
    val draft = ProxyConfig(type, host.trim(), port.toIntOrNull() ?: 0)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.network_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, entry ->
                    SegmentedButton(
                        selected = entry == type,
                        onClick = { type = entry },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) {
                        Text(
                            when (entry) {
                                ProxyConfig.Type.NONE -> stringResource(R.string.network_direct)
                                ProxyConfig.Type.HTTP -> "HTTP"
                                ProxyConfig.Type.SOCKS -> "SOCKS5"
                            },
                        )
                    }
                }
            }
            if (type != ProxyConfig.Type.NONE) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.network_host)) },
                    singleLine = true,
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
            Button(onClick = { onSave(draft) }, enabled = valid && draft != current) {
                Text(stringResource(R.string.network_save))
            }
        }
    }
}

/** Настройки → Лента: посты или сетка, двойной тап, анимация GIF. */
@Composable
private fun FeedCard(prefs: FeedPrefs, onChange: (FeedPrefs) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_feed), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !prefs.grid, onClick = { onChange(prefs.copy(grid = false)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.settings_feed_posts))
                }
                SegmentedButton(selected = prefs.grid, onClick = { onChange(prefs.copy(grid = true)) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.settings_feed_grid))
                }
            }
            SwitchRow(stringResource(R.string.settings_double_tap), prefs.doubleTapLike) { onChange(prefs.copy(doubleTapLike = it)) }
            SwitchRow(stringResource(R.string.settings_animate_gifs), prefs.animateGifs) { onChange(prefs.copy(animateGifs = it)) }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Настройки → Контент: цензура NSFW и негативные теги. Режим SFW / NSFW / Всё — в боковом меню. */
@Composable
private fun ContentCard(
    enabled: Boolean,
    prefs: CensorPrefs,
    showHidden: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPrefs: (CensorPrefs) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onOpenNegativeTags: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_content), style = MaterialTheme.typography.titleMedium)
            SwitchRow(stringResource(R.string.settings_censor), enabled, onEnabled)
            if (enabled) {
                val styles = CensorStyle.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    styles.forEachIndexed { i, style ->
                        SegmentedButton(selected = prefs.style == style, onClick = { onPrefs(prefs.copy(style = style)) }, shape = SegmentedButtonDefaults.itemShape(i, styles.size)) {
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
                Text(stringResource(R.string.censor_strength), style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Slider(value = prefs.strength, onValueChange = { onPrefs(prefs.copy(strength = it)) })
                SwitchRow(stringResource(R.string.censor_in_viewer), prefs.inViewer) { onPrefs(prefs.copy(inViewer = it)) }
                SwitchRow(stringResource(R.string.censor_sensitive), prefs.blurSensitive) { onPrefs(prefs.copy(blurSensitive = it)) }
            }
            SwitchRow(stringResource(R.string.settings_show_hidden), showHidden, onShowHidden)
            OutlinedButton(onClick = onOpenNegativeTags) { Text(stringResource(R.string.drawer_negative_tags)) }
        }
    }
}

/** Настройки → Аккаунты и профиль: локальные имя, @ник и аватарка — на сайты не отправляются. */
@Composable
private fun ProfileCard(vm: AccountsViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }
    var nick by rememberSaveable(profile?.nick) { mutableStateOf(profile?.nick.orEmpty()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(vm::setAvatarFromGallery) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.dudebooru.ui.main.Avatar(profile?.avatarUrl, Modifier.size(56.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    androidx.compose.material3.TextButton(onClick = {
                        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.profile_avatar_pick)) }
                    if (profile?.avatarUrl != null) {
                        androidx.compose.material3.TextButton(onClick = vm::resetAvatar) { Text(stringResource(R.string.profile_avatar_reset)) }
                    }
                }
            }
            Text(stringResource(R.string.profile_avatar_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = nick, onValueChange = { nick = it.removePrefix("@") }, label = { Text(stringResource(R.string.profile_nick)) }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.setProfile(name, nick) }, enabled = name != profile?.name || nick != profile?.nick) { Text(stringResource(R.string.save)) }
        }
    }
}

/** Что уходит на сайт: сохранённые синхронизируются по умолчанию, лайки — только по желанию. */
@Composable
private fun SyncCard(prefs: SyncPrefs, onChange: (SyncPrefs) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_sync), style = MaterialTheme.typography.titleMedium)
            SwitchRow(stringResource(R.string.sync_saved), prefs.syncSaved) { onChange(prefs.copy(syncSaved = it)) }
            Text(stringResource(R.string.sync_saved_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchRow(stringResource(R.string.mirror_likes), prefs.mirrorLikes) { onChange(prefs.copy(mirrorLikes = it)) }
            Text(stringResource(R.string.mirror_likes_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Настройки → Скачивание: папка (можно на SD-карту), шаблон имени с предпросмотром, Wi-Fi, параллельность, теги. */
@Composable
private fun DownloadsCard(vm: AccountsViewModel, prefs: DownloadPrefs, onChange: (DownloadPrefs) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val treePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            onChange(prefs.copy(treeUri = uri.toString()))
        }
    }
    var template by rememberSaveable(prefs.template) { mutableStateOf(prefs.template) }
    val preview = remember(template) {
        runCatching { app.dudebooru.booru.download.NameTemplate.render(template, vm.samplePost(), "jpg") }.getOrDefault("")
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_downloads), style = MaterialTheme.typography.titleMedium)
            val folderLabel = prefs.treeUri?.let { android.net.Uri.parse(it).lastPathSegment?.substringAfter(':') } ?: stringResource(R.string.dl_folder_default)
            Text(stringResource(R.string.dl_folder, folderLabel), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { treePicker.launch(null) }) { Text(stringResource(R.string.dl_folder_pick)) }
                if (prefs.treeUri != null) OutlinedButton(onClick = { onChange(prefs.copy(treeUri = null)) }) { Text(stringResource(R.string.dl_folder_reset)) }
            }
            OutlinedTextField(
                value = template,
                onValueChange = { template = it },
                label = { Text(stringResource(R.string.dl_template)) },
                supportingText = { Text(stringResource(R.string.dl_template_preview, preview)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                app.dudebooru.booru.download.NameTemplate.VARIABLES.joinToString(" ") { "{$it}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (template != prefs.template) {
                Button(onClick = { onChange(prefs.copy(template = template.ifBlank { DownloadPrefs.DEFAULT_TEMPLATE })) }) { Text(stringResource(R.string.save)) }
            }
            SwitchRow(stringResource(R.string.dl_wifi_only), prefs.wifiOnly) { onChange(prefs.copy(wifiOnly = it)) }
            SwitchRow(stringResource(R.string.dl_write_tags), prefs.writeTags) { onChange(prefs.copy(writeTags = it)) }
            Text(stringResource(R.string.dl_parallel, prefs.parallel), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.Slider(
                value = prefs.parallel.toFloat(),
                onValueChange = { onChange(prefs.copy(parallel = it.toInt().coerceIn(1, 4))) },
                valueRange = 1f..4f,
                steps = 2,
            )
        }
    }
}

/** DNS-over-HTTPS — если провайдер режет DNS-ответы для сайта («адрес не найден»). */
@Composable
private fun DohCard(current: app.dudebooru.data.net.DohProvider, onChange: (app.dudebooru.data.net.DohProvider) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.doh_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.doh_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            app.dudebooru.data.net.DohProvider.entries.forEach { provider ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChange(provider) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = provider == current, onClick = { onChange(provider) })
                    Text(
                        when (provider) {
                            app.dudebooru.data.net.DohProvider.NONE -> stringResource(R.string.doh_system)
                            app.dudebooru.data.net.DohProvider.CLOUDFLARE -> "Cloudflare"
                            app.dudebooru.data.net.DohProvider.GOOGLE -> "Google"
                            app.dudebooru.data.net.DohProvider.QUAD9 -> "Quad9"
                            app.dudebooru.data.net.DohProvider.ADGUARD -> "AdGuard"
                        },
                    )
                }
            }
        }
    }
}
