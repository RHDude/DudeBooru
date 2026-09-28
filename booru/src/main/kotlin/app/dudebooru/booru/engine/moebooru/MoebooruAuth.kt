package app.dudebooru.booru.engine.moebooru

import java.security.MessageDigest

object MoebooruAuth {
    /**
     * Хеш пароля Moebooru: SHA1 от соли сайта, где `PASSWORD` заменён на пароль.
     * yande.re: `choujin-steiner--ПАРОЛЬ--`, Konachan: `So-I-Heard-You-Like-Mupkids-?--ПАРОЛЬ--`.
     */
    fun passwordHash(salt: String, password: String): String {
        require("PASSWORD" in salt) { "salt must contain PASSWORD placeholder" }
        val digest = MessageDigest.getInstance("SHA-1").digest(salt.replace("PASSWORD", password).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
