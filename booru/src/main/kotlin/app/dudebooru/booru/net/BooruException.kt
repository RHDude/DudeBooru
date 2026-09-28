package app.dudebooru.booru.net

import java.io.IOException

/**
 * Ошибки сайтов по видам из раздела «Сайт недоступен»: у каждой свой текст и своё поведение.
 * Сообщения — для журнала, без секретов; тексты для людей живут в приложении.
 */
sealed class BooruException(val siteId: String, message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** Таймаут или нет соединения с хостом. */
    class NotResponding(siteId: String, cause: Throwable?) :
        BooruException(siteId, "$siteId: not responding (${cause?.javaClass?.simpleName})", cause)

    /** Ответ 5xx. */
    class ServerError(siteId: String, val code: Int) :
        BooruException(siteId, "$siteId: server error $code")

    /** Ответ 429. [retryAfterSeconds] — сколько ждать до повтора. */
    class TooManyRequests(siteId: String, val retryAfterSeconds: Long) :
        BooruException(siteId, "$siteId: too many requests, retry after ${retryAfterSeconds}s")

    /** Ответ 403: блокировка по стране/IP, закрытый раздел. */
    class Forbidden(siteId: String) :
        BooruException(siteId, "$siteId: forbidden")

    /** Ответ 401: ключ неверный или удалён. */
    class Unauthorized(siteId: String) :
        BooruException(siteId, "$siteId: unauthorized")

    /** Вместо JSON пришла веб-страница, например проверка «вы не робот». */
    class NotJson(siteId: String, val contentType: String?) :
        BooruException(siteId, "$siteId: expected JSON, got ${contentType ?: "unknown content"}")

    /** Проверочный запрос при входе не прошёл. */
    class InvalidCredentials(siteId: String) :
        BooruException(siteId, "$siteId: invalid credentials")

    /** Нельзя уложить обязательные части запроса в лимит тегов аккаунта. */
    class TagLimitExceeded(siteId: String, val limit: Int, val required: Int) :
        BooruException(siteId, "$siteId: tag limit $limit, query needs $required")

    /** Прочие 4xx: неверный запрос, не найдено, ошибка валидации. */
    class BadRequest(siteId: String, val code: Int, val serverMessage: String?) :
        BooruException(siteId, "$siteId: HTTP $code${serverMessage?.let { " — $it" } ?: ""}")

    /** Ответ разобрать не удалось. */
    class Malformed(siteId: String, cause: Throwable) :
        BooruException(siteId, "$siteId: malformed response (${cause.message?.take(200)})", cause)
}
