package app.dudebooru.booru

import app.dudebooru.booru.net.BooruHttp
import app.dudebooru.booru.net.RetryPolicy
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object TestSupport {
    fun fixture(name: String): String =
        requireNotNull(TestSupport::class.java.getResource("/fixtures/$name")) { "no fixture $name" }.readText()

    fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .body(body)
        .build()

    fun http(): BooruHttp = BooruHttp(
        client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
        userAgent = "DudeBooru-test/1.0",
        retry = RetryPolicy(backoffMillis = listOf(1, 1), defaultRetryAfterMillis = 1),
        sleep = {},
    )

    fun site(server: MockWebServer, engine: EngineType, id: String = "test", salt: String? = null) = SiteConfig(
        id = id,
        name = id,
        engine = engine,
        baseUrl = server.url("/").toString().trimEnd('/'),
        passwordSalt = salt,
        maxPageSize = 200,
        anonymousTagLimit = if (engine == EngineType.DANBOORU) 2 else 6,
        requestsPerSecond = 1000.0,
        burst = 1000,
    )
}
