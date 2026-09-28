package app.dudebooru.ui.viewer

import androidx.compose.runtime.staticCompositionLocalOf
import app.dudebooru.data.settings.ViewerPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Настройки → Просмотр для открытого поста. */
val LocalViewerPrefs = staticCompositionLocalOf { ViewerPrefs() }

/**
 * Клавиши громкости листают посты, пока открыт просмотр и это включено в настройках.
 * Нажатия ловит активность ([app.dudebooru.ui.MainActivity.dispatchKeyEvent]) — до системной громкости.
 */
object VolumeKeys {
    @Volatile
    var active: Boolean = false

    private val _steps = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** +1 — следующий пост, −1 — предыдущий. */
    val steps: SharedFlow<Int> = _steps

    fun press(step: Int) {
        _steps.tryEmit(step)
    }
}
