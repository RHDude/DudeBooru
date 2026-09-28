package app.dudebooru.booru

import app.dudebooru.booru.engine.BooruEngine
import app.dudebooru.booru.engine.Engines
import app.dudebooru.booru.engine.FeedRequest
import app.dudebooru.booru.engine.moebooru.MoebooruEngine
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.net.BooruHttp
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.booru.site.Sites
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Живые сайты. Запуск: `./gradlew :booru:test -Plive --tests '*LiveApiTest*'`.
 * Если задан HTTPS_PROXY (например, локальный VPN-клиент), запросы идут через него.
 */
class LiveApiTest {
    private val http = BooruHttp(clientWithEnvProxy(), "DudeBooru/0.1 (dev build; live tests)", logger = { println(it) })

    private fun clientWithEnvProxy(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        val env = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy")
        val url = env?.let { java.net.URI(it) }
        if (url?.host != null && url.port > 0) {
            builder.proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(url.host, url.port)))
        }
        return builder.build()
    }

    @Before
    fun onlyWhenAsked() {
        assumeTrue("live tests are off", System.getProperty("dudebooru.live") == "true")
    }

    private fun engine(site: SiteConfig): BooruEngine = Engines.create(site, http)

    @Test
    fun everySiteServesSfwFeed() = runBlocking {
        for (site in listOf(Sites.DANBOORU, Sites.SAFEBOORU, Sites.YANDERE, Sites.KONACHAN)) {
            val page = engine(site).posts(FeedRequest(mode = ContentMode.SFW), null, 20)
            println("${site.name}: raw=${page.rawCount} visible=${page.posts.size} q='${page.plan.query}'")
            assertTrue("${site.name} returned nothing", page.rawCount > 0)
            assertTrue("${site.name} leaked NSFW", page.posts.none { it.rating.isNsfw })
            val first = page.posts.first()
            assertTrue("${site.name} post without image", first.sampleUrl != null)
            assertTrue(first.width > 0 && first.height > 0)
            assertNotNull(page.next)
        }
    }

    @Test
    fun hotAndPopularAreFresh() = runBlocking {
        val weekAgo = System.currentTimeMillis() - 8L * 24 * 3600 * 1000
        for (site in listOf(Sites.DANBOORU, Sites.YANDERE)) {
            val e = engine(site)
            val hot = e.posts(FeedRequest(sort = SortOrder.HOT, mode = ContentMode.ALL), null, 40)
            println("${site.name} hot: ${hot.posts.take(5).map { it.id to it.score }}")
            assertTrue(hot.posts.isNotEmpty())
            val pop = e.posts(FeedRequest(sort = SortOrder.POPULAR_WEEK, mode = ContentMode.ALL), null, 40)
            assertTrue(pop.posts.isNotEmpty())
            if (site.engine == EngineType.DANBOORU) {
                assertTrue("hot must be recent", hot.posts.count { it.createdAt > weekAgo } > hot.posts.size / 2)
            }
        }
    }

    @Test
    fun anonymousDanbooruSearchOverTheLimitStillWorks() = runBlocking {
        val page = engine(Sites.DANBOORU).posts(
            FeedRequest(tags = listOf("1girl", "twintails", "hatsune_miku"), sort = SortOrder.BEST, mode = ContentMode.SFW),
            null,
            100,
        )
        println("plan: server='${page.plan.query}' local=${page.plan.localTerms} visible=${page.posts.size}/${page.rawCount}")
        assertTrue(page.plan.serverTerms.contains("hatsune_miku"))
        assertEquals(2, page.plan.localTerms.size)
        assertTrue(page.posts.all { it.allTags.containsAll(listOf("1girl", "twintails", "hatsune_miku")) })
    }

    @Test
    fun moebooruArtistsAndPools() = runBlocking {
        val page = engine(Sites.YANDERE).posts(FeedRequest(mode = ContentMode.ALL), null, 40)
        assertTrue("artists resolved from api_version=2", page.posts.count { it.tags.artist.isNotEmpty() } > page.posts.size / 2)
    }

    @Test
    fun autocompleteAndTagInfo() = runBlocking {
        val dan = engine(Sites.DANBOORU).autocomplete("hatsune")
        assertEquals("hatsune_miku", dan.first().name)
        val moe = engine(Sites.YANDERE).autocomplete("hatsune")
        assertEquals("hatsune_miku", moe.first().name)
        val info = engine(Sites.DANBOORU).tagInfo(listOf("1girl", "twintails"))
        assertEquals(2, info.size)
        assertTrue(info.all { (it.postCount ?: 0) > 100_000 })
    }

    @Test
    fun yandereTagSummary() = runBlocking {
        val summary = (engine(Sites.YANDERE) as MoebooruEngine).tagSummary()
        println("yande.re summary v${summary.version}: ${summary.entries.size} tags")
        assertTrue(summary.entries.size > 10_000)
    }

    @Test
    fun wrongDanbooruKeyIsRejected() = runBlocking {
        try {
            engine(Sites.DANBOORU).verify(engine(Sites.DANBOORU).credentials("dudebooru_nobody_404", "definitely_wrong"))
            error("must fail")
        } catch (e: BooruException.InvalidCredentials) {
        }
    }

    @Test
    fun wrongYanderePasswordIsRejectedWithoutSideEffects() = runBlocking {
        val e = engine(Sites.YANDERE)
        try {
            e.verify(e.credentials("wq15987654", "definitely_wrong_password"))
            error("must fail")
        } catch (ex: BooruException.InvalidCredentials) {
        }
    }

    @Test
    fun detectsEngines() = runBlocking {
        assertEquals(EngineType.DANBOORU, Engines.detect("danbooru.donmai.us", http)?.engine)
        assertEquals(EngineType.MOEBOORU, Engines.detect("https://konachan.net/", http)?.engine)
    }
}
