package app.dudebooru.booru.model

import kotlinx.serialization.Serializable

/** Данные для входа. [toString] никогда не выводит секрет. */
sealed interface Credentials {
    val login: String

    /** Danbooru: логин + API-ключ из профиля. */
    class ApiKey(override val login: String, val apiKey: String) : Credentials {
        override fun toString() = "ApiKey(login=$login, apiKey=***)"
        override fun equals(other: Any?) = other is ApiKey && other.login == login && other.apiKey == apiKey
        override fun hashCode() = 31 * login.hashCode() + apiKey.hashCode()
    }

    /** Moebooru: логин + SHA1-хеш пароля с солью сайта. Пароль в открытом виде не хранится. */
    class PasswordHash(override val login: String, val passwordHash: String) : Credentials {
        override fun toString() = "PasswordHash(login=$login, passwordHash=***)"
        override fun equals(other: Any?) = other is PasswordHash && other.login == login && other.passwordHash == passwordHash
        override fun hashCode() = 31 * login.hashCode() + passwordHash.hashCode()
    }
}

/** Что сайт сообщил об аккаунте после проверочного запроса. */
@Serializable
data class AccountInfo(
    val login: String,
    val userId: Long? = null,
    val level: Int? = null,
    val levelName: String? = null,
    /** Сколько тегов можно в одном запросе; `null` — без лимита. */
    val tagLimit: Int? = null,
    val blacklistedTags: List<String> = emptyList(),
    val favoriteCount: Int? = null,
)
