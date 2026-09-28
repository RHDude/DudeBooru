package app.dudebooru.booru.rec

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.PostTags
import app.dudebooru.booru.model.Rating
import app.dudebooru.booru.model.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecommenderTest {
    private var nextId = 1L
    private val day = 24L * 3600 * 1000
    private val now = 1_000L * day

    private fun post(
        artist: String = "artist_${nextId}",
        character: List<String> = emptyList(),
        copyright: List<String> = emptyList(),
        general: List<String> = listOf("1girl", "solo"),
    ) = Post(
        site = "danbooru", id = nextId++, createdAt = 0, rating = Rating.GENERAL, width = 1, height = 1,
        tags = PostTags(artist = listOf(artist), character = character, copyright = copyright, general = general),
    )

    /** Как на Danbooru: 1girl почти у всех, twintails у каждого восьмого, miku реже. */
    private val idf: (String) -> Double? = { tag ->
        when (tag) {
            "1girl" -> TasteProfile.idf(8_400_000, 9_000_000)
            "solo" -> TasteProfile.idf(7_000_000, 9_000_000)
            "twintails" -> TasteProfile.idf(1_200_000, 9_000_000)
            else -> null
        }
    }

    @Test
    fun commonTagsBarelyMatter() {
        val profile = TasteProfile.build(listOf(Signal(post(general = listOf("1girl", "twintails")), SignalKind.LIKE, now)), now, idf)
        assertTrue(profile.weight("twintails") > profile.weight("1girl") * 20)
    }

    @Test
    fun categoryWeightsAndSaveCountsDouble() {
        val p = post(artist = "artist_x", character = listOf("miku"), copyright = listOf("vocaloid"), general = listOf("smile"))
        val liked = TasteProfile.build(listOf(Signal(p, SignalKind.LIKE, now)), now)
        assertEquals(3.0 / 2.0, liked.weight("artist_x") / liked.weight("miku"), 1e-9)
        assertEquals(2.0 / 1.5, liked.weight("miku") / liked.weight("vocaloid"), 1e-9)
        val saved = TasteProfile.build(listOf(Signal(p, SignalKind.SAVE, now)), now)
        assertEquals(2.0, saved.weight("miku") / liked.weight("miku"), 1e-9)
    }

    @Test
    fun likesDecayWithHalfLifeOf60Days() {
        val p = post(character = listOf("miku"))
        val fresh = TasteProfile.build(listOf(Signal(p, SignalKind.LIKE, now)), now)
        val old = TasteProfile.build(listOf(Signal(p, SignalKind.LIKE, now - 60 * day)), now)
        assertEquals(0.5, old.weight("miku") / fresh.weight("miku"), 1e-9)
    }

    @Test
    fun notInterestedSubtracts() {
        val a = post(character = listOf("miku"))
        val b = post(character = listOf("miku"))
        val profile = TasteProfile.build(listOf(Signal(a, SignalKind.LIKE, now), Signal(b, SignalKind.DISLIKE, now)), now)
        assertEquals(0.0, profile.weight("miku"), 1e-9)
    }

    /** Приёмка ТЗ: после 20 лайков по одному персонажу он заметно преобладает, у каждого поста есть причина. */
    @Test
    fun twentyLikesOfOneCharacterDominate() {
        val signals = (1..20).map { Signal(post(character = listOf("hatsune_miku"), copyright = listOf("vocaloid")), SignalKind.LIKE, now) } +
            (1..5).map { Signal(post(character = listOf("other_girl_$it")), SignalKind.LIKE, now) }
        val profile = TasteProfile.build(signals, now, idf)
        assertEquals("hatsune_miku", profile.top(TagCategory.CHARACTER, 1).single().name)

        val candidates = (1..30).map { post(character = listOf("hatsune_miku")) } +
            (1..30).map { post(character = listOf("random_char_$it")) } +
            (1..30).map { post(character = listOf("other_girl_1")) }
        val ranked = Recommender.rank(candidates, profile, random = Random(1))
        val top = ranked.take(20)
        val miku = top.count { "hatsune_miku" in it.post.tags.character }
        assertTrue("miku should dominate top-20, got $miku", miku >= 14)
        assertTrue(ranked.filter { !it.explore }.all { it.reasons.isNotEmpty() })
    }

    @Test
    fun excludesSeenLikedSaved() {
        val profile = TasteProfile.build(listOf(Signal(post(character = listOf("miku")), SignalKind.LIKE, now)), now)
        val seen = post(character = listOf("miku"))
        val fresh = post(character = listOf("miku"))
        val ranked = Recommender.rank(listOf(seen, fresh), profile, exclude = setOf(seen.key), exploreShare = 0.0)
        assertEquals(listOf(fresh.key), ranked.map { it.post.key })
    }

    @Test
    fun noMoreThanTwoOfOneArtistInARow() {
        val profile = TasteProfile.build(listOf(Signal(post(artist = "star"), SignalKind.LIKE, now)), now)
        val list = (1..5).map { post(artist = "star") } + (1..3).map { post(artist = "other_$it") }
        val ranked = Recommender.rank(list, profile, exploreShare = 0.0)
        val artists = ranked.map { it.post.tags.artist.first() }
        for (i in 2 until artists.size) {
            val run = artists.subList(i - 2, i + 1)
            assertTrue("three in a row: $artists", !(run.all { it == "star" }) || artists.drop(i).all { it == "star" })
        }
    }

    @Test
    fun exploreShareRoughly15Percent() {
        val profile = TasteProfile.build(listOf(Signal(post(character = listOf("miku")), SignalKind.LIKE, now)), now, idf)
        val candidates = (1..85).map { post(character = listOf("miku")) } + (1..50).map { post(character = listOf("x$it")) }
        val ranked = Recommender.rank(candidates, profile, random = Random(3))
        val share = ranked.count { it.explore }.toDouble() / ranked.size
        assertTrue("explore share $share", share in 0.12..0.18)
    }

    @Test
    fun queriesFromTopTags() {
        val signals = listOf(Signal(post(artist = "artist_x", character = listOf("miku"), copyright = listOf("vocaloid"), general = listOf("twintails", "smile", "hat", "boots")), SignalKind.LIKE, now))
        val queries = Recommender.queries(TasteProfile.build(signals, now))
        assertTrue(queries.any { it.tags == listOf("artist_x") && it.kind == CandidateQuery.Kind.ARTIST_NEW })
        assertTrue(queries.any { it.tags == listOf("miku") })
        assertTrue(queries.any { it.tags.size == 2 })
    }
}
