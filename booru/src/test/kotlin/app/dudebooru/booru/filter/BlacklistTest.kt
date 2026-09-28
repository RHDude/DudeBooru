package app.dudebooru.booru.filter

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.PostTags
import app.dudebooru.booru.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlacklistTest {
    private fun post(vararg tags: String, rating: Rating = Rating.GENERAL, score: Int = 0, site: String = "danbooru") = Post(
        site = site, id = 1, createdAt = 0, rating = rating, score = score, width = 1, height = 1,
        tags = PostTags(general = tags.toList()),
    )

    private fun list(vararg lines: String, site: String? = null) =
        Blacklist(lines.mapIndexed { i, line -> BlacklistEntry(i.toLong(), line, site) })

    @Test
    fun singleTag() {
        val bl = list("guro")
        assertNotNull(bl.match(post("guro", "1girl")))
        assertNull(bl.match(post("1girl")))
    }

    @Test
    fun combinationNeedsAllTags() {
        val bl = list("furry male_focus")
        assertNull(bl.match(post("furry")))
        assertNotNull(bl.match(post("furry", "male_focus")))
    }

    @Test
    fun negationAndRating() {
        // Как у Danbooru по умолчанию: «furry -rating:g» — furry, если пост не general.
        val bl = list("furry -rating:g")
        assertNull(bl.match(post("furry", rating = Rating.GENERAL)))
        assertNotNull(bl.match(post("furry", rating = Rating.QUESTIONABLE)))
    }

    @Test
    fun scoreAndWildcard() {
        assertNotNull(list("score:<0").match(post("x", score = -3)))
        assertNull(list("score:<0").match(post("x", score = 5)))
        assertNotNull(list("*_(cosplay)").match(post("hatsune_miku_(cosplay)")))
    }

    @Test
    fun siteScopedEntryOnlyAppliesThere() {
        val bl = list("comic", site = "yandere")
        assertNull(bl.match(post("comic", site = "danbooru")))
        assertNotNull(bl.match(post("comic", site = "yandere")))
    }

    @Test
    fun partitionReportsWhichLineHid() {
        val bl = list("guro", "comic")
        val (visible, hidden) = bl.partition(listOf(post("a"), post("comic"), post("guro")))
        assertEquals(1, visible.size)
        assertEquals(listOf("comic", "guro"), hidden.map { it.second.expression })
    }
}
