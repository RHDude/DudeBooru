@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.rec

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dudebooru.R
import app.dudebooru.booru.model.Post
import app.dudebooru.ui.feed.FeedController
import app.dudebooru.ui.feed.FeedList
import app.dudebooru.ui.feed.PostActions
import app.dudebooru.ui.icons.DudeIcons
import app.dudebooru.ui.main.MainViewModel

/**
 * «Рекомендации»: подборка по лайкам и сохранённым, разложенная по папкам-источникам.
 * Пока лайков меньше 10 — популярное за неделю с плашкой «Лайкните ещё N артов».
 */
@Composable
fun RecsScreen(vm: MainViewModel, actions: PostActions, onBack: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val likes by vm.likeCount.collectAsStateWithLifecycle()
    var siteId by rememberSaveable { mutableStateOf(vm.selected.value ?: folders.firstOrNull()?.id.orEmpty()) }
    val cold = likes < vm.c.recs.coldStartLikes
    LaunchedEffect(Unit) { vm.markRecsSeen() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.drawer_recommendations)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(folders, key = { it.id }) { folder ->
                    FilterChip(selected = folder.id == siteId, onClick = { siteId = folder.id }, label = { Text(folder.name) })
                }
            }
            if (siteId.isEmpty()) return@Column
            val controller = remember(siteId, cold) { if (cold) vm.coldStartFeed(siteId) else vm.recsFeed(siteId) }
            LaunchedEffect(controller) { controller.ensureLoaded() }
            FeedList(controller, actions, grid = false) {
                if (cold) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("(๑•̀ㅂ•́)و✧", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            val left = (vm.c.recs.coldStartLikes - likes).coerceAtLeast(1)
                            Text(
                                androidx.compose.ui.res.pluralStringResource(R.plurals.recs_cold, left, left),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** «Найти похожие»: лента похожих по тегам и та же картинка на других источниках. */
@Composable
fun SimilarScreen(vm: MainViewModel, controller: FeedController, post: Post, actions: PostActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val elsewhere by produceState<List<Post>>(emptyList(), post.key) { value = vm.c.recs.sameImageElsewhere(post) }
    LaunchedEffect(controller) { controller.ensureLoaded() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(DudeIcons.Back, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.similar_title)) },
            )
        },
    ) { padding ->
        FeedList(controller, actions, modifier = Modifier.padding(padding), grid = true) {
            if (elsewhere.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.similar_same_image), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(elsewhere, key = { it.key }) { other ->
                            val site = vm.c.registry.site(other.site)
                            AssistChip(
                                onClick = {
                                    val url = site?.let { vm.c.registry.engine(it).postUrl(other) } ?: return@AssistChip
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                                label = { Text(site?.name ?: other.site) },
                            )
                        }
                    }
                }
            }
        }
    }
}
