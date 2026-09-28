package app.dudebooru.ui.common

import android.content.Context
import androidx.annotation.StringRes
import app.dudebooru.R
import app.dudebooru.booru.model.ContentMode
import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.SortOrder
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.site.Sites
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ошибка сайта словами из раздела «Сайт недоступен». */
fun Context.errorText(error: Throwable): String {
    val site = (error as? BooruException)?.siteId?.let { id -> Sites.byId(id)?.name ?: id } ?: ""
    return when (error) {
        is BooruException.NotResponding -> if (error.cause is java.net.UnknownHostException) {
            getString(R.string.error_dns, site)
        } else {
            getString(R.string.error_not_responding, site)
        }
        is BooruException.ServerError -> getString(R.string.error_server, site, error.code)
        is BooruException.TooManyRequests -> getString(R.string.error_too_many, site, error.retryAfterSeconds.toInt())
        is BooruException.Forbidden -> getString(R.string.error_forbidden, site)
        is BooruException.Unauthorized -> getString(R.string.error_unauthorized, site)
        is BooruException.NotJson -> getString(R.string.error_not_json, site)
        is BooruException.TagLimitExceeded -> getString(R.string.error_tag_limit, site, error.limit)
        is BooruException.BadRequest -> getString(R.string.error_bad_request, site, error.code) +
            (error.serverMessage?.let { ": $it" } ?: "")
        is BooruException.Malformed -> getString(R.string.error_malformed, site)
        is BooruException.InvalidCredentials -> getString(R.string.account_bad_password)
        else -> getString(R.string.error_unknown, error.message ?: error.javaClass.simpleName)
    }
}

@StringRes
fun SortOrder.label(): Int = when (this) {
    SortOrder.NEW -> R.string.sort_new
    SortOrder.HOT -> R.string.sort_hot
    SortOrder.POPULAR_DAY -> R.string.sort_popular_day
    SortOrder.POPULAR_WEEK -> R.string.sort_popular_week
    SortOrder.POPULAR_MONTH -> R.string.sort_popular_month
    SortOrder.POPULAR_YEAR -> R.string.sort_popular_year
    SortOrder.BEST -> R.string.sort_best
    SortOrder.FAVCOUNT -> R.string.sort_favcount
    SortOrder.MPIXELS -> R.string.sort_mpixels
    SortOrder.LANDSCAPE -> R.string.sort_landscape
    SortOrder.PORTRAIT -> R.string.sort_portrait
    SortOrder.RANDOM -> R.string.sort_random
}

@StringRes
fun ContentMode.label(): Int = when (this) {
    ContentMode.SFW -> R.string.mode_sfw
    ContentMode.NSFW -> R.string.mode_nsfw
    ContentMode.ALL -> R.string.mode_all
}

/** «artist_a», «artist_a и ещё 1», «Неизвестный художник». */
fun Context.artistLabel(post: Post): String {
    val artists = post.tags.artist
    return when (artists.size) {
        0 -> getString(R.string.artist_unknown)
        1 -> artists[0]
        else -> getString(R.string.artist_and_more, artists[0], artists.size - 1)
    }
}

/** «сегодня в 22:32», «вчера в 13:05», «24 сентября в 22:32», «24 сентября 2024». */
fun Context.postDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    if (epochMillis <= 0) return ""
    // Месяцы — на языке интерфейса, а не системы: иначе «24 September в 22:32».
    val locale = Locale.forLanguageTag(getString(R.string.date_locale))
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val date = dateTime.toLocalDate()
    val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    return when {
        date == today -> getString(R.string.date_today, time)
        date == today.minusDays(1) -> getString(R.string.date_yesterday, time)
        date.year == today.year -> getString(R.string.date_this_year, dateTime.format(DateTimeFormatter.ofPattern("d MMMM", locale)), time)
        else -> dateTime.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }
}

/** Расстояние Левенштейна — для «может, тег пишется иначе?». */
fun editDistance(a: String, b: String): Int {
    val prev = IntArray(b.length + 1) { it }
    val cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        cur.copyInto(prev)
    }
    return prev[b.length]
}
