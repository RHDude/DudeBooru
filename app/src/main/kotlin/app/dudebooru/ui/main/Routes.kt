package app.dudebooru.ui.main

import androidx.annotation.StringRes

/** Экраны поверх главного. Стек живёт в [MainViewModel]. */
sealed interface Route {
    data object Main : Route
    data class Search(val siteId: String, val initial: String = "") : Route
    data class Results(val controllerId: String) : Route
    data class Viewer(val controllerId: String, val startKey: String) : Route
    data class Artist(val controllerId: String, val siteId: String, val name: String) : Route
    data object Settings : Route
    data object NegativeTags : Route
    data object Saved : Route
    data object Profile : Route
    data object History : Route
    data object Artists : Route
    data object Downloads : Route
    data object Recs : Route
    data class Similar(val controllerId: String, val post: app.dudebooru.booru.model.Post) : Route
    data class Soon(@StringRes val title: Int, val step: Int) : Route
}
