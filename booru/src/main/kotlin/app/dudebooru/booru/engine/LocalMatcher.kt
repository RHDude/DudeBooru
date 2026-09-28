package app.dudebooru.booru.engine

import app.dudebooru.booru.model.Post

/**
 * Проверка в приложении тех тегов поиска, что не влезли в лимит:
 * `tag`, `-tag`, маски `hatsune*`, группа «или» `~a ~b`.
 */
class LocalMatcher(terms: List<String>) {
    private val required = mutableListOf<TermTest>()
    private val excluded = mutableListOf<TermTest>()
    private val anyOf = mutableListOf<TermTest>()

    init {
        for (raw in terms) {
            when {
                raw.startsWith("-") -> excluded += TermTest(raw.substring(1))
                raw.startsWith("~") -> anyOf += TermTest(raw.substring(1))
                else -> required += TermTest(raw)
            }
        }
    }

    val isEmpty: Boolean get() = required.isEmpty() && excluded.isEmpty() && anyOf.isEmpty()

    fun matches(post: Post): Boolean {
        val tags = post.allTags
        if (required.any { !it.test(tags) }) return false
        if (excluded.any { it.test(tags) }) return false
        if (anyOf.isNotEmpty() && anyOf.none { it.test(tags) }) return false
        return true
    }

    private class TermTest(term: String) {
        private val exact: String? = term.lowercase().takeIf { '*' !in it }
        private val pattern: Regex? = if (exact == null) {
            Regex(term.lowercase().split('*').joinToString(".*") { Regex.escape(it) })
        } else {
            null
        }

        fun test(tags: Set<String>): Boolean =
            if (exact != null) exact in tags else tags.any { pattern!!.matches(it) }
    }
}
