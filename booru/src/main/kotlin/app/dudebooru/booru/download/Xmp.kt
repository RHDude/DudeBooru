package app.dudebooru.booru.download

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * Теги в метаданные файла (XMP `dc:subject`), чтобы поиск в галерее находил картинки по тегам.
 * Вставка потоковая: файл копируется целиком, в память попадает только заголовок.
 */
object Xmp {
    fun packet(tags: List<String>, source: String?, title: String?): String {
        val items = tags.joinToString("") { "<rdf:li>${escape(it)}</rdf:li>" }
        val sourceTag = source?.let { "<dc:source>${escape(it)}</dc:source>" } ?: ""
        val titleTag = title?.let { "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">${escape(it)}</rdf:li></rdf:Alt></dc:title>" } ?: ""
        return "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            titleTag + sourceTag + "<dc:subject><rdf:Bag>$items</rdf:Bag></dc:subject>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"
    }

    /** Поддерживаются JPEG и PNG; остальное копируется как есть. Возвращает true, если теги записаны. */
    fun copyWithXmp(input: InputStream, output: OutputStream, ext: String, packet: String): Boolean = when (ext.lowercase()) {
        "jpg", "jpeg" -> jpeg(DataInputStream(input.buffered()), output, packet)
        "png" -> png(DataInputStream(input.buffered()), output, packet)
        else -> {
            input.copyTo(output)
            false
        }
    }

    private fun jpeg(input: DataInputStream, output: OutputStream, packet: String): Boolean {
        val soi = input.readUnsignedShort()
        if (soi != 0xFFD8) throw IOException("not a JPEG")
        output.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        // APP0 (JFIF) должен идти первым — XMP ставим сразу после него.
        input.mark(4)
        val marker = input.readUnsignedShort()
        if (marker == 0xFFE0) {
            val length = input.readUnsignedShort()
            val body = ByteArray(length - 2).also { input.readFully(it) }
            output.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), (length shr 8).toByte(), length.toByte()))
            output.write(body)
        } else {
            input.reset()
        }
        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        var data = packet.toByteArray(Charsets.UTF_8)
        // Сегмент JPEG не больше 64 КБ: слишком длинный список тегов не пишем, файл важнее.
        val written = namespace.size + data.size + 2 <= 0xFFFF
        if (written) {
            val length = namespace.size + data.size + 2
            output.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()))
            output.write(namespace)
            output.write(data)
        } else {
            data = ByteArray(0)
        }
        input.copyTo(output)
        return written
    }

    private fun png(input: DataInputStream, output: OutputStream, packet: String): Boolean {
        val signature = ByteArray(8).also { input.readFully(it) }
        if (!signature.contentEquals(PNG_SIGNATURE)) throw IOException("not a PNG")
        output.write(signature)
        // IHDR всегда первый; iTXt с XMP — сразу после него.
        val ihdrLength = input.readInt()
        val ihdr = ByteArray(4 + ihdrLength + 4).also { input.readFully(it) }
        output.write(int(ihdrLength))
        output.write(ihdr)

        val keyword = "XML:com.adobe.xmp".toByteArray(Charsets.ISO_8859_1)
        val text = packet.toByteArray(Charsets.UTF_8)
        // keyword, 0, compression flag 0, method 0, language "", 0, translated keyword "", 0, text
        val data = keyword + byteArrayOf(0, 0, 0, 0, 0) + text
        val type = "iTXt".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(type)
            update(data)
        }
        output.write(int(data.size))
        output.write(type)
        output.write(data)
        output.write(int(crc.value.toInt()))
        input.copyTo(output)
        return true
    }

    private fun int(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
