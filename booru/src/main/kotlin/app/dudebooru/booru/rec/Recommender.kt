package app.dudebooru.booru.rec

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.TagCategory
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** Что человек сделал с постом: лайк, сохранение или «Не интересно». */
enum class SignalKind(val weight: Double) {
    LIKE(1.0),
    SAVE(2.0),
    DISLIKE(-1.0),
}

data class Signal(val post: Post, val kind: SignalKind, val at: Long)

/** Тег профиля вкуса. Имя без категории: лайк на Danbooru учитывается и на Yande.re. */
data class TasteTag(val name: String, val category: TagCategory, val weight: Double)

/**
 * Профиль вкуса: веса тегов из лайкнутого и сохранённого.
 * Категории: художник ×3, персонаж ×2, копирайт ×1.5, general ×1, meta не учитывается.
 * Редкие теги важнее частых (TF-IDF), свежие лайки весят больше (полураспад 60 дней),
 * сохранение ×2, «Не интересно» вычитает.
 */
class TasteProfile private constructor(
    private val weights: Map<String, TasteTag>,
    val signalCount: Int,
    val likeCount: Int,
) {
    fun weight(tag: String): Double = weights[tag]?.weight ?: 0.0

    fun top(category: TagCategory? = null, limit: Int = 20): List<TasteTag> =
        weights.values.asSequence()
            .filter { it.weight > 0 && (category == null || it.category == category) }
            .sortedByDescending { it.weight }
            .take(limit)
            .toList()

    val isEmpty: Boolean get() = weights.isEmpty()

    val maxWeight: Double = weights.values.maxOfOrNull { it.weight }?.coerceAtLeast(0.0) ?: 0.0

    companion object {
        const val HALF_LIFE_DAYS = 60.0
        private const val DAY = 24L * 3600 * 1000

        fun categoryWeight(category: TagCategory): Double = when (category) {
            TagCategory.ARTIST -> 3.0
            TagCategory.CHARACTER -> 2.0
            TagCategory.COPYRIGHT -> 1.5
            TagCategory.GENERAL -> 1.0
            TagCategory.META -> 0.0
        }

        /**
         * @param idf редкость тега: ln(постов на сайте / постов с тегом). Неизвестный тег — средняя редкость.
         * @param muted теги, которые человек убрал из «Моих тегов».
         */
        fun build(
            signals: List<Signal>,
            now: Long,
            idf: (String) -> Double? = { null },
            muted: Set<String> = emptySet(),
        ): TasteProfile {
            val sums = HashMap<String, Pair<TagCategory, Double>>()
            for (signal in signals) {
                val ageDays = ((now - signal.at).coerceAtLeast(0)).toDouble() / DAY
                val decay = 0.5.pow(ageDays / HALF_LIFE_DAYS)
                val base = signal.kind.weight * decay
                for (category in TagCategory.entries) {
                    val cw = categoryWeight(category)
                    if (cw == 0.0) continue
                    for (tag in signal.post.tags.byCategory(category)) {
                        if (tag in muted) continue
                        val rarity = idf(tag) ?: DEFAULT_IDF
                        val add = base * cw * rarity.coerceIn(MIN_IDF, MAX_IDF)
                        val current = sums[tag]
                        sums[tag] = category to ((current?.second ?: 0.0) + add)
                    }
                }
            }
            val weights = sums.mapValues { (name, v) -> TasteTag(name, v.first, v.second) }
            return TasteProfile(weights, signals.size, signals.count { it.kind != SignalKind.DISLIKE })
        }

        /** «Найти похожие»: профиль из одного поста. */
        fun ofPost(post: Post, idf: (String) -> Double? = { null }): TasteProfile =
            build(listOf(Signal(post, SignalKind.LIKE, 0)), 0, idf)

        const val DEFAULT_IDF = 4.0
        const val MIN_IDF = 0.05
        const val MAX_IDF = 10.0

        /** ln(N / df) — классический IDF; [total] — примерно постов на сайте. */
        fun idf(postCount: Long, total: Long): Double = ln(total.toDouble() / (postCount + 1).toDouble())
    }
}

/** Рекомендованный пост и почему: «вы лайкали hatsune_miku и artist_x». */
data class Recommendation(val post: Post, val score: Double, val reasons: List<String>, val explore: Boolean = false)

