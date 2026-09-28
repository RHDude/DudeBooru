package app.dudebooru.ui.main

import androidx.annotation.StringRes

/** Экраны поверх главного. Стек живёт в [MainViewModel]. */
sealed interface Route {
    data object Main : Route
    data class Search(val siteId: String, val initial: String = "") : Route
    data class Results(val controllerId: String) : Route
    data class Viewer(val controllerId: String, val startKey: String) : Route
    data class Artist(val controllerId: String, val siteId: String, val name: String) : Route
    /** Настройки: без раздела — список разделов. */
    data class Settings(val page: SettingsPage? = null) : Route
    data object NegativeTags : Route
    data object Saved : Route
    data object Profile : Route
    data object History : Route
    data object Artists : Route
    data object Downloads : Route
    data object Recs : Route
    data object IconPicker : Route
    data object Themes : Route
    data object ThemeEditor : Route
    data object Game : Route
    data class Similar(val controllerId: String, val post: app.dudebooru.booru.model.Post) : Route
    data class Soon(@StringRes val title: Int, val step: Int) : Route
}

/** Разделы настроек в порядке списка. */
enum class SettingsPage { PROFILE, ACCOUNTS, FEED, CONTENT, LOOK, NOTIFICATIONS, DOWNLOADS, NETWORK, ABOUT }
