@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import app.dudebooru.data.settings.FeedPrefs
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
            TopAppBar(
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
