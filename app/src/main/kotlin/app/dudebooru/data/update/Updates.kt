package app.dudebooru.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.dudebooru.BuildConfig
import app.dudebooru.DudeApp
import app.dudebooru.booru.net.await
import app.dudebooru.booru.update.ReleaseInfo
import app.dudebooru.booru.update.Releases
import app.dudebooru.notify.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Страница выпусков недоступна: репозиторий закрыт или выпусков ещё нет. */
class ReleasesUnavailableException : IOException("releases unavailable")

/** Скачанный APK подписан не тем ключом, что установленное приложение: система его не поставит. */
class ForeignSignatureException : IOException("signature mismatch")

/**
 * Обновления через GitHub Releases: проверка новой версии, скачивание APK и установка
 * системным установщиком. Без своих серверов и аналитики.
 */
class Updates(private val context: Context, private val client: () -> OkHttpClient) {

    /** Новая версия, если она вышла; null — стоит последняя. */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(Releases.latestUrl(REPO))
            .header("Accept", "application/vnd.github+json")
            .build()
        val release = client().newCall(request).await().use { response ->
            if (response.code == 404) throw ReleasesUnavailableException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            Releases.parse(response.body.string())
        }
        release?.takeIf { Releases.isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    /** APK в кэш приложения; [onProgress] — доля от 0 до 1 или null, если размер неизвестен. */
    suspend fun download(release: ReleaseInfo, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw IOException("no apk in release")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "DudeBooru-${release.version}.apk")
        client().newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else null)
                    }
                }
            }
        }
        if (!sameSigner(target)) {
            target.delete()
            throw ForeignSignatureException()
        }
        target
    }

    /** Android 8+: установка APK из приложения требует разрешения «Установка неизвестных приложений». */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Подпись скачанного APK совпадает с установленной — иначе система откажет с непонятной ошибкой.
     * Отладочная сборка — другое приложение (app.dudebooru.debug): там сравнивать не с чем.
     */
    private fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (archive.packageName != context.packageName) return true
        val theirs = archive.certificates()
        // Некоторые прошивки не читают подпись из архива — тогда решит сам установщик.
        if (theirs.isEmpty()) return true
        val mine = runCatching { pm.getPackageInfo(context.packageName, flags).certificates() }.getOrDefault(emptySet())
        return theirs == mine
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.certificates(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        } else {
            signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        }

    /** Раз в сутки: вышла новая версия — уведомление (одно на версию). */
    class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as DudeApp).container
            if (!BuildConfig.UPDATE_CHECK || !c.settings.notifyUpdates.first()) return Result.success()
            val release = runCatching { c.updates.check() }.getOrNull() ?: return Result.success()
            c.settings.setLastUpdateCheck(System.currentTimeMillis())
            if (c.settings.notifiedVersion.first() == release.version) return Result.success()
            Notifications.showUpdate(applicationContext, release)
            c.settings.setNotifiedVersion(release.version)
            return Result.success()
        }
    }

    companion object {
        const val REPO = "RHDude/DudeBooru"

        fun schedulePeriodic(context: Context) {
            if (!BuildConfig.UPDATE_CHECK) return
            val request = PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
