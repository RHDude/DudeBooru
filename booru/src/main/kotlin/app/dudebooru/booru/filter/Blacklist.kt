package app.dudebooru.booru.filter

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.Rating

/**
 * Одна строка негативных тегов в синтаксисе блэклиста Danbooru:
 * `tag_a tag_b` скрывает пост, только если на нём оба тега; `-tag` — «если тега нет»;
 * поддерживаются `rating:q,e` и `score:<0`. [site] = null — для всех источников.
 */
data class BlacklistEntry(val id: Long, val expression: String, val site: String? = null)

class Blacklist(entries: List<BlacklistEntry>) {
    private val rules = entries.mapNotNull { entry -> Rule.parse(entry) }

    val isEmpty: Boolean get() = rules.isEmpty()

    /** Какая строка скрывает пост, или null. Проверяется до отрисовки: пост ни разу не мелькнёт. */
    fun match(post: Post): BlacklistEntry? = rules.firstOrNull { it.matches(post) }?.entry

    fun partition(posts: List<Post>): Pair<List<Post>, List<Pair<Post, BlacklistEntry>>> {
        if (rules.isEmpty()) return posts to emptyList()
        val visible = ArrayList<Post>(posts.size)
        val hidden = ArrayList<Pair<Post, BlacklistEntry>>()
        for (post in posts) {
            val hit = match(post)
            if (hit == null) visible += post else hidden += post to hit
        }
        return visible to hidden
    }

    private class Rule(val entry: BlacklistEntry, val terms: List<Term>) {
        fun matches(post: Post): Boolean {
            if (entry.site != null && entry.site != post.site) return false
            return terms.all { it.test(post) }
        }

        companion object {
            fun parse(entry: BlacklistEntry): Rule? {
                val terms = entry.expression.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Term.parse(it) }
                return if (terms.isEmpty()) null else Rule(entry, terms)
            }
        }
    }

    private sealed interface Term {
        fun test(post: Post): Boolean

        class Tag(val name: String, val negated: Boolean) : Term {
            private val pattern = if ('*' in name) Regex(name.split('*').joinToString(".*") { Regex.escape(it) }) else null
            override fun test(post: Post): Boolean {
                val has = if (pattern == null) name in post.allTags else post.allTags.any { pattern.matches(it) }
                return has != negated
            }
        }

        class RatingTerm(val ratings: Set<Char>, val negated: Boolean) : Term {
            override fun test(post: Post): Boolean = (post.rating.code in ratings || moebooruSafe(post)) != negated

            // У Moebooru «s» — это safe; для блэклиста `rating:g` считаем его подходящим тоже.
            private fun moebooruSafe(post: Post) = 'g' in ratings && post.rating == Rating.SENSITIVE && post.site !in DANBOORU_SITES
        }

        class ScoreTerm(val op: String, val value: Int, val negated: Boolean) : Term {
            override fun test(post: Post): Boolean {
                val hit = when (op) {
                    "<" -> post.score < value
                    "<=" -> post.score <= value
                    ">" -> post.score > value
                    ">=" -> post.score >= value
                    else -> post.score == value
                }
                return hit != negated
            }
        }

        companion object {
            fun parse(raw: String): Term {
                val negated = raw.startsWith("-")
                val body = raw.removePrefix("-")
                if (body.startsWith("rating:")) {
                    val codes = body.removePrefix("rating:").split(',').mapNotNull { it.firstOrNull() }.toSet()
                    return RatingTerm(codes, negated)
                }
                if (body.startsWith("score:")) {
                    val m = Regex("""score:(<=|>=|<|>)?(-?\d+)""").matchEntire(body)
                    if (m != null) return ScoreTerm(m.groupValues[1], m.groupValues[2].toInt(), negated)
                }
                return Tag(body, negated)
            }

            private val DANBOORU_SITES = setOf("danbooru", "safebooru")
        }
    }
}
