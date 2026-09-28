package app.dudebooru.ui.update

import android.content.Context
import app.dudebooru.AppContainer
import app.dudebooru.BuildConfig
import app.dudebooru.R
import app.dudebooru.booru.update.ReleaseInfo
import app.dudebooru.data.update.ForeignSignatureException
import app.dudebooru.data.update.ReleasesUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Что сейчас с обновлением: для «О приложении», пункта в меню и окна «Вышла версия». */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo, val hint: String? = null) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/** Проверка, скачивание и установка обновления из GitHub Releases. */
class UpdateController(private val context: Context, private val c: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Окно «Вышла версия X» открыто. */
    private val _dialog = MutableStateFlow(false)
    val dialog: StateFlow<Boolean> = _dialog.asStateFlow()

    private var downloaded: File? = null

    /** При запуске — тихо и не чаще раза в 12 часов: вышла версия — в меню появится пункт «Обновить». */
    fun checkOnStart() {
        if (!BuildConfig.UPDATE_CHECK) return
        scope.launch {
            if (!c.settings.notifyUpdates.first()) return@launch
            if (System.currentTimeMillis() - c.settings.lastUpdateCheck.first() < 12 * HOUR) return@launch
            val release = runCatching { c.updates.check() }.getOrNull() ?: return@launch
            c.settings.setLastUpdateCheck(System.currentTimeMillis())
            if (_state.value is UpdateState.Idle) _state.value = UpdateState.Available(release)
        }
    }

    /** «Проверить обновления» в «О приложении» или тап по уведомлению. */
    fun check(openDialog: Boolean = true) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        if (current is UpdateState.Available) {
            if (openDialog) _dialog.value = true
            return
        }
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val release = c.updates.check()
                c.settings.setLastUpdateCheck(System.currentTimeMillis())
                if (release == null) {
                    UpdateState.UpToDate
                } else {
                    if (openDialog) _dialog.value = true
                    UpdateState.Available(release)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReleasesUnavailableException) {
                UpdateState.Failed(context.getString(R.string.update_unavailable))
            } catch (e: Exception) {
                UpdateState.Failed(context.getString(R.string.update_check_failed, describe(e)))
            }
        }
    }

    fun openDialog() {
        if (_state.value is UpdateState.Available || _state.value is UpdateState.Downloading) _dialog.value = true else check()
    }

    fun closeDialog() {
        _dialog.value = false
    }

    /** «Скачать и установить»: без разрешения на установку сначала ведём в настройки системы. */
    fun install() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release ?: return
            else -> return
        }
        if (!c.updates.canInstall()) {
            _state.value = UpdateState.Available(release, hint = context.getString(R.string.update_allow_install))
            c.updates.openInstallPermission()
            return
        }
        val ready = downloaded?.takeIf { it.exists() && it.name.contains(release.version) }
        if (ready != null) {
            launchInstaller(release, ready)
            return
        }
        _state.value = UpdateState.Downloading(release, null)
        scope.launch {
            try {
                val file = c.updates.download(release) { progress -> _state.value = UpdateState.Downloading(release, progress) }
                downloaded = file
                launchInstaller(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForeignSignatureException) {
                _state.value = UpdateState.Failed(context.getString(R.string.update_foreign_signature), release)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(context.getString(R.string.update_download_failed, describe(e)), release)
            }
        }
    }

    private fun launchInstaller(release: ReleaseInfo, file: File) {
        _state.value = UpdateState.Available(release)
        runCatching { c.updates.install(file) }
            .onFailure { _state.value = UpdateState.Failed(context.getString(R.string.update_download_failed, describe(it)), release) }
    }

    private fun describe(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            context.getString(R.string.update_no_network)
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val HOUR = 3600_000L
    }
}