object Recommender {
    /**
     * Ранжирование кандидатов: оценка — совпадение тегов с профилем (нормировано на длину списка тегов).
     * Разнообразие: не больше 2 работ одного художника подряд, ~15% «на пробу» из неподходящих.
     */
    fun rank(
        candidates: List<Post>,
        profile: TasteProfile,
        exclude: Set<String> = emptySet(),
        exploreShare: Double = 0.15,
        random: Random = Random.Default,
    ): List<Recommendation> {
        val unique = candidates.distinctBy { it.key }.filter { it.key !in exclude }
        val scored = unique.map { post -> score(post, profile) }
        // Подходящий пост — с сильной причиной; совпадение только по общим тегам не в счёт.
        val (matching, others) = scored.partition { it.reasons.isNotEmpty() && it.score > 0 }
        val sorted = matching.sortedByDescending { it.score }

        // «На пробу»: немного постов без сильных совпадений, чтобы подборка не замыкалась на себе.
        val exploreCount = if (sorted.isEmpty()) others.size else (sorted.size * exploreShare / (1 - exploreShare)).toInt()
        val explore = others.shuffled(random).take(exploreCount).map { it.copy(explore = true, reasons = emptyList()) }

        val mixed = ArrayList<Recommendation>(sorted.size + explore.size)
        val step = if (explore.isEmpty()) Int.MAX_VALUE else (sorted.size / explore.size).coerceAtLeast(1)
        var e = 0
        for ((i, rec) in sorted.withIndex()) {
            mixed += rec
            if ((i + 1) % step == 0 && e < explore.size) mixed += explore[e++]
        }
        while (e < explore.size) mixed += explore[e++]
        return diversify(mixed)
    }

    fun score(post: Post, profile: TasteProfile): Recommendation {
        var total = 0.0
        val contributions = ArrayList<Pair<String, Double>>()
        for (tag in post.allTags) {
            val w = profile.weight(tag)
            if (w != 0.0) {
                total += w
                if (w > 0) contributions += tag to w
            }
        }
        val norm = total / sqrt(post.allTags.size.coerceAtLeast(1).toDouble())
        // Причина — только заметный тег вкуса, а не «1girl».
        val strong = profile.maxWeight * STRONG_SHARE
        val reasons = contributions.filter { it.second >= strong }.sortedByDescending { it.second }.take(2).map { it.first }
        return Recommendation(post, norm, reasons)
    }

    /** Доля от самого весомого тега профиля, с которой тег считается сильной причиной. */
    const val STRONG_SHARE = 0.2

    /** Не больше 2 работ одного художника подряд: третью отодвигаем ниже. */
    fun diversify(list: List<Recommendation>, maxRun: Int = 2): List<Recommendation> {
        val pending = ArrayDeque(list)
        val result = ArrayList<Recommendation>(list.size)
        while (pending.isNotEmpty()) {
            val lastArtists = result.takeLast(maxRun).map { it.post.tags.artist.firstOrNull() }
            val runArtist = lastArtists.firstOrNull()?.takeIf { a -> lastArtists.size == maxRun && lastArtists.all { it == a } }
            val index = if (runArtist == null) 0 else pending.indexOfFirst { it.post.tags.artist.firstOrNull() != runArtist }
            result += if (index < 0) pending.removeFirst() else pending.removeAt(index)
        }
        return result
    }

    /**
     * Запросы кандидатов: новые работы любимых художников, популярное по любимым персонажам
     * и копирайтам, пары general-тегов с высоким весом.
     */
    fun queries(profile: TasteProfile, artists: Int = 3, characters: Int = 3, copyrights: Int = 2, generalPairs: Int = 2): List<CandidateQuery> {
        val result = ArrayList<CandidateQuery>()
        profile.top(TagCategory.ARTIST, artists).forEach { result += CandidateQuery(listOf(it.name), CandidateQuery.Kind.ARTIST_NEW) }
        profile.top(TagCategory.CHARACTER, characters).forEach { result += CandidateQuery(listOf(it.name), CandidateQuery.Kind.POPULAR) }
        profile.top(TagCategory.COPYRIGHT, copyrights).forEach { result += CandidateQuery(listOf(it.name), CandidateQuery.Kind.POPULAR) }
        val generals = profile.top(TagCategory.GENERAL, generalPairs * 2)
        generals.chunked(2).filter { it.size == 2 }.forEach { pair ->
            result += CandidateQuery(pair.map { it.name }, CandidateQuery.Kind.POPULAR)
        }
        return result
    }
}

data class CandidateQuery(val tags: List<String>, val kind: Kind) {
    enum class Kind { ARTIST_NEW, POPULAR }
}
