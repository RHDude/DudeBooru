package app.dudebooru.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.dudebooru.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал ошибок: при вылете отчёт пишется в файл на телефоне, «Отправить отчёт» делится им
 * через Telegram или почту. Никакой аналитики и отправки без спроса.
 */
object CrashLog {
    private const val MAX_BYTES = 64 * 1024

    private fun file(context: Context) = File(context.filesDir, "crash/crashes.txt")

    /** Ставится первым делом при запуске: дописывает отчёт и отдаёт вылет системе как обычно. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun append(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val report = buildString {
            append("=== ").append(stamp).append(" ===\n")
            append("DudeBooru ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), ")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append(trace).append('\n')
        }
        val target = file(context)
        target.parentFile?.mkdirs()
        val old = if (target.exists()) target.readText() else ""
        // Новые отчёты сверху; старые обрезаются, чтобы файл не рос бесконечно.
        target.writeText((report + old).take(MAX_BYTES))
    }

    /** Сколько вылетов записано. */
    fun count(context: Context): Int {
        val target = file(context)
        if (!target.exists()) return 0
        return target.readLines().count { it.startsWith("=== ") }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Отчёт как вложение: копия в общий кэш и системное окно «Поделиться». */
    fun shareIntent(context: Context): Intent? {
        val source = file(context)
        if (!source.exists()) return null
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val copy = File(dir, "dudebooru-crash-log.txt")
        source.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "DudeBooru ${BuildConfig.VERSION_NAME}: crash log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }
}
