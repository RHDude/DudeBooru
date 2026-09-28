package app.dudebooru.booru.download

import app.dudebooru.booru.model.Post

/**
 * Шаблон имени файла: `Booru/{site}/{artist}/{site}_{id}.{ext}`.
 * Переменные: {site} {id} {md5} {ext} {artist} {character} {copyright} {rating} {width} {height}.
 * Каждый сегмент пути очищается от символов, запрещённых в именах файлов.
 */
object NameTemplate {
    val VARIABLES = listOf("site", "id", "md5", "ext", "artist", "character", "copyright", "rating", "width", "height")

    fun render(template: String, post: Post, ext: String): String {
        val values = mapOf(
            "site" to post.site,
            "id" to post.id.toString(),
            "md5" to (post.md5 ?: post.id.toString()),
            "ext" to ext.lowercase().ifEmpty { "bin" },
            "artist" to join(post.tags.artist, "unknown_artist"),
            "character" to join(post.tags.character, "no_character"),
            "copyright" to join(post.tags.copyright, "original"),
            "rating" to post.rating.code.toString(),
            "width" to post.width.toString(),
            "height" to post.height.toString(),
        )
        val filled = VARIABLE.replace(template.ifBlank { DEFAULT }) { m -> values[m.groupValues[1].lowercase()] ?: m.value }
        val segments = filled.replace('\\', '/').split('/').map { sanitize(it) }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return "${post.site}_${post.id}.$ext"
        // Расширение обязательно: без него галерея не узнает файл.
        val last = segments.last()
        val withExt = if (last.contains('.')) last else "$last.$ext"
        return (segments.dropLast(1) + withExt).joinToString("/")
    }

    fun directory(path: String): String = path.substringBeforeLast('/', "")

    fun fileName(path: String): String = path.substringAfterLast('/')

    private fun join(values: List<String>, fallback: String): String =
        if (values.isEmpty()) fallback else values.take(3).joinToString("+")

    private fun sanitize(segment: String): String =
        segment.replace(ILLEGAL, "_").trim().trim('.').take(120).trim()

    const val DEFAULT = "Booru/{site}/{artist}/{site}_{id}.{ext}"
    private val VARIABLE = Regex("""\{([a-zA-Z0-9]+)\}""")
    private val ILLEGAL = Regex("""[\u0000-\u001f:*?"<>|]""")
}
