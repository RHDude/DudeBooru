package app.dudebooru.booru.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ведро токенов на один сайт: [burst] запросов подряд, дальше [permitsPerSecond] в среднем.
 * После 429 весь сайт ставится на паузу через [pauseFor], чтобы не добивать его повторами.
 */
class RateLimiter(
    private val permitsPerSecond: Double,
    private val burst: Int,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var tokens = burst.toDouble()
    private var lastRefill = nanoTime()
    private var pausedUntil = Long.MIN_VALUE

    init {
        require(permitsPerSecond > 0) { "permitsPerSecond must be positive" }
        require(burst >= 1) { "burst must be at least 1" }
    }

    /**
     * @param reserve фоновые запросы (аватарки, догрузка словаря) ждут, пока в ведре останется
     *   запас [reserve] токенов, — лента всегда получает свои запросы первой.
     */
    suspend fun acquire(reserve: Int = 0) {
        while (true) {
            val waitNanos = mutex.withLock { tryTake(reserve.coerceIn(0, burst - 1)) }
            if (waitNanos <= 0) return
            delay((waitNanos + 999_999) / 1_000_000)
        }
    }

    /** Сколько наносекунд ждать; 0 — токен выдан. Вызывается под мьютексом. */
    private fun tryTake(reserve: Int): Long {
        val now = nanoTime()
        if (pausedUntil != Long.MIN_VALUE && now - pausedUntil < 0) return pausedUntil - now
        val elapsed = (now - lastRefill).coerceAtLeast(0)
        tokens = (tokens + elapsed / 1e9 * permitsPerSecond).coerceAtMost(burst.toDouble())
        lastRefill = now
        val need = 1.0 + reserve
        if (tokens >= need) {
            tokens -= 1.0
            return 0
        }
        return ((need - tokens) / permitsPerSecond * 1e9).toLong().coerceAtLeast(1)
    }

    suspend fun pauseFor(millis: Long) {
        mutex.withLock {
            val until = nanoTime() + millis * 1_000_000
            if (pausedUntil == Long.MIN_VALUE || until - pausedUntil > 0) pausedUntil = until
            tokens = 0.0
        }
    }
}
