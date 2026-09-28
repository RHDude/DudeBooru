package app.dudebooru.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.update.ReleaseInfo
import app.dudebooru.notify.Notifications
import app.dudebooru.ui.feed.formatSize

/** «Вышла версия X»: что нового, «Скачать и установить», прогресс загрузки и понятные ошибки. */
@Composable
fun UpdateDialog(controller: UpdateController) {
    val open by controller.dialog.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    if (!open) return
    val release: ReleaseInfo = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = controller::closeDialog,
        title = { Text(stringResource(R.string.update_available_title, release.version)) },
        text = {
            Column {
                Text(
                    Notifications.plainNotes(release.notes).ifBlank { stringResource(R.string.update_notify_text) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
                formatSize(release.apkSize.takeIf { it > 0 })?.let { size ->
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_size, size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(12.dp))
                        val progress = s.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateState.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.Available -> s.hint?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            val downloading = state is UpdateState.Downloading
            if (release.apkUrl != null) {
                TextButton(onClick = controller::install, enabled = !downloading) {
                    Text(stringResource(if (downloading) R.string.update_downloading else R.string.update_install))
                }
            } else {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))) }
                    controller.closeDialog()
                }) { Text(stringResource(R.string.update_open_page)) }
            }
        },
        dismissButton = { TextButton(onClick = controller::closeDialog) { Text(stringResource(R.string.update_later)) } },
    )
}
