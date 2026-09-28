package app.dudebooru.booru.site

object Sites {
    val DANBOORU = SiteConfig(
        id = "danbooru",
        name = "Danbooru",
        engine = EngineType.DANBOORU,
        baseUrl = "https://danbooru.donmai.us",
        accountGroup = "donmai",
        maxPageSize = 200,
        anonymousTagLimit = 2,
        requestsPerSecond = 1.0,
        burst = 6,
        approxTotalPosts = 9_500_000,
    )

    val SAFEBOORU = SiteConfig(
        id = "safebooru",
        name = "Safebooru",
        engine = EngineType.DANBOORU,
        baseUrl = "https://safebooru.donmai.us",
        safeOnly = true,
        accountGroup = "donmai",
        maxPageSize = 200,
        anonymousTagLimit = 2,
        requestsPerSecond = 1.0,
        burst = 6,
        approxTotalPosts = 9_500_000,
    )

    val YANDERE = SiteConfig(
        id = "yandere",
        name = "Yande.re",
        engine = EngineType.MOEBOORU,
        baseUrl = "https://yande.re",
        passwordSalt = "choujin-steiner--PASSWORD--",
        maxPageSize = 100,
        anonymousTagLimit = 6,
        requestsPerSecond = 1.0,
        burst = 4,
        approxTotalPosts = 1_270_000,
    )

    val KONACHAN = SiteConfig(
        id = "konachan",
        name = "Konachan",
        engine = EngineType.MOEBOORU,
        baseUrl = "https://konachan.com",
        safeBaseUrl = "https://konachan.net",
        passwordSalt = "So-I-Heard-You-Like-Mupkids-?--PASSWORD--",
        maxPageSize = 100,
        anonymousTagLimit = 6,
        requestsPerSecond = 1.0,
        burst = 4,
        approxTotalPosts = 410_000,
    )

    val SAKUGABOORU = SiteConfig(
        id = "sakugabooru",
        name = "Sakugabooru",
        engine = EngineType.MOEBOORU,
        baseUrl = "https://www.sakugabooru.com",
        supportsLogin = false,
        maxPageSize = 100,
        anonymousTagLimit = 6,
        requestsPerSecond = 1.0,
        burst = 4,
    )

    /** Порядок папок по умолчанию. Sakugabooru выключен, пока его не включат в настройках. */
    val builtIn: List<SiteConfig> = listOf(DANBOORU, YANDERE, KONACHAN, SAFEBOORU, SAKUGABOORU)

    fun byId(id: String): SiteConfig? = builtIn.firstOrNull { it.id == id }
}
