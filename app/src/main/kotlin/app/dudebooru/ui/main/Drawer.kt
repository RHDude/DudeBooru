package app.dudebooru.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.ui.common.label
import app.dudebooru.ui.icons.DudeIcons
import coil3.compose.AsyncImage

/** Боковое меню по макету: шапка с аватаркой и темой, быстрые переключатели, пункты. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DudeDrawer(vm: MainViewModel, dark: Boolean, onNavigate: (Route) -> Unit, onCloseApp: () -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val censor by vm.censor.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val savedCount by vm.savedCount.collectAsStateWithLifecycle()
    val artistsNew by vm.artistsWithNew.collectAsStateWithLifecycle()
    val recsNew by vm.recsNew.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    var accountsOpen by rememberSaveable { mutableStateOf(false) }

    val look = app.dudebooru.ui.theme.LocalAppTheme.current
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Box {
            // Фон меню из темы — тот же, что у ленты.
            if (look.background.kind != app.dudebooru.ui.theme.ThemeBackground.Kind.NONE) {
                app.dudebooru.ui.theme.ThemeBackdrop(look.background, Modifier.matchParentSize())
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                // --- шапка ---
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)) {
                    Column {
                        Avatar(profile?.avatarUrl, Modifier.size(64.dp).clickable { onNavigate(Route.Settings) })
                        Spacer(Modifier.height(12.dp))
                        Text(profile?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth().clickable { accountsOpen = !accountsOpen }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "@" + profile?.nick.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (accountsOpen) DudeIcons.ChevronUp else DudeIcons.ChevronDown, stringResource(R.string.drawer_accounts))
                        }
                    }
                    // Солнце в тёмной теме, луна в светлой: иконка показывает, куда переключит.
                    val reveal = app.dudebooru.ui.theme.LocalThemeReveal.current
                    val revealScope = androidx.compose.runtime.rememberCoroutineScope()
                    var buttonCenter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(44.dp)
                            .clip(CircleShape)
                            .onGloballyPositioned { buttonCenter = it.boundsInRoot().center }
                            .combinedClickable(
                                onClick = { revealScope.launch { reveal.run(buttonCenter) { vm.toggleTheme(dark) } } },
                                onLongClick = { onNavigate(Route.Themes) },
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (dark) DudeIcons.Sun else DudeIcons.Moon, stringResource(R.string.drawer_toggle_theme), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Кружево темы Maid под шапкой меню.
                if (look.pattern == app.dudebooru.ui.theme.ThemePattern.LACE) {
                    app.dudebooru.ui.face.LaceEdge(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 12.dp))
                    Spacer(Modifier.height(8.dp))
                }

                // --- ▾ аккаунты сайтов ---
                if (accountsOpen) {
                    Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        vm.c.registry.sites.filter { it.supportsLogin }.distinctBy { it.accountGroup }.forEach { site ->
                            val account = accounts[site.accountGroup]?.takeIf { !it.invalid }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(site.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    account?.info?.login ?: stringResource(R.string.drawer_not_signed_in),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (account != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { onNavigate(Route.Settings) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(DudeIcons.Plus, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.drawer_sign_in), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // --- быстрые переключатели: то, что чаще всего меняют ---
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        ContentMode.entries.forEachIndexed { i, entry ->
                            SegmentedButton(
                                selected = entry == mode,
                                onClick = { vm.setMode(entry) },
                                shape = SegmentedButtonDefaults.itemShape(i, ContentMode.entries.size),
                                icon = {},
                            ) { Text(stringResource(entry.label()), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (censor) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.toggleCensor() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (censor) DudeIcons.Hide else DudeIcons.Eye,
                                stringResource(if (censor) R.string.drawer_censor_on else R.string.drawer_censor_off),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                DrawerItem(DudeIcons.User, R.string.drawer_profile) { onNavigate(Route.Profile) }
                DrawerItem(DudeIcons.Spark, R.string.drawer_recommendations, badge = if (recsNew) "new" else null, highlight = true) {
                    onNavigate(Route.Recs)
                }
                DrawerItem(DudeIcons.Save, R.string.drawer_saved, badge = savedCount.takeIf { it > 0 }?.toString()) {
                    onNavigate(Route.Saved)
                }
                DrawerItem(DudeIcons.Users, R.string.drawer_artists, badge = artistsNew.takeIf { it > 0 }?.toString(), highlight = true) {
                    onNavigate(Route.Artists)
                }
                DrawerItem(
                    DudeIcons.Download,
                    R.string.drawer_downloads,
                    badge = downloadProgress?.let { (done, total) -> stringResource(R.string.dl_progress, done, total) },
                ) { onNavigate(Route.Downloads) }
                DrawerItem(DudeIcons.History, R.string.drawer_history) { onNavigate(Route.History) }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                DrawerItem(DudeIcons.Shuffle, R.string.drawer_random) { vm.openRandom()?.let(onNavigate) }
                DrawerItem(DudeIcons.Hide, R.string.drawer_negative_tags) { onNavigate(Route.NegativeTags) }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                DrawerItem(DudeIcons.Palette, R.string.drawer_themes) { onNavigate(Route.Themes) }
                DrawerItem(DudeIcons.Gear, R.string.drawer_settings) { onNavigate(Route.Settings) }
                DrawerItem(DudeIcons.Off, R.string.drawer_close_app, onClick = onCloseApp)
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: Int, badge: String? = null, highlight: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(stringResource(label)) },
        badge = badge?.let {
            {
                if (highlight) {
                    app.dudebooru.ui.main.CountBadge(it, highlighted = true)
                } else {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).height(48.dp),
    )
}

/** Своя аватарка из поста или галереи; пока её нет — градиентный круг. */
@Composable
fun Avatar(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF7FD6CC), Color(0xFF7B4FD1))))) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
