package app.dudebooru.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.booru.engine.PoolInfo
import app.dudebooru.booru.model.Post
import app.dudebooru.ui.icons.DudeIcons
import coil3.compose.AsyncImage
import java.util.Locale

/** Аватарка художника: вырезка из его самой популярной работы; пока грузится — буква на цветном круге. */
@Composable
fun ArtistAvatar(post: Post, size: Dp = 36.dp, actions: PostActions? = LocalPostActions.current) {
    val artist = post.tags.artist.firstOrNull()
    val url by produceState(initialValue = actions?.cachedAvatar(post), post.site, artist) {
        if (value == null && artist != null && actions != null) value = actions.loadAvatar(post)
    }
    Box(Modifier.size(size).clip(CircleShape).background(avatarColor(artist ?: "?")), contentAlignment = Alignment.Center) {
        Text(
            (artist?.firstOrNull { it.isLetterOrDigit() } ?: '?').uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (url != null) {
            // Вырезка из работы в NSFW-режиме тоже может быть откровенной: при цензуре — размытое пятно.
            val censored = LocalCensor.current.let { it.enabled && it.mode != app.dudebooru.booru.model.ContentMode.SFW }
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(context).data(url).apply { if (censored) size(10) }.build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

val LocalPostActions = androidx.compose.runtime.staticCompositionLocalOf<PostActions?> { null }

private val AvatarPalette = listOf(
    Color(0xFF2F6FDE), Color(0xFF8E44AD), Color(0xFF1E8A4C), Color(0xFFC0392B),
    Color(0xFFD9822B), Color(0xFF2BB5A8), Color(0xFF6D4C9F), Color(0xFF3D7EA6),
)

/** Цвет стабильный, от хеша имени. */
fun avatarColor(name: String): Color = AvatarPalette[Math.floorMod(name.hashCode(), AvatarPalette.size)]

/** «1.2 МБ», «18 МБ», «640 КБ». */
fun formatSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 10 -> String.format(Locale.ROOT, "%.0f МБ", mb)
        mb >= 1 -> String.format(Locale.ROOT, "%.1f МБ", mb)
        else -> String.format(Locale.ROOT, "%.0f КБ", kb)
    }
}

/** Меню ⋮ — одно и то же в ленте и в просмотре. */
@Composable
fun PostMenuButton(post: Post, group: List<Post>, actions: PostActions, tint: Color = MaterialTheme.colorScheme.primary) {
    var open by remember { mutableStateOf(false) }
    var pools by remember(post.key) { mutableStateOf<List<PoolInfo>?>(null) }
    val collections = LocalCollections.current

    if (open) {
        LaunchedEffect(post.key) { if (pools == null) pools = runCatching { actions.pools(post) }.getOrDefault(emptyList()) }
    }
    // Меню привязано к самой кнопке ⋮, а не к строке, в которой она стоит.
    Box {
        IconButton(onClick = { open = true }) { Icon(DudeIcons.Dots, stringResource(R.string.post_actions), tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            val lightHint = formatSize(post.lightSize)
            val originalHint = listOfNotNull(post.fileExt?.uppercase(), formatSize(post.fileSize)).joinToString(" · ").ifEmpty { null }
            MenuRow(DudeIcons.Download, stringResource(R.string.menu_download), lightHint) { close(); actions.download(post, original = false) }
            MenuRow(DudeIcons.Download, stringResource(R.string.menu_download_original), originalHint) { close(); actions.download(post, original = true) }
            if (group.size > 1) {
                MenuRow(DudeIcons.Download, stringResource(R.string.menu_download_all, group.size)) { close(); actions.downloadAll(group) }
            }
            val saved = post.key in collections.saved
            MenuRow(if (saved) DudeIcons.SaveFilled else DudeIcons.Save, stringResource(if (saved) R.string.menu_unsave else R.string.menu_save)) {
                close(); actions.toggleSave(post)
            }
            if (post.tags.artist.isNotEmpty()) {
                MenuRow(DudeIcons.User, stringResource(R.string.menu_artist)) { close(); actions.openArtist(post) }
            }
            // «К пулу» есть, только если пост входит в пул.
            pools.orEmpty().take(3).forEach { pool ->
                MenuRow(DudeIcons.Image, stringResource(R.string.menu_pool), pool.displayName.take(24)) { close(); actions.openPool(post, pool) }
            }
            HorizontalDivider()
            MenuRow(DudeIcons.Link, stringResource(R.string.menu_copy_link)) { close(); actions.copyLink(post) }
            MenuRow(DudeIcons.Copy, stringResource(R.string.menu_copy_image)) { close(); actions.copyImage(post) }
            MenuRow(DudeIcons.Copy, stringResource(R.string.menu_copy_direct)) { close(); actions.copyDirectLink(post) }
            MenuRow(DudeIcons.Search, stringResource(R.string.menu_similar)) { close(); actions.findSimilar(post) }
            MenuRow(DudeIcons.Hide, stringResource(R.string.menu_not_interested)) { close(); actions.notInterested(post) }
            MenuRow(DudeIcons.User, stringResource(R.string.menu_make_avatar)) { close(); actions.makeAvatar(post) }
            MenuRow(DudeIcons.Palette, stringResource(R.string.menu_theme_background)) { close(); actions.useAsBackground(post) }
            MenuRow(DudeIcons.Out, stringResource(R.string.menu_open_site)) { close(); actions.openOnSite(post) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, hint: String? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, null) },
        text = { Text(text) },
        trailingIcon = hint?.let { { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        onClick = onClick,
    )
}
