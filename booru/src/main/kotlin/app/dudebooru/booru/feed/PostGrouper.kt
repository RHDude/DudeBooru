package app.dudebooru.booru.feed

import app.dudebooru.booru.model.Post

/**
 * Собирает серию из одной публикации художника в одну карточку-карусель.
 * На booru каждая картинка — отдельный пост; серия связывается родителем и детьми,
 * одинаковым `pixiv_id` (Danbooru) или одинаковым первоисточником у одного художника.
 * Подряд идущие посты одного пула тоже склеиваются.
 */
object PostGrouper {

    /** Группы в порядке первого появления; внутри — родитель первым, дальше по id. */
    fun group(posts: List<Post>): List<List<Post>> {
        if (posts.size < 2) return posts.map { listOf(it) }
        val parent = IntArray(posts.size) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }

        val firstByKey = HashMap<String, Int>()
        posts.forEachIndexed { i, post ->
            for (key in keys(post)) {
                val seen = firstByKey.putIfAbsent(key, i)
                if (seen != null) union(seen, i)
            }
        }
        // Пул: только соседние посты, иначе весь артбук слипнется в одну карточку через всю ленту.
        for (i in 1 until posts.size) {
            val a = posts[i - 1].poolIds
            if (a.isNotEmpty() && posts[i].poolIds.any { it in a }) union(i - 1, i)
        }

        val groups = LinkedHashMap<Int, MutableList<Post>>()
        posts.forEachIndexed { i, post -> groups.getOrPut(find(i)) { mutableListOf() } += post }
        return groups.values.map { order(it) }
    }

    /** Родитель первым, затем остальные по возрастанию id (обычно это порядок загрузки p0, p1…). */
    fun order(posts: Collection<Post>): List<Post> {
        val distinct = posts.distinctBy { it.id }
        val ids = distinct.mapTo(HashSet()) { it.id }
        return distinct.sortedWith(
            compareBy<Post> { if (it.parentId != null && it.parentId in ids) 1 else 0 }.thenBy { it.id },
        )
    }

    /** Корень семьи для догрузки `parent:ID`. */
    fun familyRoot(posts: List<Post>): Long? {
        val withParent = posts.firstOrNull { it.parentId != null }
        if (withParent != null) return withParent.parentId
        return posts.firstOrNull { it.hasChildren }?.id
    }

    internal fun keys(post: Post): List<String> = buildList {
        add("root:${post.site}:${post.parentId ?: post.id}")
        post.pixivId?.let { add("pixiv:${post.site}:$it") }
        val artist = post.tags.artist.firstOrNull()
        val source = post.source?.let { normalizeSource(it) }
        if (artist != null && source != null) add("src:${post.site}:$artist:$source")
    }

    /**
     * Первоисточник без мусора: для pixiv — id работы, для X/Twitter — id твита.
     * Ссылки на профиль или главную не годятся: они общие для всех работ художника.
     */
    internal fun normalizeSource(source: String): String? {
        val s = source.trim().lowercase()
        PIXIV_ARTWORK.find(s)?.let { return "pixiv:${it.groupValues[1]}" }
        PIXIV_IMAGE.find(s)?.let { return "pixiv:${it.groupValues[1]}" }
        TWITTER_STATUS.find(s)?.let { return "tw:${it.groupValues[1]}" }
        val noQuery = s.substringBefore('?').substringBefore('#').trimEnd('/')
        val path = noQuery.substringAfter("://", noQuery).substringAfter('/', "")
        // Слишком короткий путь — это профиль или сайт, а не конкретная публикация.
        return if (path.count { it == '/' } >= 1 || path.any { it.isDigit() } && path.length > 4) noQuery else null
    }

    private val PIXIV_ARTWORK = Regex("""pixiv\.net/(?:[a-z]{2}/)?artworks/(\d+)""")
    private val PIXIV_IMAGE = Regex("""pximg\.net/.*/(\d+)_p\d+""")
    private val TWITTER_STATUS = Regex("""(?:twitter\.com|x\.com)/[^/]+/status/(\d+)""")
}
