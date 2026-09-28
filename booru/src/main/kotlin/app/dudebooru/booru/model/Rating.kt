package app.dudebooru.booru.model

import kotlinx.serialization.Serializable

/**
 * Рейтинг поста в терминах Danbooru. У Moebooru рейтингов три: `s` означает «safe»,
 * но покрывает и general, и sensitive Danbooru, поэтому осторожно отображается в [SENSITIVE].
 */
@Serializable
enum class Rating(val code: Char) {
    GENERAL('g'),
    SENSITIVE('s'),
    QUESTIONABLE('q'),
    EXPLICIT('e');

    val isNsfw: Boolean get() = this == QUESTIONABLE || this == EXPLICIT

    companion object {
        fun fromDanbooru(code: String?): Rating = when (code?.firstOrNull()) {
            'g' -> GENERAL
            's' -> SENSITIVE
            'q' -> QUESTIONABLE
            'e' -> EXPLICIT
            // Неизвестный рейтинг считаем откровенным: лучше спрятать лишнее, чем показать.
            else -> EXPLICIT
        }

        fun fromMoebooru(code: String?): Rating = when (code?.firstOrNull()) {
            's' -> SENSITIVE
            'q' -> QUESTIONABLE
            'e' -> EXPLICIT
            else -> EXPLICIT
        }
    }
}

/** Режим контента: SFW / NSFW / Всё. */
@Serializable
enum class ContentMode {
    SFW, NSFW, ALL;

    fun allows(rating: Rating): Boolean = when (this) {
        SFW -> !rating.isNsfw
        NSFW -> rating.isNsfw
        ALL -> true
    }
}
