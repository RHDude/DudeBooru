package app.dudebooru.data.posts

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.net.BooruJson
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.MessageDigest

/**
 * Кэш ленты: первая страница каждой ленты лежит файлом. Без сети лента показывает её,
 * и игра появляется, только если открыть совсем нечего.
 */
class FeedSnapshots(private val dir: File) {
    private val serializer = ListSerializer(Post.serializer())

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    fun save(key: String, posts: List<Post>) {
        if (posts.isEmpty()) return
        runCatching {
            dir.mkdirs()
            val target = file(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(BooruJson.encodeToString(serializer, posts.take(60)))
            tmp.renameTo(target)
        }
    }

    fun load(key: String): List<Post>? = runCatching {
        val f = file(key)
        if (!f.exists()) null else BooruJson.decodeFromString(serializer, f.readText())
    }.getOrNull()
}
