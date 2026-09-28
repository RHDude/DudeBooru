package app.dudebooru.booru.engine

import app.dudebooru.booru.engine.danbooru.DanbooruEngine
import app.dudebooru.booru.engine.moebooru.MoebooruEngine
import app.dudebooru.booru.net.BooruException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryPlannerTest {
    private val dan = DanbooruEngine.RULES

    @Test
    fun `rating and age are free, order takes a slot`() {
        val plan = QueryPlanner.plan(
            siteId = "danbooru",
            rules = dan,
            userTerms = listOf("hatsune_miku"),
            systemTerms = listOf("rating:g,s", "age:<1w"),
            orderTerm = "order:score",
            limit = 2,
        )
        assertEquals(listOf("rating:g,s", "age:<1w", "order:score", "hatsune_miku"), plan.serverTerms)
        assertTrue(plan.localTerms.isEmpty())
    }

    @Test
    fun `over the limit the rarest tags go to the server, the rest are checked locally`() {
        val plan = QueryPlanner.plan(
            siteId = "danbooru",
            rules = dan,
            userTerms = listOf("1girl", "twintails", "hatsune_miku", "-comic"),
            systemTerms = listOf("rating:g,s"),
            orderTerm = null,
            limit = 2,
            postCounts = mapOf("1girl" to 8_000_000L, "twintails" to 1_200_000L, "hatsune_miku" to 146_000L),
        )
        assertEquals(listOf("rating:g,s", "hatsune_miku", "twintails"), plan.serverTerms)
        assertEquals(listOf("1girl", "-comic"), plan.localTerms)
    }

    @Test
    fun `free metatags never go local`() {
        val plan = QueryPlanner.plan(
            siteId = "danbooru",
            rules = dan,
            userTerms = listOf("score:>100", "width:>=1920", "a", "b", "c"),
            systemTerms = emptyList(),
            orderTerm = "order:score",
            limit = 2,
        )
        assertTrue(plan.serverTerms.containsAll(listOf("score:>100", "width:>=1920")))
        assertEquals(2, plan.serverTerms.count { it in setOf("a", "b", "c") })
        assertEquals(1, plan.localTerms.size)
    }

    @Test
    fun `two tags on a free account beat the sort`() {
        val plan = QueryPlanner.plan(
            siteId = "danbooru",
            rules = dan,
            userTerms = listOf("hatsune_miku", "twintails"),
            systemTerms = listOf("rating:g,s"),
            orderTerm = "order:rank",
            limit = 2,
        )
        assertEquals(listOf("rating:g,s", "hatsune_miku", "twintails"), plan.serverTerms)
        assertTrue(plan.localTerms.isEmpty())
        assertTrue(plan.sortDropped)
    }

    @Test
    fun `sort takes the slot a negative tag would have taken`() {
        val plan = QueryPlanner.plan("danbooru", dan, listOf("hatsune_miku", "-comic"), emptyList(), "order:rank", limit = 2)
        assertEquals(listOf("order:rank", "hatsune_miku"), plan.serverTerms)
        assertEquals(listOf("-comic"), plan.localTerms)
        assertFalse(plan.sortDropped)
    }

    @Test
    fun `a tag with a colon that is not a metatag counts as a tag`() {
        assertEquals(null, dan.metatagName("re:zero_kara_hajimeru_isekai_seikatsu"))
        assertEquals("user", dan.metatagName("-user:someone"))
        assertTrue(dan.isFree("rating:e"))
        assertFalse(dan.isFree("order:score"))
    }

    @Test(expected = BooruException.TagLimitExceeded::class)
    fun `mandatory server-only metatags over the limit fail loudly`() {
        QueryPlanner.plan("danbooru", dan, listOf("user:a", "fav:b", "pool:1"), emptyList(), null, limit = 2)
    }

    @Test
    fun `sort that does not fit is dropped and reported`() {
        val plan = QueryPlanner.plan("danbooru", dan, listOf("user:a", "fav:b"), emptyList(), "order:score", limit = 2)
        assertTrue(plan.sortDropped)
        assertFalse(plan.serverTerms.contains("order:score"))
    }

    @Test
    fun `user order overrides sort order`() {
        val plan = QueryPlanner.plan("danbooru", dan, listOf("order:favcount"), emptyList(), "order:score", limit = 2)
        assertEquals(listOf("order:favcount"), plan.serverTerms)
    }

    @Test
    fun `unlimited accounts send everything`() {
        val terms = listOf("a", "b", "c", "d", "-e", "~f", "~g")
        val plan = QueryPlanner.plan("danbooru", dan, terms, listOf("rating:q,e"), "order:rank", limit = null)
        assertTrue(plan.localTerms.isEmpty())
        assertTrue(plan.serverTerms.containsAll(terms))
    }

    @Test
    fun `moebooru limits positives and negatives separately, metatags are free`() {
        val positives = (1..8).map { "p$it" }
        val negatives = (1..7).map { "-n$it" }
        val plan = QueryPlanner.plan(
            siteId = "yandere",
            rules = MoebooruEngine.RULES,
            userTerms = positives + negatives + listOf("width:>1000", "user:x"),
            systemTerms = listOf("rating:s"),
            orderTerm = "order:score",
            limit = 6,
            moebooruLimits = true,
        )
        assertEquals(6, plan.serverTerms.count { it.startsWith("p") })
        assertEquals(6, plan.serverTerms.count { it.startsWith("-n") })
        assertTrue(plan.serverTerms.containsAll(listOf("rating:s", "width:>1000", "user:x", "order:score")))
        assertEquals(3, plan.localTerms.size)
    }
}
