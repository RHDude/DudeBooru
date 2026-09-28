package app.dudebooru.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dudebooru.AppContainer
import app.dudebooru.booru.model.TagInfo
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.db.SearchHistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Подсказки по префиксу: сначала мгновенно из словаря, потом с сайта. */
class SearchViewModel(private val c: AppContainer, val site: SiteConfig) : ViewModel() {

    private val _suggestions = MutableStateFlow<List<TagInfo>>(emptyList())
    val suggestions: StateFlow<List<TagInfo>> = _suggestions.asStateFlow()

    val history: Flow<List<SearchHistoryEntity>> = c.db.searchHistory().recent(site.id)

    private var job: Job? = null

    /**
     * Подсказки для набранного текста: сначала для всей фразы через «_» («hatsune mi» → hatsune_mi…),
     * потом для последнего слова — это может быть уже следующий тег.
     */
    fun onInput(text: String) {
        job?.cancel()
        val words = words(text)
        val last = words.lastOrNull()?.trimStart('-', '~')?.lowercase().orEmpty()
        val phrase = if (words.size > 1) words.joinToString("_") { it.trimStart('-', '~') }.lowercase() else null
        if (last.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }
        job = viewModelScope.launch {
            val local = (phrase?.let { c.tags.localSuggestions(site, it, 6) }.orEmpty() + c.tags.localSuggestions(site, last, 12))
                .distinctBy { it.name }
            _suggestions.value = local
            delay(250)
            val remote = runCatching {
                phrase?.let { c.tags.remoteSuggestions(site, it, 6) }.orEmpty() + c.tags.remoteSuggestions(site, last, 12)
            }.getOrDefault(emptyList())
            if (remote.isNotEmpty()) _suggestions.value = (remote + local).distinctBy { it.name }.take(15)
        }
    }

    /**
     * Нажали подсказку: что из набранного становится чипами. Подсказка для всей фразы заменяет её целиком;
     * подсказка для последнего слова заменяет только его, а слова перед ним остаются своими тегами —
     * раньше они терялись, и поиск по двум тегам шёл по одному.
     */
    suspend fun accept(text: String, tag: String): List<String> {
        val words = words(text)
        if (words.isEmpty()) return listOf(tag)
        val phrase = words.joinToString("_") { it.trimStart('-', '~') }.lowercase()
        if (words.size > 1 && tag.startsWith(phrase)) return listOf(sign(words.first()) + tag)
        return normalize(words.dropLast(1).joinToString(" ")) + (sign(words.last()) + tag)
    }

    private fun words(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun sign(word: String): String = word.takeWhile { it == '-' || it == '~' }

    /**
     * Свободный текст → теги. «hatsune miku» целиком становится `hatsune_miku`, если такой тег есть;
     * иначе слова — отдельные теги. Алиас заменяется каноничным тегом.
     */
    suspend fun normalize(text: String): List<String> {
        val raw = text.trim().lowercase()
        if (raw.isEmpty()) return emptyList()
        if (' ' in raw && !raw.contains(':')) {
            val joined = raw.split(Regex("\\s+")).joinToString("_")
            val known = c.tags.localSuggestions(site, joined, 1).firstOrNull()?.name == joined ||
                runCatching { c.tags.remoteSuggestions(site, joined, 3) }.getOrDefault(emptyList()).any { it.name == joined }
            if (known) return listOf(c.tags.canonical(site, joined))
        }
        return raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { term ->
            val prefix = term.takeWhile { it == '-' || it == '~' }
            prefix + c.tags.canonical(site, term.removePrefix(prefix))
        }
    }

    fun remember(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val dao = c.db.searchHistory()
            val pinned = dao.get(site.id, query)?.pinned ?: false
            dao.upsert(SearchHistoryEntity(site.id, query, System.currentTimeMillis(), pinned))
        }
    }

    fun togglePin(entry: SearchHistoryEntity) {
        viewModelScope.launch { c.db.searchHistory().upsert(entry.copy(pinned = !entry.pinned)) }
    }

    fun forget(entry: SearchHistoryEntity) {
        viewModelScope.launch { c.db.searchHistory().delete(entry.site, entry.query) }
    }
}
