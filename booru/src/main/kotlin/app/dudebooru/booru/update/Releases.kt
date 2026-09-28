package app.dudebooru.booru.update

import app.dudebooru.booru.net.BooruJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Выпуск приложения на GitHub Releases: версия, что нового и APK. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
)

/**
 * Проверка обновлений через GitHub Releases API (`/repos/{repo}/releases/latest`):
 * черновики и предварительные выпуски latest не отдаёт, APK берётся из вложений.
 */
object Releases {
    fun latestUrl(repo: String): String = "https://api.github.com/repos/$repo/releases/latest"

    fun parse(json: String): ReleaseInfo? = runCatching {
        val dto = BooruJson.decodeFromString(ReleaseDto.serializer(), json)
        if (dto.draft || dto.prerelease) return null
        val apk = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ReleaseInfo(
            version = dto.tagName.trim().removePrefix("v").removePrefix("V"),
            notes = dto.body.orEmpty().trim(),
            pageUrl = dto.htmlUrl,
            apkUrl = apk?.url,
            apkSize = apk?.size ?: 0,
        )
    }.getOrNull()

    /**
     * Новее ли [candidate], чем [current]: сравнение по числам «1.10.0» > «1.9.2»;
     * хвосты вроде «-debug» и «-beta» не учитываются, у одинаковых чисел обновления нет.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').substringBefore('+').split('.')
            .map { part -> part.takeWhile { it.isDigit() } }
            .takeWhile { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
    )
}
