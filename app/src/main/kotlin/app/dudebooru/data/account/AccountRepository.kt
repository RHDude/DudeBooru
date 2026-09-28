package app.dudebooru.data.account

import app.dudebooru.booru.engine.Session
import app.dudebooru.booru.model.AccountInfo
import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.net.BooruJson
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.secure.SecretStore
import app.dudebooru.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.ConcurrentHashMap

/** Аккаунт без секрета. Секрет (API-ключ или хеш пароля) — в [SecretStore]. */
@Serializable
data class StoredAccount(
    /** Группа аккаунта: Danbooru и Safebooru делят одну. */
    val group: String,
    val kind: Kind,
    val info: AccountInfo,
    val verifiedAt: Long,
    /** Сайт ответил 401 — вход больше не действует, лента работает анонимно до повторного входа. */
    val invalid: Boolean = false,
) {
    @Serializable
    enum class Kind { API_KEY, PASSWORD_HASH }
}

class AccountRepository(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val registry: SiteRegistry,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val credentialCache = ConcurrentHashMap<String, Credentials>()

    val accounts: StateFlow<Map<String, StoredAccount>> = settings.accountsJson
        .map { decode(it).associateBy { account -> account.group } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** От чьего имени ходить на сайт и какой у аккаунта лимит тегов. */
    suspend fun session(site: SiteConfig): Session {
        val account = current(site.accountGroup) ?: return Session()
        if (account.invalid) return Session()
        val credentials = credentials(account) ?: return Session()
        return Session(credentials, account.info.tagLimit)
    }

    /** Проверочный запрос и сохранение. Для Moebooru пароль сразу превращается в хеш. */
    suspend fun login(site: SiteConfig, login: String, secret: String): AccountInfo {
        val engine = registry.engine(site)
        val credentials = engine.credentials(login, secret)
        val info = engine.verify(credentials)
        val kind = when (credentials) {
            is Credentials.ApiKey -> StoredAccount.Kind.API_KEY
            is Credentials.PasswordHash -> StoredAccount.Kind.PASSWORD_HASH
        }
        val secretValue = when (credentials) {
            is Credentials.ApiKey -> credentials.apiKey
            is Credentials.PasswordHash -> credentials.passwordHash
        }
        mutex.withLock {
            withContext(Dispatchers.IO) { secrets.put(secretKey(site.accountGroup), secretValue) }
            credentialCache[site.accountGroup] = credentials
            val updated = currentAll().filter { it.group != site.accountGroup } +
                StoredAccount(site.accountGroup, kind, info.copy(login = info.login.ifBlank { credentials.login }), System.currentTimeMillis())
            save(updated)
        }
        return info
    }

    suspend fun logout(site: SiteConfig) = mutex.withLock {
        withContext(Dispatchers.IO) { secrets.put(secretKey(site.accountGroup), null) }
        credentialCache.remove(site.accountGroup)
        save(currentAll().filter { it.group != site.accountGroup })
    }

    /** Сайт ответил 401 на запрос с ключом: переходим на анонимный режим, логин помним для «Войти заново». */
    suspend fun markInvalid(site: SiteConfig) = mutex.withLock {
        credentialCache.remove(site.accountGroup)
        save(currentAll().map { if (it.group == site.accountGroup) it.copy(invalid = true) else it })
    }

    private suspend fun current(group: String): StoredAccount? =
        accounts.value[group] ?: currentAll().firstOrNull { it.group == group }

    private suspend fun currentAll(): List<StoredAccount> = decode(settings.accountsJson.first())

    private suspend fun credentials(account: StoredAccount): Credentials? {
        credentialCache[account.group]?.let { return it }
        val secret = withContext(Dispatchers.IO) { secrets.get(secretKey(account.group)) } ?: return null
        val credentials = when (account.kind) {
            StoredAccount.Kind.API_KEY -> Credentials.ApiKey(account.info.login, secret)
            StoredAccount.Kind.PASSWORD_HASH -> Credentials.PasswordHash(account.info.login, secret)
        }
        credentialCache[account.group] = credentials
        return credentials
    }

    private suspend fun save(list: List<StoredAccount>) {
        settings.setAccountsJson(BooruJson.encodeToString(LIST, list))
    }

    private fun decode(json: String?): List<StoredAccount> =
        json?.let { runCatching { BooruJson.decodeFromString(LIST, it) }.getOrNull() }.orEmpty()

    private fun secretKey(group: String) = "account.$group"

    private companion object {
        val LIST = ListSerializer(StoredAccount.serializer())
    }
}
