package app.dudebooru.booru.net

import app.dudebooru.booru.TestSupport
import app.dudebooru.booru.site.EngineType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class BooruHttpTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun get(path: String) = Request.Builder().url(server.url(path)).build()

    @Test
    fun retriesAfter429ThenSucceeds() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "0").build())
        server.enqueue(TestSupport.json("[]"))
        val http = TestSupport.http()
        val body = http.get(TestSupport.site(server, EngineType.DANBOORU), get("/posts.json"))
        assertEquals("[]", body)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun long429IsReportedWithTimer() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "42").build())
        val http = TestSupport.http()
        try {
            http.get(TestSupport.site(server, EngineType.DANBOORU), get("/posts.json"))
            fail("expected TooManyRequests")
        } catch (e: BooruException.TooManyRequests) {
            assertEquals(42, e.retryAfterSeconds)
        }
    }

    @Test
    fun serverErrorsAreRetriedThenReported() = runBlocking {
        repeat(3) { server.enqueue(TestSupport.json("{}", code = 502)) }
        try {
            TestSupport.http().get(TestSupport.site(server, EngineType.DANBOORU), get("/posts.json"))
            fail("expected ServerError")
        } catch (e: BooruException.ServerError) {
            assertEquals(502, e.code)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun htmlInsteadOfJson() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<!doctype html><title>Just a moment...</title>").build(),
        )
        try {
            TestSupport.http().get(TestSupport.site(server, EngineType.DANBOORU), get("/posts.json"))
            fail("expected NotJson")
        } catch (e: BooruException.NotJson) {
            assertTrue(e.contentType!!.contains("html"))
        }
    }

    @Test
    fun challengeWith403IsNotJsonButPlain403IsForbidden() = runBlocking {
        val http = TestSupport.http()
        val site = TestSupport.site(server, EngineType.DANBOORU)
        server.enqueue(MockResponse.Builder().code(403).addHeader("Content-Type", "text/html").body("<html>cf_chl challenge</html>").build())
        server.enqueue(TestSupport.json("""{"success":false,"reason":"access denied"}""", code = 403))
        try {
            http.get(site, get("/a.json")); fail()
        } catch (e: BooruException.NotJson) {
        }
        try {
            http.get(site, get("/b.json")); fail()
        } catch (e: BooruException.Forbidden) {
        }
    }

    @Test
    fun unauthorizedAndBadRequestCarryServerMessage() = runBlocking {
        val http = TestSupport.http()
        val site = TestSupport.site(server, EngineType.DANBOORU)
        server.enqueue(TestSupport.json("""{"success":false,"message":"Invalid API key"}""", code = 401))
        server.enqueue(TestSupport.json("""{"success":false,"message":"You cannot search for more than 2 tags at a time."}""", code = 422))
        try {
            http.get(site, get("/profile.json")); fail()
        } catch (e: BooruException.Unauthorized) {
        }
        try {
            http.get(site, get("/posts.json")); fail()
        } catch (e: BooruException.BadRequest) {
            assertEquals(422, e.code)
            assertTrue(e.serverMessage!!.contains("more than 2 tags"))
        }
    }

    @Test
    fun identicalRequestsInFlightAreCoalesced() = runBlocking {
        server.enqueue(TestSupport.json("[1]").newBuilder().bodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS).build())
        val http = TestSupport.http()
        val site = TestSupport.site(server, EngineType.DANBOORU)
        val results = (1..5).map { async { http.get(site, get("/same.json")) } }.awaitAll()
        assertTrue(results.all { it == "[1]" })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun userAgentIsAlwaysSet() = runBlocking {
        server.enqueue(TestSupport.json("[]"))
        TestSupport.http().get(TestSupport.site(server, EngineType.DANBOORU), get("/ua.json"))
        assertEquals("DudeBooru-test/1.0", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun redactorMasksSecrets() {
        val url = "https://yande.re/post/vote.json?id=1&login=dude&password_hash=abcdef0123&api_key=zzz"
        val safe = Redactor.redact(url)
        assertTrue(safe, "abcdef0123" !in safe && "zzz" !in safe && "dude" !in safe)
        assertEquals("authorization: Basic ***", Redactor.redact("authorization: Basic ZHVkZTprZXk="))
    }
}
