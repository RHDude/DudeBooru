package app.dudebooru.ui.feed

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import app.dudebooru.AppContainer
import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.engine.PageKey
import app.dudebooru.booru.engine.QueryPlan
import app.dudebooru.booru.feed.PostGrouper
import app.dudebooru.booru.filter.Blacklist
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.site.SiteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что показывает лента: смена любой части начинает её заново. */
data class FeedKey(
    val siteId: String,
    val mode: ContentMode,
    val sort: SortOrder,
    val tags: List<String>,
)

/** Карточка ленты: один пост или карусель из серии. */
data class FeedItem(val posts: List<Post>) {
    val lead: Post get() = posts.first()
    val key: String get() = lead.key
}

data class FeedState(
    val key: FeedKey? = null,
    val items: List<FeedItem> = emptyList(),
    val next: PageKey? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: Throwable? = null,
    val plan: QueryPlan? = null,
    val authDropped: Boolean = false,
    /** Скрыто негативными тегами: строка → сколько постов («скрыто 12»). */
    val hidden: Map<String, Int> = emptyMap(),
) {
    val hiddenCount: Int get() = hidden.values.sum()
}

/**
 * Одна лента: папка источника, результаты поиска, работы художника, пул.
 * Держит посты, курсор, позицию прокрутки и догрузку семей для каруселей.
 */
class FeedController(
    val id: String,
    val site: SiteConfig,
    private val c: AppContainer,
    private val scope: CoroutineScope,
    val tags: List<String>,
    initialSort: SortOrder,
    private val mode: () -> ContentMode,
    private val onSortChanged: (SortOrder) -> Unit = {},
) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val _sort = MutableStateFlow(initialSort)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    val listState = LazyListState()
    val gridState = LazyGridState()

    val supportedSorts: List<SortOrder> get() = c.registry.engine(site).supportedSorts

    private var job: Job? = null
    private val familyRequested = HashSet<Long>()

    /** Все картинки ленты подряд — для просмотра: сначала вся карусель, потом следующий пост. */
    val posts: List<Post> get() = _state.value.items.flatMap { it.posts }

    fun setSort(sort: SortOrder) {
        if (_sort.value == sort) return
        _sort.value = sort
        onSortChanged(sort)
        ensureLoaded()
    }

    fun ensureLoaded() {
        val s = _state.value
        if (s.key != currentKey() || (s.items.isEmpty() && !s.loading && s.error == null && !s.endReached)) load(reset = true)
    }

    fun refresh() = load(reset = true, pull = true)

    fun loadMore() = load(reset = false)

    fun retry() = if (_state.value.items.isEmpty()) load(reset = true) else load(reset = false)

    private fun currentKey() = FeedKey(site.id, mode(), _sort.value, tags)

    private fun load(reset: Boolean, pull: Boolean = false) {
        val before = _state.value
        if (!reset && (before.loading || before.endReached || before.items.isEmpty())) return
        val key = currentKey()
        if (reset) {
            job?.cancel()
            familyRequested.clear()
            if (!pull) scope.launch {
                listState.scrollToItem(0)
                gridState.scrollToItem(0)
            }
            _state.value = if (pull && before.key == key) {
                before.copy(refreshing = true, error = null)
            } else {
                FeedState(key = key, loading = true, authDropped = before.authDropped)
            }
        } else {
            _state.update { it.copy(loading = true, error = null) }
        }
        job = scope.launch {
            try {
                val current = _state.value
                val seen = if (reset) emptySet() else current.items.flatMapTo(HashSet()) { item -> item.posts.map { it.id } }
                val chunk = withContext(Dispatchers.IO) {
                    c.posts.load(
                        site = site,
                        request = FeedRequest(tags = key.tags, sort = key.sort, mode = key.mode),
                        cursor = if (reset) null else current.next,
                        seen = seen,
                    )
                }
                val fresh = PostGrouper.group(chunk.posts).map { FeedItem(it) }
                _state.update {
                    it.copy(
                        items = if (reset) fresh else it.items + fresh,
                        next = chunk.next,
                        endReached = chunk.next == null,
                        loading = false,
                        refreshing = false,
                        error = null,
                        plan = chunk.plan,
                        authDropped = it.authDropped || chunk.authDropped,
                        hidden = merge(if (reset) emptyMap() else it.hidden, chunk.hidden),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = e) }
            }
        }
    }

    /** Добавили негативный тег — посты с ним уходят сразу, без перезагрузки. */
    fun applyBlacklist(blacklist: Blacklist) {
        _state.update { s ->
            val newlyHidden = HashMap<String, Int>()
            val items = s.items.mapNotNull { item ->
                val kept = item.posts.filter { post ->
                    val hit = blacklist.match(post)
                    if (hit != null) newlyHidden.merge(hit.expression, 1, Int::plus)
                    hit == null
                }
                when {
                    kept.isEmpty() -> null
                    kept.size == item.posts.size -> item
                    else -> FeedItem(kept)
                }
            }
            if (newlyHidden.isEmpty()) s else s.copy(items = items, hidden = merge(s.hidden, newlyHidden))
        }
    }

    private fun merge(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> =
        (a.keys + b.keys).associateWith { (a[it] ?: 0) + (b[it] ?: 0) }

    /**
     * Если у поста есть дети или родитель, которых нет на этой странице, — догружаем семью
     * одним запросом `parent:ID`, когда карточка появляется на экране.
     */
    fun ensureFamily(item: FeedItem) {
        val root = PostGrouper.familyRoot(item.posts) ?: return
        if (!familyRequested.add(root)) return
        scope.launch {
            val family = runCatching {
                withContext(Dispatchers.IO) {
                    c.registry.engine(site).family(root, mode(), c.accounts.session(site))
                }
            }.getOrNull().orEmpty()
            val blacklist = c.negative.blacklist.value
            val allowed = family.filter { blacklist.match(it) == null }
            if (allowed.size <= 1) return@launch
            _state.update { s ->
                val index = s.items.indexOfFirst { it.key == item.key }
                if (index < 0) return@update s
                // Первая картинка остаётся на месте: по ней считается высота карточки, лента не прыгает.
                val current = s.items[index]
                val others = (current.posts.drop(1) + allowed).filter { it.id != current.lead.id }
                val merged = FeedItem(listOf(current.lead) + PostGrouper.order(others))
                val ids = merged.posts.mapTo(HashSet()) { it.id }
                // Дети, которые уже стоят в ленте отдельными карточками, уходят в карусель.
                val rest = s.items.filterIndexed { i, other -> i == index || !ids.containsAll(other.posts.map { it.id }) }
                s.copy(items = rest.map { if (it.key == item.key) merged else it })
            }
        }
    }
}
