package app.dudebooru.booru.engine

import app.dudebooru.booru.net.BooruException

/** Разложение поиска: что уходит на сервер, что проверяется в приложении. */
data class QueryPlan(
    val serverTerms: List<String>,
    val localTerms: List<String>,
    /** Сортировку не удалось уложить в лимит — лента идёт в порядке по умолчанию. */
    val sortDropped: Boolean = false,
) {
    val query: String get() = serverTerms.joinToString(" ")

    companion object {
        val EMPTY = QueryPlan(emptyList(), emptyList())
    }
}

/**
 * Правила подсчёта слотов лимита тегов для движка.
 * [freeMetatags] не тратят слот, [metatags] — все известные метатеги (остальное с двоеточием — теги вроде `re:zero`).
 */
class TagRules(
    val metatags: Set<String>,
    val freeMetatags: Set<String>,
) {
    fun metatagName(term: String): String? {
        val body = term.removePrefix("-").removePrefix("~")
        val colon = body.indexOf(':')
        if (colon <= 0) return null
        val name = body.substring(0, colon).lowercase()
        return name.takeIf { it in metatags }
    }

    fun isFree(term: String): Boolean = metatagName(term)?.let { it in freeMetatags } == true
}

object QueryPlanner {
    /**
     * @param userTerms теги поиска как ввёл человек.
     * @param systemTerms обязательные бесплатные части (рейтинг режима, `age:`/`date:` популярного).
     * @param orderTerm `order:…` от сортировки, тратит слот.
     * @param limit лимит тегов аккаунта, `null` — без лимита.
     * @param postCounts число постов у тегов: на сервер уходят самые редкие.
     * @param moebooruLimits правила Moebooru: метатеги и «или» бесплатны, лимит отдельно
     *   на обязательные теги и отдельно на исключения (post/sql_methods.rb).
     */
    fun plan(
        siteId: String,
        rules: TagRules,
        userTerms: List<String>,
        systemTerms: List<String>,
        orderTerm: String?,
        limit: Int?,
        postCounts: Map<String, Long> = emptyMap(),
        moebooruLimits: Boolean = false,
    ): QueryPlan {
        if (moebooruLimits) return planMoebooru(rules, userTerms, systemTerms, orderTerm, limit, postCounts)
        val terms = userTerms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val userOrder = terms.firstOrNull { rules.metatagName(it) in ORDER_NAMES }
        val effectiveOrder = if (userOrder != null) null else orderTerm

        val free = mutableListOf<String>()
        val mandatory = mutableListOf<String>()
        val orGroup = mutableListOf<String>()
        val positives = mutableListOf<String>()
        val negatives = mutableListOf<String>()
        for (term in terms) {
            when {
                rules.metatagName(term) != null -> if (rules.isFree(term)) free += term else mandatory += term
                term.startsWith("~") -> orGroup += term
                term.startsWith("-") -> negatives += term
                else -> positives += term
            }
        }

        if (limit == null) {
            val all = systemTerms + free + mandatory + listOfNotNull(effectiveOrder) + orGroup + positives + negatives
            return QueryPlan(all, emptyList())
        }

        var used = mandatory.size
        if (used > limit) throw BooruException.TagLimitExceeded(siteId, limit, used)

        val server = mutableListOf<String>()
        val local = mutableListOf<String>()
        fun claim(term: String) {
            if (used < limit) {
                server += term
                used++
            } else {
                local += term
            }
        }
        // Свои теги важнее сортировки: тег, проверяемый у себя, листает ленту страницами вхолостую,
        // а без сортировки лента просто идёт новыми сверху. Редкие теги сужают выдачу сильнее всего.
        positives.sortedWith(compareBy<String> { it.contains('*') }.thenBy { postCounts[it] ?: Long.MAX_VALUE / 2 })
            .forEach(::claim)
        // Группа «или» уходит целиком или целиком проверяется локально.
        if (orGroup.isNotEmpty()) {
            if (used + orGroup.size <= limit) {
                server += orGroup
                used += orGroup.size
            } else {
                local += orGroup
            }
        }
        var sortDropped = false
        val order = effectiveOrder?.let {
            if (used < limit) {
                used++
                it
            } else {
                sortDropped = true
                null
            }
        }
        // Исключения дешевле всего проверить у себя — они последние в очереди за слотами.
        negatives.forEach(::claim)

        return QueryPlan(
            serverTerms = systemTerms + free + mandatory + listOfNotNull(order) + server,
            localTerms = local,
            sortDropped = sortDropped,
        )
    }

    private fun planMoebooru(
        rules: TagRules,
        userTerms: List<String>,
        systemTerms: List<String>,
        orderTerm: String?,
        limit: Int?,
        postCounts: Map<String, Long>,
    ): QueryPlan {
        val terms = userTerms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val userOrder = terms.any { rules.metatagName(it) == "order" }
        val meta = terms.filter { rules.metatagName(it) != null }
        val orGroup = terms.filter { rules.metatagName(it) == null && it.startsWith("~") }
        val negatives = terms.filter { rules.metatagName(it) == null && it.startsWith("-") }
        val positives = terms.filter { rules.metatagName(it) == null && !it.startsWith("-") && !it.startsWith("~") }
            .sortedWith(compareBy<String> { it.contains('*') }.thenBy { postCounts[it] ?: Long.MAX_VALUE / 2 })

        val cap = limit ?: Int.MAX_VALUE
        val server = systemTerms + meta + listOfNotNull(orderTerm.takeUnless { userOrder }) + orGroup +
            positives.take(cap) + negatives.take(cap)
        val local = positives.drop(cap) + negatives.drop(cap)
        return QueryPlan(server, local)
    }

    private val ORDER_NAMES = setOf("order", "ordfav", "ordvote", "ordfavgroup", "ordpool")
}
