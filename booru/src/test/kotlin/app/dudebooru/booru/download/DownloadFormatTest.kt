package app.dudebooru.booru.download

import app.dudebooru.booru.model.Post
import app.dudebooru.booru.model.PostTags
import app.dudebooru.booru.model.Rating
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class DownloadFormatTest {
    private val post = Post(
        site = "danbooru", id = 123, md5 = "abc", createdAt = 0, rating = Rating.GENERAL, width = 10, height = 20,
        tags = PostTags(artist = listOf("art:ist?"), character = listOf("hatsune_miku"), general = listOf("1girl")),
    )

    @Test
    fun defaultTemplate() {
        assertEquals("Booru/danbooru/art_ist_/danbooru_123.png", NameTemplate.render(NameTemplate.DEFAULT, post, "png"))
    }

    @Test
    fun templateVariablesAndFallbacks() {
        val noArtist = post.copy(tags = PostTags())
        assertEquals("unknown_artist/abc", NameTemplate.render("{artist}/{md5}", noArtist, "jpg").removeSuffix(".jpg"))
        assertEquals("x/hatsune_miku_g.jpg", NameTemplate.render("x/{character}_{rating}.{ext}", post, "jpg"))
        assertEquals("danbooru_123.jpg", NameTemplate.render("///", post, "jpg"))
    }

    @Test
    fun jpegGetsApp1AfterApp0() {
        val jfif = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x11, 0x22)
        val rest = byteArrayOf(0xFF.toByte(), 0xDB.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
        val input = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + jfif + rest
        val out = ByteArrayOutputStream()
        assertTrue(Xmp.copyWithXmp(ByteArrayInputStream(input), out, "jpg", Xmp.packet(listOf("a&b", "c"), null, null)))
        val bytes = out.toByteArray()
        assertArrayEquals(input.copyOfRange(0, 8), bytes.copyOfRange(0, 8))
        assertEquals(0xFF.toByte(), bytes[8])
        assertEquals(0xE1.toByte(), bytes[9])
        val length = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        val segment = String(bytes, 12, length - 2, Charsets.UTF_8)
        assertTrue(segment.startsWith("http://ns.adobe.com/xap/1.0/"))
        assertTrue(segment.contains("<rdf:li>a&amp;b</rdf:li>"))
        assertArrayEquals(rest, bytes.copyOfRange(bytes.size - rest.size, bytes.size))
    }

    @Test
    fun pngGetsValidItxtAfterIhdr() {
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdr = byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + ByteArray(13) + ByteArray(4)
        val iend = byteArrayOf(0, 0, 0, 0) + "IEND".toByteArray() + ByteArray(4)
        val out = ByteArrayOutputStream()
        Xmp.copyWithXmp(ByteArrayInputStream(sig + ihdr + iend), out, "png", Xmp.packet(listOf("tag"), "https://x", "t"))
        val bytes = out.toByteArray()
        val start = 8 + ihdr.size
        val length = java.nio.ByteBuffer.wrap(bytes, start, 4).int
        assertEquals("iTXt", String(bytes, start + 4, 4))
        val data = bytes.copyOfRange(start + 8, start + 8 + length)
        val crc = CRC32().apply { update("iTXt".toByteArray()); update(data) }.value.toInt()
        assertEquals(crc, java.nio.ByteBuffer.wrap(bytes, start + 8 + length, 4).int)
        assertTrue(String(data, Charsets.UTF_8).startsWith("XML:com.adobe.xmp"))
        assertArrayEquals(iend, bytes.copyOfRange(bytes.size - iend.size, bytes.size))
    }
}
