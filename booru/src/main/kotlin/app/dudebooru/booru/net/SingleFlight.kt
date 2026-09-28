package app.dudebooru.booru.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Склеивает одинаковые запросы в полёте: пока первый идёт, остальные ждут его результат.
 * Работа выполняется в собственном [scope], поэтому отмена одного из ждущих её не обрывает.
 */
class SingleFlight<K : Any, V>(private val scope: CoroutineScope) {
    private val inFlight = ConcurrentHashMap<K, Deferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val deferred = inFlight.computeIfAbsent(key) { k ->
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    inFlight.remove(k)
                }
            }
        }
        return deferred.await()
    }

    val size: Int get() = inFlight.size
}
