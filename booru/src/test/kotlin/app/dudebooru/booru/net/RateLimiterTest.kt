package app.dudebooru.booru.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {
    @Test
    fun burstThenSteadyRate() = runTest {
        val limiter = RateLimiter(permitsPerSecond = 2.0, burst = 3, nanoTime = { testScheduler.currentTime * 1_000_000 })
        repeat(3) { limiter.acquire() }
        assertEquals(0, currentTime)
        limiter.acquire()
        assertEquals(500, currentTime)
        limiter.acquire()
        assertEquals(1000, currentTime)
    }

    @Test
    fun pauseBlocksEveryone() = runTest {
        val limiter = RateLimiter(permitsPerSecond = 10.0, burst = 10, nanoTime = { testScheduler.currentTime * 1_000_000 })
        limiter.pauseFor(3_000)
        limiter.acquire()
        assertTrue("waited $currentTime ms", currentTime >= 3_000)
    }
}
