package app.dudebooru.util

import android.util.Log
import app.dudebooru.BuildConfig
import app.dudebooru.booru.net.Redactor

/** Журнал только в отладочной сборке и всегда без ключей и хешей. */
object DudeLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d("Dude/$tag", Redactor.redact(message))
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w("Dude/$tag", Redactor.redact(message))
    }
}
