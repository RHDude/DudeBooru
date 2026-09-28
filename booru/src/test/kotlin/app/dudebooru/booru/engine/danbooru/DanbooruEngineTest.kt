package app.dudebooru.booru.engine.danbooru

import app.dudebooru.booru.TestSupport
import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.engine.PageKey
import app.dudebooru.booru.engine.Session
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.model.Rating
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.site.EngineType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DanbooruEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: DanbooruEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = DanbooruEngine(TestSupport.site(server, EngineType.DANBOORU), TestSupport.http())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun parsesRealPostsResponse() = runBlocking {
        server.enqueue(TestSupport.json(TestSupport.fixture("danbooru_posts.json")))
        val page = engine.posts(FeedRequest(mode = ContentMode.SFW), null, 20)
        val post = page.posts.first()
        assertEquals(12271217L, post.id)
        assertEquals("test:12271217", post.key)
        assertEquals(Rating.GENERAL, post.rating)
        assertEquals(listOf("moyangahdid"), post.tags.artist)
        assertEquals(listOf("ikari_shinji"), post.tags.character)
        assertEquals(listOf("neon_genesis_evangelion", "rebuild_of_evangelion"), post.tags.copyright)
        assertEquals(listOf("highres"), post.tags.meta)
        assertEquals(1516, post.width)
        assertEquals(2048, post.height)
        assertEquals(850, post.sampleWidth)
        assertTrue(post.sampleUrl!!.contains("/sample/"))
        assertTrue(post.previewUrl!!.contains("/360x360/"))
        assertTrue(post.fileUrl!!.contains("/original/"))
        assertEquals("2422b16fa3c5059d86d95307c14d7ac0", post.md5)
        assertTrue(post.createdAt > 1_700_000_000_000)
        // Порядок по умолчанию листается курсором «старше id».
        assertEquals(PageKey.Before(page.posts.minOf { it.id }), page.next)
    }

    @Test
    fun buildsQueryWithinAnonymousLimit() = runBlocking {
        server.enqueue(TestSupport.json("[]"))
        engine.posts(FeedRequest(tags = listOf("hatsune_miku"), sort = SortOrder.BEST, mode = ContentMode.SFW), null, 50)
        val url = server.takeRequest().url
        assertEquals("/posts.json", url.encodedPath)
        assertEquals("rating:g,s order:score hatsune_miku", url.queryParameter("tags"))
        assertEquals("50", url.queryParameter("limit"))
    }

    @Test
    fun popularWithoutSearchUsesExploreAndFiltersRatingLocally() = runBlocking {
        server.enqueue(TestSupport.json(TestSupport.fixture("danbooru_posts.json")))
        val page = engine.posts(FeedRequest(sort = SortOrder.POPULAR_WEEK, mode = ContentMode.NSFW), null, 100)
        val url = server.takeRequest().url
        assertEquals("/explore/posts/popular.json", url.encodedPath)
        assertEquals("week", url.queryParameter("scale"))
        assertTrue("NSFW mode must hide general posts", page.posts.none { it.rating == Rating.GENERAL })
        assertEquals(PageKey.Number(2), page.next)
    }

    @Test
    fun popularWithSearchBecomesScoreAndAge() = runBlocking {
        server.enqueue(TestSupport.json("[]"))
        engine.posts(FeedRequest(tags = listOf("twintails"), sort = SortOrder.POPULAR_WEEK, mode = ContentMode.ALL), null, 20)
        assertEquals("age:<1w order:score twintails", server.takeRequest().url.queryParameter("tags"))
    }

    @Test
    fun localTermsFilterPosts() = runBlocking {
        // Сначала движок узнаёт число постов у тегов, чтобы отправить на сервер самый редкий.
        server.enqueue(
            TestSupport.json(
                """[{"name":"headphones","post_count":180000,"category":0},{"name":"ikari_shinji","post_count":30000,"category":4}]""",
            ),
        )
        server.enqueue(TestSupport.json(TestSupport.fixture("danbooru_posts.json")))
        // Лимит 2: сортировка занимает слот, на сервер уходит самый редкий тег, второй проверяется
        // локально — и страница берётся крупнее, чтобы экран заполнился.
        val page = engine.posts(
            FeedRequest(tags = listOf("headphones", "ikari_shinji"), sort = SortOrder.BEST, mode = ContentMode.ALL),
            null,
            20,
        )
        assertEquals("/tags.json", server.takeRequest().url.encodedPath)
        val request = server.takeRequest()
        assertEquals("order:score ikari_shinji", request.url.queryParameter("tags"))
        assertEquals("100", request.url.queryParameter("limit"))
        assertEquals(listOf("headphones"), page.plan.localTerms)
        assertFalse(page.plan.sortDropped)
        assertTrue(page.posts.isNotEmpty())
        assertTrue(page.posts.all { it.allTags.containsAll(listOf("headphones", "ikari_shinji")) })
    }

    @Test
    fun tagLimitErrorFromServerIsTyped() = runBlocking {
        server.enqueue(TestSupport.json("""{"success":false,"message":"You cannot search for more than 2 tags at a time."}""", code = 422))
        try {
            engine.posts(FeedRequest(tags = listOf("a")), null, 20, Session(tagLimit = 10))
            fail()
        } catch (e: BooruException.TagLimitExceeded) {
            assertEquals(2, e.limit)
        }
    }

    @Test
    fun verifyReadsLevelAndTagLimit() = runBlocking {
        server.enqueue(
            TestSupport.json(
                """{"id":42,"name":"dude","level":30,"level_string":"Gold","tag_query_limit":6,
                   "blacklisted_tags":"guro\nscat\n","favorite_count":12}""",
            ),
        )
        val info = engine.verify(engine.credentials(" dude ", " key123 "))
        assertEquals(42L, info.userId)
        assertEquals("Gold", info.levelName)
        assertEquals(6, info.tagLimit)
        assertEquals(listOf("guro", "scat"), info.blacklistedTags)
        val auth = server.takeRequest().headers["Authorization"]!!
        assertTrue(auth.startsWith("Basic "))
    }

    @Test
    fun platinumHasNoLimit() = runBlocking {
        server.enqueue(TestSupport.json("""{"id":1,"name":"rich","level":31,"level_string":"Platinum","tag_query_limit":null}"""))
        assertNull(engine.verify(Credentials.ApiKey("rich", "k")).tagLimit)
    }

    @Test(expected = BooruException.InvalidCredentials::class)
    fun badKeyIsInvalidCredentials() = runBlocking<Unit> {
        server.enqueue(TestSupport.json("""{"success":false,"message":"Invalid API key"}""", code = 401))
        engine.verify(Credentials.ApiKey("dude", "wrong"))
    }

    @Test(expected = BooruException.InvalidCredentials::class)
    fun anonymousProfileIsInvalidCredentials() = runBlocking<Unit> {
        server.enqueue(TestSupport.json("""{"id":null,"name":"Anonymous","level":0,"tag_query_limit":2}"""))
        engine.verify(Credentials.ApiKey("dude", "wrong"))
    }

    @Test
    fun autocompleteKeepsTagsAndAliases() = runBlocking {
        server.enqueue(
            TestSupport.json(
                """[{"type":"tag-alias","label":"miku","value":"hatsune_miku","category":4,"post_count":146574,"antecedent":"miku"},
                    {"type":"user","label":"miku_fan","value":"user:miku_fan"}]""",
            ),
        )
        val tags = engine.autocomplete("miku")
        assertEquals(1, tags.size)
        assertEquals("hatsune_miku", tags[0].name)
        assertEquals("miku", tags[0].antecedent)
        assertEquals(app.dudebooru.booru.model.TagCategory.CHARACTER, tags[0].category)
    }
}
