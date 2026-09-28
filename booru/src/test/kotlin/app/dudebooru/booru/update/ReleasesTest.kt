package app.dudebooru.booru.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test
    fun comparesVersionsByNumbers() {
        assertTrue(Releases.isNewer("0.9.1", "0.9.0"))
        assertTrue(Releases.isNewer("1.10.0", "1.9.2"))
        assertTrue(Releases.isNewer("v1.0", "0.9.9"))
        assertTrue(Releases.isNewer("1.0.1", "1.0"))
        assertFalse(Releases.isNewer("0.9.0", "0.9.0"))
        assertFalse(Releases.isNewer("0.9.0", "0.9.0-debug"))
        assertFalse(Releases.isNewer("0.8.9", "0.9.0"))
        assertFalse(Releases.isNewer("garbage", "0.9.0"))
    }

    @Test
    fun parsesLatestRelease() {
        val json = """
            {
              "tag_name": "v0.9.0",
              "name": "DudeBooru 0.9.0",
              "body": "## Что нового\n- Колокольчик\n",
              "html_url": "https://github.com/RHDude/DudeBooru/releases/tag/v0.9.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "checksums.txt", "browser_download_url": "https://example.org/sums", "size": 10},
                {"name": "DudeBooru-0.9.0.apk", "browser_download_url": "https://example.org/app.apk", "size": 12345678, "extra": 1}
              ]
            }
        """.trimIndent()
        val release = Releases.parse(json)!!
        assertEquals("0.9.0", release.version)
        assertEquals("## Что нового\n- Колокольчик", release.notes)
        assertEquals("https://example.org/app.apk", release.apkUrl)
        assertEquals(12345678L, release.apkSize)
    }

    @Test
    fun skipsDraftsAndBrokenJson() {
        assertNull(Releases.parse("""{"tag_name": "v1.0", "draft": true}"""))
        assertNull(Releases.parse("""{"message": "Not Found"}"""))
        assertNull(Releases.parse("not json"))
    }
}
