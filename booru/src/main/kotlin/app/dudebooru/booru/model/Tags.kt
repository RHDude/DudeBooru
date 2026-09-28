package app.dudebooru.booru.model

import kotlinx.serialization.Serializable

@Serializable
enum class TagCategory {
    GENERAL, ARTIST, COPYRIGHT, CHARACTER, META;

    companion object {
        /** Категории Danbooru: 0 general, 1 artist, 3 copyright, 4 character, 5 meta. */
        fun fromDanbooru(code: Int): TagCategory = when (code) {
            1 -> ARTIST
            3 -> COPYRIGHT
            4 -> CHARACTER
            5 -> META
            else -> GENERAL
        }

        /**
         * Типы Moebooru: 0 general, 1 artist, 3 copyright, 4 character, 5 circle, 6 faults/style.
         * Кружок (circle) — группа художников, показываем как художника.
         */
        fun fromMoebooru(code: Int): TagCategory = when (code) {
            1, 5 -> ARTIST
            3 -> COPYRIGHT
            4 -> CHARACTER
            6 -> META
            else -> GENERAL
        }

        /** Строковые типы из `tags` в ответе `api_version=2`. */
        fun fromMoebooruName(name: String?): TagCategory = when (name) {
            "artist", "circle" -> ARTIST
            "copyright" -> COPYRIGHT
            "character" -> CHARACTER
            "faults", "style", "meta" -> META
            else -> GENERAL
        }
    }
}

/** Тег с категорией и числом постов — для автодополнения, словаря и расчёта редкости. */
@Serializable
data class TagInfo(
    val name: String,
    val category: TagCategory,
    val postCount: Long? = null,
    /** Если введён алиас — то, что ввели; [name] уже каноничный. */
    val antecedent: String? = null,
)

@Serializable
data class ArtistInfo(
    val name: String,
    val otherNames: List<String> = emptyList(),
    val groupName: String? = null,
    val urls: List<String> = emptyList(),
    val isBanned: Boolean = false,
)
