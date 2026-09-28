package app.dudebooru.booru.engine.moebooru

import app.dudebooru.booru.TestSupport
import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.engine.PageKey
import app.dudebooru.booru.engine.TagLookup
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.model.Rating
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.model.TagCategory
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class MoebooruEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: MoebooruEngine

    private val lookup = object : TagLookup {
        override suspend fun postCounts(site: SiteConfig, names: Collection<String>) = emptyMap<String, Long>()
        override suspend fun categories(site: SiteConfig, names: Collection<String>) =
            names.associateWith { if (it == "genshin_impact") TagCategory.COPYRIGHT else TagCategory.GENERAL }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = MoebooruEngine(
            site = TestSupport.site(server, EngineType.MOEBOORU, salt = "choujin-steiner--PASSWORD--"),
            http = TestSupport.http(),
            tags = lookup,
            today = { LocalDate.of(2026, 9, 27) },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun parsesV2ResponseWithTagTypes() = runBlocking {
        server.enqueue(TestSupport.json(TestSupport.fixture("moebooru_posts_v2.json")))
        val page = engine.posts(FeedRequest(mode = ContentMode.ALL), null, 40)
        val url = server.takeRequest().url
        assertEquals("/post.json", url.encodedPath)
        assertEquals("2", url.queryParameter("api_version"))
        assertEquals("1", url.queryParameter("include_tags"))

        val post = page.posts.first { it.id == 1269628L }
        assertEquals(Rating.QUESTIONABLE, post.rating)
        assertEquals(listOf("ssong2"), post.tags.artist)
        assertEquals(listOf("blue_archive"), post.tags.copyright)
        assertEquals(listOf("tsukatsuki_rio"), post.tags.character)
        assertEquals(1790533783000L, post.createdAt)
        assertEquals("jpg", post.fileExt)
        // У JPG-оригинала jpeg_url совпадает с file_url, облегчённая — sample.
        assertTrue(post.lightUrl!!.contains("/sample/"))
        assertEquals(333436L, post.lightSize)
        assertEquals(PageKey.Number(2), page.next)
    }

    @Test
    fun sfwModeFiltersAndSendsRating() = runBlocking {
        server.enqueue(TestSupport.json(TestSupport.fixture("moebooru_posts_v2.json")))
        val page = engine.posts(FeedRequest(tags = listOf("blue_archive"), mode = ContentMode.SFW), null, 40)
        assertEquals("rating:s blue_archive", server.takeRequest().url.queryParameter("tags"))
        assertTrue(page.posts.none { it.rating.isNsfw })
    }

    @Test
    fun nsfwModeExcludesSafe() = runBlocking {
        server.enqueue(TestSupport.json("""{"posts":[]}"""))
        val page = engine.posts(FeedRequest(mode = ContentMode.NSFW), null, 40)
        assertEquals("-rating:s", server.takeRequest().url.queryParameter("tags"))
        assertNull(page.next)
    }

    @Test
    fun popularWithoutSearchUsesPopularRecentAndDictionary() = runBlocking {
        server.enqueue(TestSupport.json(TestSupport.fixture("moebooru_popular.json")))
        val page = engine.posts(FeedRequest(sort = SortOrder.POPULAR_WEEK, mode = ContentMode.ALL), null, 40)
        val url = server.takeRequest().url
        assertEquals("/post/popular_recent.json", url.encodedPath)
        assertEquals("1w", url.queryParameter("period"))
        assertNull("popular_recent has a single page", page.next)
        assertTrue(page.posts.first().tags.copyright.contains("genshin_impact"))
    }

    @Test
    fun hotWithSearchBecomesScoreAndDate() = runBlocking {
        server.enqueue(TestSupport.json("""{"posts":[]}"""))
        engine.posts(FeedRequest(tags = listOf("hatsune_miku"), sort = SortOrder.HOT, mode = ContentMode.ALL), null, 40)
        assertEquals("date:>=2026-09-26 order:score hatsune_miku", server.takeRequest().url.queryParameter("tags"))
    }

    @Test
    fun verifyUsesVoteWithoutScore() = runBlocking {
        server.enqueue(TestSupport.json("""[{"name":"Dude","id":77}]"""))
        server.enqueue(TestSupport.json(TestSupport.fixture("moebooru_posts_v2.json")))
        server.enqueue(TestSupport.json("""{"success":true,"vote":0}"""))
        val creds = engine.credentials("dude", "hunter2") as Credentials.PasswordHash
        assertEquals("1fc0adf8544b5cb927ac1895f8e67c042e6e8dba", creds.passwordHash)

        val info = engine.verify(creds)
        assertEquals(77L, info.userId)
        assertEquals("Dude", info.login)

        server.takeRequest()
        server.takeRequest()
        val vote = server.takeRequest()
        assertEquals("/post/vote.json", vote.url.encodedPath)
        assertEquals("POST", vote.method)
        val form = vote.body!!.utf8()
        assertTrue(form.contains("password_hash=1fc0adf8544b5cb927ac1895f8e67c042e6e8dba"))
        assertTrue("verification must not change votes", !form.contains("score="))
    }

    @Test(expected = BooruException.InvalidCredentials::class)
    fun wrongPasswordIsInvalidCredentials() = runBlocking<Unit> {
        server.enqueue(TestSupport.json("""[{"name":"dude","id":77}]"""))
        server.enqueue(TestSupport.json(TestSupport.fixture("moebooru_posts_v2.json")))
        server.enqueue(TestSupport.json("""{"success":false,"reason":"access denied"}""", code = 403))
        engine.verify(engine.credentials("dude", "wrong"))
    }

    @Test(expected = BooruException.InvalidCredentials::class)
    fun unknownUserIsInvalidCredentials() = runBlocking<Unit> {
        server.enqueue(TestSupport.json("[]"))
        engine.verify(engine.credentials("nobody", "x"))
    }

    @Test
    fun artistResolvesAlias() = runBlocking {
        server.enqueue(
            TestSupport.json(
                """[{"id":36675,"name":"ssong2","alias_id":null,"urls":["https://twitter.com/ssong2ne"]},
                    {"id":41506,"name":"ssong2ne","alias_id":36675,"urls":[]}]""",
            ),
        )
        val artist = engine.artist("ssong2ne")!!
        assertEquals("ssong2", artist.name)
        assertEquals(listOf("ssong2ne"), artist.otherNames)
        assertEquals(listOf("https://twitter.com/ssong2ne"), artist.urls)
    }

    @Test
    fun relativeUrlsBecomeAbsolute() {
        assertEquals("https://konachan.com/data/x.jpg", MoebooruEngine.absolute("//konachan.com/data/x.jpg", "https://konachan.com"))
        assertEquals("https://konachan.net/data/x.jpg", MoebooruEngine.absolute("/data/x.jpg", "https://konachan.net"))
    }

    @Test
    fun tagSummaryFixture() = runBlocking {
        server.enqueue(TestSupport.json(TestSupport.fixture("moebooru_tag_summary.json")))
        val summary = engine.tagSummary()
        assertTrue(summary.version > 0)
        assertTrue(summary.entries.size > 50)
        assertTrue(summary.entries.any { it.name == "wallpaper" && "wallpapers" in it.aliases })
    }
}
