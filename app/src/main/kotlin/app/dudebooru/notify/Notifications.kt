package app.dudebooru.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dudebooru.R
import app.dudebooru.booru.update.ReleaseInfo
import app.dudebooru.data.collections.NewWorks
import app.dudebooru.ui.MainActivity

/**
 * Системные уведомления, кроме загрузок: новые работы у подписок (с учётом колокольчика
 * на странице художника) и новая версия приложения. Картинок в уведомлениях нет: на экране блокировки
 * не должно мелькнуть откровенное.
 */
object Notifications {
    private const val CHANNEL_ARTISTS = "artists"
    private const val CHANNEL_UPDATES = "updates"
    private const val GROUP_ARTISTS = "app.dudebooru.ARTISTS"
    private const val ID_ARTISTS_SUMMARY = 100
    private const val ID_UPDATE = 101

    const val ACTION_OPEN_ARTIST = "app.dudebooru.action.OPEN_ARTIST"
    const val ACTION_OPEN_ARTISTS = "app.dudebooru.action.OPEN_ARTISTS"
    const val ACTION_SHOW_UPDATE = "app.dudebooru.action.SHOW_UPDATE"
    const val EXTRA_SITE = "app.dudebooru.extra.SITE"
    const val EXTRA_ARTIST = "app.dudebooru.extra.ARTIST"

    /** Каналы создаются заново при каждом вызове: так их названия следуют за языком интерфейса. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ARTISTS, context.getString(R.string.notify_channel_artists), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notify_channel_artists_hint)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.notify_channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Разрешение на уведомления (Android 13+) и общий переключатель в системе. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** По уведомлению на художника; если их несколько — ещё и сводка «новое у N художников». */
    fun showNewWorks(context: Context, works: List<NewWorks>) {
        if (works.isEmpty() || !allowed(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ARTISTS)
            .setSmallIcon(R.drawable.ic_stat_dude)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notify_artists_public))
            .build()
        for (item in works) {
            val name = item.artist.replace('_', ' ')
            val notification = NotificationCompat.Builder(context, CHANNEL_ARTISTS)
                .setSmallIcon(R.drawable.ic_stat_dude)
                .setContentTitle(name)
                .setContentText(context.resources.getQuantityString(R.plurals.notify_new_works, item.count, item.count, item.site.name))
                .setContentIntent(
                    pending(
                        context,
                        requestCode = "${item.site.id}:${item.artist}".hashCode(),
                        intent = open(context, ACTION_OPEN_ARTIST)
                            .putExtra(EXTRA_SITE, item.site.id)
                            .putExtra(EXTRA_ARTIST, item.artist),
                    ),
                )
                .setAutoCancel(true)
                .setGroup(GROUP_ARTISTS)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build()
            manager.notify(tag(item), 0, notification)
        }
        if (works.size > 1) {
            val style = NotificationCompat.InboxStyle()
            works.take(6).forEach { item ->
                style.addLine(
                    item.artist.replace('_', ' ') + " — " +
                        context.resources.getQuantityString(R.plurals.notify_new_works, item.count, item.count, item.site.name),
                )
            }
            val summary = NotificationCompat.Builder(context, CHANNEL_ARTISTS)
                .setSmallIcon(R.drawable.ic_stat_dude)
                .setContentTitle(context.getString(R.string.notify_artists_title))
                .setContentText(context.resources.getQuantityString(R.plurals.notify_artists_summary, works.size, works.size))
                .setStyle(style)
                .setContentIntent(pending(context, ID_ARTISTS_SUMMARY, open(context, ACTION_OPEN_ARTISTS)))
                .setAutoCancel(true)
                .setGroup(GROUP_ARTISTS)
                .setGroupSummary(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build()
            manager.notify(ID_ARTISTS_SUMMARY, summary)
        }
    }

    /** «Вышла DudeBooru 1.0 — что нового»; тап открывает окно обновления в приложении. */
    fun showUpdate(context: Context, release: ReleaseInfo) {
        if (!allowed(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notes = plainNotes(release.notes)
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_dude)
            .setContentTitle(context.getString(R.string.update_available_title, release.version))
            .setContentText(notes.lineSequence().firstOrNull { it.isNotBlank() } ?: context.getString(R.string.update_notify_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes.ifBlank { context.getString(R.string.update_notify_text) }))
            .setContentIntent(pending(context, ID_UPDATE, open(context, ACTION_SHOW_UPDATE)))
            .setAutoCancel(true)
            .build()
        manager.notify(ID_UPDATE, notification)
    }

    /** Заметки к выпуску пишутся в Markdown; в уведомлении и окне — простым текстом. */
    fun plainNotes(markdown: String): String = markdown.lines()
        .map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#") -> trimmed.trimStart('#').trim()
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• " + trimmed.drop(2)
                else -> trimmed
            }.replace("**", "").replace("`", "")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun tag(item: NewWorks) = "artist:${item.site.id}:${item.artist}"

    private fun open(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun pending(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
