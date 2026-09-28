package app.dudebooru.booru.engine

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.PostTags
import app.dudebooru.booru.model.Rating
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMatcherTest {
    private val post = Post(
        site = "danbooru", id = 1, createdAt = 0, rating = Rating.GENERAL, width = 1, height = 1,
        tags = PostTags(
            artist = listOf("artist_x"),
            character = listOf("hatsune_miku"),
            general = listOf("1girl", "twintails", "smile"),
        ),
    )

    @Test
    fun plainAndNegated() {
        assertTrue(LocalMatcher(listOf("twintails", "-comic")).matches(post))
        assertFalse(LocalMatcher(listOf("twintails", "-smile")).matches(post))
        assertFalse(LocalMatcher(listOf("long_hair")).matches(post))
    }

    @Test
    fun wildcards() {
        assertTrue(LocalMatcher(listOf("hatsune*")).matches(post))
        assertFalse(LocalMatcher(listOf("-*miku")).matches(post))
        assertTrue(LocalMatcher(listOf("*tail*")).matches(post))
    }

    @Test
    fun orGroup() {
        assertTrue(LocalMatcher(listOf("~comic", "~smile")).matches(post))
        assertFalse(LocalMatcher(listOf("~comic", "~monochrome")).matches(post))
    }

    @Test
    fun emptyMatchesEverything() {
        assertTrue(LocalMatcher(emptyList()).isEmpty)
        assertTrue(LocalMatcher(emptyList()).matches(post))
    }
}
