package app.dudebooru

import android.app.Application
import android.os.Build
import app.dudebooru.booru.net.BooruHttp
import app.dudebooru.data.SiteRegistry
import app.dudebooru.data.account.AccountRepository
import app.dudebooru.data.collections.CollectionsRepository
import app.dudebooru.data.collections.SubscriptionRepository
import app.dudebooru.data.db.AppDatabase
import app.dudebooru.data.downloads.DownloadRepository
import app.dudebooru.data.filter.NegativeTags
import app.dudebooru.data.net.DynamicDns
import app.dudebooru.data.net.DynamicProxySelector
import app.dudebooru.data.posts.ArtistAvatars
import app.dudebooru.data.posts.Downloader
import app.dudebooru.data.posts.PostRepository
import app.dudebooru.data.secure.SecretStore
import app.dudebooru.data.settings.SettingsRepository
import app.dudebooru.data.settings.settingsStore
import app.dudebooru.data.tags.TagDictionary
import app.dudebooru.util.DudeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ручная сборка зависимостей: без DI-фреймворков, чтобы сборка для F-Droid оставалась простой. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(app.settingsStore)

    private val proxySelector = DynamicProxySelector()

    /** Клиент без своего DNS — через него DoH-резолвер ходит к серверу DNS. */
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).proxySelector(proxySelector).build()
    }

    private val dns = DynamicDns { bootstrapClient }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .proxySelector(proxySelector)
        .dns(dns)
        .build()

    val userAgent = "DudeBooru/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; booru client)"

    val http = BooruHttp(baseClient, userAgent, logger = { DudeLog.d("http", it) })

    /** Для картинок: тот же прокси и User-Agent, но без ограничителя API. */
    val imageClient: OkHttpClient get() = http.client

    val db: AppDatabase = AppDatabase.build(app)

    private val secrets = SecretStore(app)

    val tags = TagDictionary(db.tags(), settings)

    val registry = SiteRegistry(http, tags).also { tags.attach(it) }

    val accounts = AccountRepository(settings, secrets, registry, scope)

    val negative = NegativeTags(db.negativeTags(), tags, registry, accounts, scope)

    val posts = PostRepository(registry, accounts, db, tags, negative)

    val avatars = ArtistAvatars(registry, accounts, db.artistAvatars(), negative)

    val downloader = Downloader(app) { imageClient }

    val collections = CollectionsRepository(app, db, registry, accounts, settings)

    val downloads = DownloadRepository(app, db, settings)

    val subscriptions = SubscriptionRepository(db.subscriptions(), registry, accounts, settings, negative)

    init {
        SubscriptionRepository.schedulePeriodic(app)
        scope.launch {
            settings.doh.distinctUntilChanged().collect { provider ->
                dns.use(provider)
                http.client.connectionPool.evictAll()
            }
        }
        scope.launch {
            settings.proxy.distinctUntilChanged().collect { config ->
                proxySelector.config = config
                // Соединения, открытые напрямую, не должны пережить включение прокси.
                http.client.connectionPool.evictAll()
            }
        }
    }
}
