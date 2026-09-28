@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.ui.common.label
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.icons.DudeIcons

/** Иконка кнопки показывает текущий выбор. */
fun SortOrder.icon(): ImageVector = when (this) {
    SortOrder.NEW -> DudeIcons.Clock
    SortOrder.HOT -> DudeIcons.Fire
    SortOrder.POPULAR_DAY, SortOrder.POPULAR_WEEK, SortOrder.POPULAR_MONTH, SortOrder.POPULAR_YEAR -> DudeIcons.Trending
    SortOrder.BEST -> DudeIcons.Star
    SortOrder.FAVCOUNT -> DudeIcons.Heart
    SortOrder.MPIXELS -> DudeIcons.Expand
    SortOrder.LANDSCAPE -> DudeIcons.Landscape
    SortOrder.PORTRAIT -> DudeIcons.Portrait
    SortOrder.RANDOM -> DudeIcons.Shuffle
}

@Composable
fun SortButton(controller: FeedController) {
    var open by remember { mutableStateOf(false) }
    val current by controller.sort.collectAsStateWithLifecycle()
    IconButton(onClick = { open = true }) {
        Icon(current.icon(), contentDescription = stringResource(R.string.sort) + ": " + stringResource(current.label()))
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    stringResource(R.string.sort),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                // Пункты, которые источник не поддерживает, не показываются.
                controller.supportedSorts.forEach { sort ->
                    val selected = sort == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                open = false
                                controller.setSort(sort)
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(sort.icon(), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(20.dp))
                        Text(
                            stringResource(sort.label()),
                            modifier = Modifier.weight(1f),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (selected) Icon(DudeIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
