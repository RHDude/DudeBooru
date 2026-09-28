package app.dudebooru.booru.engine.moebooru

import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.site.Sites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoebooruAuthTest {
    @Test
    fun yandereSalt() {
        assertEquals(
            "1fc0adf8544b5cb927ac1895f8e67c042e6e8dba",
            MoebooruAuth.passwordHash(Sites.YANDERE.passwordSalt!!, "hunter2"),
        )
    }

    @Test
    fun konachanSalt() {
        assertEquals(
            "80a114d2973833fb8d6c18ef9274b4b9597b4a2a",
            MoebooruAuth.passwordHash(Sites.KONACHAN.passwordSalt!!, "hunter2"),
        )
    }

    @Test
    fun utf8Password() {
        assertEquals(
            "50ca0d81c3d720c747cc61259749c69c39f5b13c",
            MoebooruAuth.passwordHash(Sites.YANDERE.passwordSalt!!, "пароль"),
        )
    }

    @Test
    fun credentialsNeverPrintSecrets() {
        val creds = Credentials.PasswordHash("dude", "1fc0adf8544b5cb927ac1895f8e67c042e6e8dba")
        assertFalse(creds.toString().contains("1fc0adf8"))
        assertFalse(Credentials.ApiKey("dude", "secretkey123").toString().contains("secretkey123"))
    }

    @Test
    fun tagSummaryParsing() {
        val summary = MoebooruTagSummary.parse(
            7,
            "3`ef_~a_fairytale_of_the_two~`/ef`ef` 0`wallpaper`wallpapers` 1`ssong2`ssong2ne` 4`tsuruya` ",
        )
        assertEquals(4, summary.entries.size)
        assertEquals(listOf("ef"), summary.entries[0].aliases)
        assertEquals(app.dudebooru.booru.model.TagCategory.ARTIST, summary.entries[2].category)
        assertEquals(emptyList<String>(), summary.entries[3].aliases)
    }
}
