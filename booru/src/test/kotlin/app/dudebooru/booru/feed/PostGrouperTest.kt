package app.dudebooru.booru.feed

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.PostTags
import app.dudebooru.booru.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostGrouperTest {
    private fun post(
        id: Long,
        parent: Long? = null,
        children: Boolean = false,
        artist: String? = "artist_x",
        source: String? = null,
        pixiv: Long? = null,
        pools: List<Long> = emptyList(),
    ) = Post(
        site = "danbooru", id = id, createdAt = 0, rating = Rating.GENERAL, width = 1, height = 1,
        parentId = parent, hasChildren = children, source = source, pixivId = pixiv, poolIds = pools,
        tags = PostTags(artist = listOfNotNull(artist)),
    )

    @Test
    fun parentAndChildrenBecomeOneCarousel() {
        val groups = PostGrouper.group(listOf(post(13, parent = 10), post(12, parent = 10), post(11), post(10, children = true)))
        assertEquals(2, groups.size)
        assertEquals(listOf(10L, 12L, 13L), groups[0].map { it.id })
        assertEquals(listOf(11L), groups[1].map { it.id })
    }

    @Test
    fun samePixivIdGroups() {
        val groups = PostGrouper.group(listOf(post(5, pixiv = 777), post(4, artist = "b"), post(3, pixiv = 777)))
        assertEquals(listOf(listOf(3L, 5L), listOf(4L)), groups.map { g -> g.map { it.id } })
    }

    @Test
    fun sameSourceSameArtistGroupsButProfilesDoNot() {
        val tweet = "https://x.com/artist_x/status/2103089377253687617/photo/1"
        val tweet2 = "https://twitter.com/artist_x/status/2103089377253687617/photo/2"
        val profile = "https://x.com/artist_x"
        val groups = PostGrouper.group(
            listOf(post(4, source = tweet), post(3, source = tweet2), post(2, source = profile), post(1, source = profile)),
        )
        assertEquals(listOf(listOf(3L, 4L), listOf(2L), listOf(1L)), groups.map { g -> g.map { it.id } })
    }

    @Test
    fun differentArtistsWithSameSourceStaySeparate() {
        val src = "https://www.pixiv.net/artworks/149749360"
        val groups = PostGrouper.group(listOf(post(2, source = src, artist = "a"), post(1, source = src, artist = "b")))
        assertEquals(2, groups.size)
    }

    @Test
    fun onlyAdjacentPoolPostsGroup() {
        val groups = PostGrouper.group(listOf(post(5, pools = listOf(1)), post(4, pools = listOf(1)), post(3, artist = "b"), post(2, pools = listOf(1))))
        assertEquals(listOf(listOf(4L, 5L), listOf(3L), listOf(2L)), groups.map { g -> g.map { it.id } })
    }

    @Test
    fun pixivImageUrlNormalizesToArtwork() {
        assertEquals("pixiv:148985121", PostGrouper.normalizeSource("https://i.pximg.net/img-original/img/2026/08/28/19/29/22/148985121_p0.png"))
        assertEquals("pixiv:149749360", PostGrouper.normalizeSource("https://www.pixiv.net/en/artworks/149749360"))
        assertNull(PostGrouper.normalizeSource("https://x.com/artist"))
    }

    @Test
    fun familyRoot() {
        assertEquals(10L, PostGrouper.familyRoot(listOf(post(12, parent = 10))))
        assertEquals(7L, PostGrouper.familyRoot(listOf(post(7, children = true))))
        assertNull(PostGrouper.familyRoot(listOf(post(7))))
    }
}
