package app.dudebooru.booru.net

/** Маскирует ключи и хеши паролей в адресах и текстах перед записью в журнал. */
object Redactor {
    private val secretParams = Regex("""(?i)((?:api_key|password_hash|password|pass_hash|login)=)[^&\s"]+""")
    private val basicAuth = Regex("""(?i)(authorization:\s*basic\s+)\S+""")
    private val userInfo = Regex("""(https?://)[^/@\s]+@""")

    fun redact(text: String): String = text
        .replace(secretParams, "$1***")
        .replace(basicAuth, "$1***")
        .replace(userInfo, "$1***@")
}
