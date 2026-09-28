package app.dudebooru.ui.theme

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Автоночной режим, как в Telegram: по расписанию или по закату и рассвету. */
object NightSchedule {
    /** Тёмная ли тема сейчас по расписанию [startMin]..[endMin] (минуты от полуночи, через полночь можно). */
    fun scheduleDark(now: LocalTime, startMin: Int, endMin: Int): Boolean {
        val m = now.hour * 60 + now.minute
        return if (startMin <= endMin) m in startMin until endMin else m >= startMin || m < endMin
    }

    /** Следующий момент смены по расписанию — ручное переключение действует до него. */
    fun nextScheduleSwitch(now: ZonedDateTime, startMin: Int, endMin: Int): Long =
        listOf(startMin, endMin).map { minute ->
            var t = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
            if (!t.isAfter(now)) t = t.plusDays(1)
            t.toInstant().toEpochMilli()
        }.min()

    /** Закат и рассвет в минутах от полуночи по приблизительным координатам часового пояса. */
    fun sunTimes(date: LocalDate, zone: ZoneId): Pair<Int, Int> {
        val (lat, lon) = approxCoordinates(zone)
        val offsetHours = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 3600.0
        val sunrise = solarEvent(date, lat, lon, offsetHours, rising = true) ?: (7 * 60)
        val sunset = solarEvent(date, lat, lon, offsetHours, rising = false) ?: (20 * 60)
        return sunrise to sunset
    }

    /** Упрощённый алгоритм NOAA: зенит 90.833°. Возвращает минуты от полуночи или null (полярный день/ночь). */
    private fun solarEvent(date: LocalDate, lat: Double, lon: Double, offsetHours: Double, rising: Boolean): Int? {
        val rad = PI / 180
        val n = date.dayOfYear
        val lngHour = lon / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(m * rad) + 0.020 * sin(2 * m * rad) + 282.634
        l = (l % 360 + 360) % 360
        var ra = Math.toDegrees(kotlin.math.atan(0.91764 * tan(l * rad)))
        ra = (ra % 360 + 360) % 360
        ra += (Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90)
        ra /= 15
        val sinDec = 0.39782 * sin(l * rad)
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(90.833 * rad) - sinDec * sin(lat * rad)) / (cosDec * cos(lat * rad))
        if (cosH > 1 || cosH < -1) return null
        var h = if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        h /= 15
        val localT = h + ra - 0.06571 * t - 6.622
        val ut = ((localT - lngHour) % 24 + 24) % 24
        val local = ((ut + offsetHours) % 24 + 24) % 24
        return (local * 60).toInt()
    }

    /** Без разрешения на геолокацию: широта по известным поясам, долгота — середина часового пояса. */
    private fun approxCoordinates(zone: ZoneId): Pair<Double, Double> {
        val known = mapOf(
            "Europe/Moscow" to (55.75 to 37.62), "Europe/Kaliningrad" to (54.71 to 20.51), "Europe/Samara" to (53.2 to 50.15),
            "Asia/Yekaterinburg" to (56.84 to 60.6), "Asia/Novosibirsk" to (55.03 to 82.92), "Asia/Krasnoyarsk" to (56.01 to 92.87),
            "Asia/Irkutsk" to (52.29 to 104.28), "Asia/Vladivostok" to (43.12 to 131.9), "Europe/Kiev" to (50.45 to 30.52),
            "Europe/Kyiv" to (50.45 to 30.52), "Europe/Minsk" to (53.9 to 27.56), "Asia/Almaty" to (43.24 to 76.89),
            "Europe/London" to (51.5 to -0.12), "Europe/Berlin" to (52.52 to 13.4), "Asia/Tokyo" to (35.68 to 139.69),
            "America/New_York" to (40.71 to -74.0), "America/Los_Angeles" to (34.05 to -118.24),
        )
        known[zone.id]?.let { return it }
        val offset = zone.rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
        return 50.0 to offset * 15
    }
}
