package app.dudebooru.booru.net

import app.dudebooru.booru.site.SiteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

val BooruJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

data class RetryPolicy(
    val maxAttempts: Int = 3,
    /** Паузы перед повторами после обрыва и 5xx. */
    val backoffMillis: List<Long> = listOf(1_000, 3_000),
    /** 429 с паузой не длиннее этой ждём внутри запроса, более длинную отдаём наверх с таймером. */
    val maxInlineRetryAfterMillis: Long = 10_000,
    /** Если сайт не прислал Retry-After. */
    val defaultRetryAfterMillis: Long = 2_000,
)

/**
 * HTTP для всех движков: ограничитель на каждый сайт, повтор с паузой на 429/5xx/обрыв,
 * склейка одинаковых GET в полёте, понятные ошибки вместо кодов.
 */
class BooruHttp(
    client: OkHttpClient,
    userAgent: String,
    private val retry: RetryPolicy = RetryPolicy(),
    private val logger: (String) -> Unit = {},
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val client: OkHttpClient = client.newBuilder()
        .addInterceptor(UserAgentInterceptor(userAgent))
        .build()

    private val limiters = ConcurrentHashMap<String, RateLimiter>()
    private val flights = SingleFlight<String, String>(scope)

    /** Зеркала одного сайта делят общий бюджет запросов. */
    private fun limiterFor(site: SiteConfig): RateLimiter =
        limiters.computeIfAbsent(site.id) { RateLimiter(site.requestsPerSecond, site.burst) }

    /**
     * GET с повторами. Одинаковые запросы (адрес + вход) в полёте выполняются один раз.
     * [background] — запрос уступает ленте: ждёт запаса в ограничителе.
     */
    suspend fun get(site: SiteConfig, request: Request, background: Boolean = false): String {
        require(request.method == "GET") { "use send() for ${request.method}" }
        val key = request.url.toString() + "|" + request.header("Authorization").hashCode()
        return flights.run(key) { execute(site, request, idempotent = true, background = background) }
    }

    /** Запросы с побочными эффектами: повтор только после 429, когда сайт сам попросил подождать. */
    suspend fun send(site: SiteConfig, request: Request): String = execute(site, request, idempotent = false)

    private suspend fun execute(site: SiteConfig, request: Request, idempotent: Boolean, background: Boolean = false): String {
        val limiter = limiterFor(site)
        val safeUrl = Redactor.redact(request.url.toString())
        var attempt = 0
        while (true) {
            attempt++
            val last = attempt >= retry.maxAttempts
            limiter.acquire(reserve = if (background) BACKGROUND_RESERVE else 0)
            val response = try {
                client.newCall(request).await()
            } catch (e: IOException) {
                logger("${request.method} $safeUrl failed: ${e.javaClass.simpleName}")
                if (idempotent && !last) {
                    sleep(backoff(attempt))
                    continue
                }
                throw BooruException.NotResponding(site.id, e)
            }

            val code = response.code
            val contentType = response.header("Content-Type")
            val retryAfterHeader = response.header("Retry-After")
            val body = withContext(Dispatchers.IO) { response.use { it.body.string() } }
            logger("${request.method} $safeUrl -> $code (${body.length} B)")

            when {
                code in 200..299 -> {
                    if (looksLikeHtml(contentType, body)) throw BooruException.NotJson(site.id, contentType)
                    return body
                }
                code == 429 -> {
                    val waitMs = parseRetryAfter(retryAfterHeader) ?: (retry.defaultRetryAfterMillis shl (attempt - 1))
                    limiter.pauseFor(waitMs)
                    if (!last && waitMs <= retry.maxInlineRetryAfterMillis) {
                        sleep(waitMs)
                        continue
                    }
                    throw BooruException.TooManyRequests(site.id, (waitMs + 999) / 1000)
                }
                code >= 500 -> {
                    if (idempotent && !last) {
                        sleep(backoff(attempt))
                        continue
                    }
                    throw BooruException.ServerError(site.id, code)
                }
                code == 401 -> throw BooruException.Unauthorized(site.id)
                code == 403 -> {
                    if (looksLikeHtml(contentType, body) && looksLikeChallenge(body)) {
                        throw BooruException.NotJson(site.id, contentType)
                    }
                    throw BooruException.Forbidden(site.id)
                }
                else -> throw BooruException.BadRequest(site.id, code, serverMessage(body))
            }
        }
    }

    private fun backoff(attempt: Int): Long {
        val base = retry.backoffMillis.getOrElse(attempt - 1) { retry.backoffMillis.lastOrNull() ?: 1_000 }
        return base + Random.nextLong(0, base / 4 + 1)
    }

    private class UserAgentInterceptor(private val userAgent: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header("User-Agent") != null) return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
        }
    }

    companion object {
        /** Сколько токенов фоновый запрос оставляет ленте. */
        const val BACKGROUND_RESERVE = 2

        internal fun looksLikeHtml(contentType: String?, body: String): Boolean {
            if (contentType != null && contentType.contains("html", ignoreCase = true)) return true
            val start = body.trimStart()
            return start.startsWith("<!doctype", ignoreCase = true) || start.startsWith("<html", ignoreCase = true)
        }

        private fun looksLikeChallenge(body: String): Boolean {
            val lower = body.lowercase()
            return "just a moment" in lower || "cf_chl" in lower || "challenge" in lower || "captcha" in lower
        }

        /** Секунды или HTTP-дата. */
        internal fun parseRetryAfter(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            value.trim().toLongOrNull()?.let { return (it * 1000).coerceAtLeast(0) }
            return runCatching {
                val date = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                (date.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
            }.getOrNull()
        }

        /** Danbooru: `message`, Moebooru: `reason`. */
        internal fun serverMessage(body: String): String? = runCatching {
            val obj = BooruJson.parseToJsonElement(body) as? JsonObject ?: return null
            (obj["message"] ?: obj["reason"] ?: obj["error"])?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
}
