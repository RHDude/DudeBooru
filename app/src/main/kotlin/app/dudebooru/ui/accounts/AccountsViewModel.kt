package app.dudebooru.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dudebooru.AppContainer
import app.dudebooru.R
import app.dudebooru.booru.net.BooruException
import app.dudebooru.booru.site.EngineType
import app.dudebooru.booru.site.SiteConfig
import app.dudebooru.data.account.StoredAccount
import app.dudebooru.data.net.ProxyConfig
import app.dudebooru.data.settings.CensorPrefs
import app.dudebooru.data.settings.FeedPrefs
import app.dudebooru.ui.common.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginForm(
    val login: String = "",
    val secret: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

class AccountsViewModel(app: Application, private val c: AppContainer) : AndroidViewModel(app) {

    /** Одна карточка на группу аккаунта: Danbooru покрывает и Safebooru. */
    val groups: List<SiteConfig> = c.registry.sites.filter { it.supportsLogin }.distinctBy { it.accountGroup }

    val accounts: StateFlow<Map<String, StoredAccount>> = c.accounts.accounts

    private val _forms = MutableStateFlow<Map<String, LoginForm>>(emptyMap())
    val forms: StateFlow<Map<String, LoginForm>> = _forms.asStateFlow()

    val proxy: StateFlow<ProxyConfig> = c.settings.proxy.stateIn(viewModelScope, SharingStarted.Eagerly, ProxyConfig())

    val feedPrefs: StateFlow<FeedPrefs> = c.settings.feedPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, FeedPrefs())

    fun setFeedPrefs(prefs: FeedPrefs) {
        viewModelScope.launch { c.settings.setFeedPrefs(prefs) }
    }

    val censorEnabled: StateFlow<Boolean> = c.settings.censorEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val censorPrefs: StateFlow<CensorPrefs> = c.settings.censorPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, CensorPrefs())
    val showHiddenCount: StateFlow<Boolean> = c.settings.showHiddenCount.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setCensorEnabled(value: Boolean) {
        viewModelScope.launch { c.settings.setCensorEnabled(value) }
    }

    fun setCensorPrefs(value: CensorPrefs) {
        viewModelScope.launch { c.settings.setCensorPrefs(value) }
    }

    fun setShowHiddenCount(value: Boolean) {
        viewModelScope.launch { c.settings.setShowHiddenCount(value) }
    }

    fun sharedWith(site: SiteConfig): List<SiteConfig> = c.registry.sitesInGroup(site.accountGroup).filter { it.id != site.id }

    fun edit(site: SiteConfig, transform: (LoginForm) -> LoginForm) {
        _forms.update { it + (site.accountGroup to transform(it[site.accountGroup] ?: LoginForm())) }
    }

    fun login(site: SiteConfig) {
        val form = _forms.value[site.accountGroup] ?: LoginForm()
        val context = getApplication<Application>()
        if (form.login.isBlank() || form.secret.isBlank()) {
            val what = context.getString(if (site.engine == EngineType.DANBOORU) R.string.account_api_key else R.string.account_password)
            edit(site) { it.copy(error = context.getString(R.string.account_fill_fields, what.lowercase())) }
            return
        }
        edit(site) { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                c.accounts.login(site, form.login, form.secret)
                // Секрет не держим в памяти формы дольше, чем нужно.
                edit(site) { LoginForm() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BooruException.InvalidCredentials) {
                val message = if (site.engine == EngineType.DANBOORU) {
                    context.getString(R.string.account_bad_danbooru_key, site.name)
                } else {
                    context.getString(R.string.account_bad_password)
                }
                edit(site) { it.copy(busy = false, error = message) }
            } catch (e: Exception) {
                edit(site) { it.copy(busy = false, error = context.errorText(e)) }
            }
        }
    }

    fun logout(site: SiteConfig) {
        viewModelScope.launch { c.accounts.logout(site) }
    }

    fun saveProxy(config: ProxyConfig) {
        viewModelScope.launch { c.settings.setProxy(config) }
    }
}
