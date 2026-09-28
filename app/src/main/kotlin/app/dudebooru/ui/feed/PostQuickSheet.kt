@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.booru.model.Post
import app.dudebooru.ui.icons.DudeIcons
import coil3.compose.AsyncImage

/**
 * Долгое нажатие на пост: не мгновенное скачивание, а лист быстрых действий, как контекстное меню в Telegram.
 * Оригинал по-прежнему в два касания: зажать → «Оригинал».
 */
@Composable
fun PostQuickSheet(post: Post, group: List<Post>, actions: PostActions, onDismiss: () -> Unit) {
    val collections = LocalCollections.current
    val censor = LocalCensor.current
    val saved = post.key in collections.saved
    val act = { action: () -> Unit ->
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            // Шапка: превью (закрытое, если пост под цензурой), художник, размеры.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (censor.hides(post)) {
                        Icon(DudeIcons.Hide, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        AsyncImage(
                            model = post.previewUrl ?: post.sampleUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        post.tags.artist.firstOrNull()?.replace('_', ' ') ?: "#${post.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            "${post.width}×${post.height}",
                            post.fileExt?.uppercase(),
                            formatSize(post.fileSize),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickButton(DudeIcons.Download, stringResource(R.string.quick_original)) { act { actions.download(post, original = true) } }
                QuickButton(if (saved) DudeIcons.SaveFilled else DudeIcons.Save, stringResource(if (saved) R.string.quick_saved else R.string.quick_save)) {
                    act { actions.toggleSave(post) }
                }
                QuickButton(DudeIcons.Share, stringResource(R.string.quick_share)) { act { actions.share(post) } }
                QuickButton(DudeIcons.Spark, stringResource(R.string.quick_similar)) { act { actions.findSimilar(post) } }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    QuickRow(DudeIcons.Download, stringResource(R.string.menu_download), formatSize(post.lightSize)) {
                        act { actions.download(post, original = false) }
                    }
                    if (group.size > 1) {
                        QuickRow(DudeIcons.Download, stringResource(R.string.menu_download_all, group.size)) { act { actions.downloadAll(group) } }
                    }
                    if (post.tags.artist.isNotEmpty()) {
                        QuickRow(DudeIcons.User, stringResource(R.string.menu_artist)) { act { actions.openArtist(post) } }
                    }
                    QuickRow(DudeIcons.Link, stringResource(R.string.menu_copy_link)) { act { actions.copyLink(post) } }
                    QuickRow(DudeIcons.Copy, stringResource(R.string.menu_copy_image)) { act { actions.copyImage(post) } }
                    QuickRow(DudeIcons.Hide, stringResource(R.string.menu_not_interested)) { act { actions.notInterested(post) } }
                    QuickRow(DudeIcons.Out, stringResource(R.string.menu_open_site)) { act { actions.openOnSite(post) } }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickRow(icon: ImageVector, text: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
