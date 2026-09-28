package app.dudebooru.booru.site

import app.dudebooru.booru.model.ContentMode
import kotlinx.serialization.Serializable

@Serializable
enum class EngineType { DANBOORU, MOEBOORU }

/**
 * Сайт — это конфиг поверх движка. Safebooru — тот же Danbooru с другим адресом,
 * Konachan в режиме SFW переезжает на безопасное зеркало `.net`.
 */
@Serializable
data class SiteConfig(
    val id: String,
    val name: String,
    val engine: EngineType,
    val baseUrl: String,
    /** Безопасное зеркало для режима SFW (konachan.net). */
    val safeBaseUrl: String? = null,
    /** Сайт сам показывает только безопасное (Safebooru). В режиме NSFW папка скрывается. */
    val safeOnly: Boolean = false,
    /** Соль для SHA1-хеша пароля Moebooru: `соль` с `ПАРОЛЬ` на месте пароля. */
    val passwordSalt: String? = null,
    /** Аккаунты общие для сайтов с одинаковой группой (Danbooru и Safebooru). */
    val accountGroup: String = id,
    val supportsLogin: Boolean = true,
    /** Максимум постов за запрос. */
    val maxPageSize: Int,
    /** Лимит тегов без входа; `null` — без лимита. */
    val anonymousTagLimit: Int?,
    /** Средняя скорость запросов на долгих сессиях и допустимый всплеск. */
    val requestsPerSecond: Double = 1.0,
    val burst: Int = 5,
    val isCustom: Boolean = false,
) {
    fun baseUrlFor(mode: ContentMode): String =
        if (mode == ContentMode.SFW && safeBaseUrl != null) safeBaseUrl else baseUrl

    /** Все адреса сайта: для ограничителя запросов у зеркал общий бюджет. */
    val hosts: List<String>
        get() = listOfNotNull(baseUrl, safeBaseUrl).map { it.substringAfter("://").substringBefore('/') }
}
