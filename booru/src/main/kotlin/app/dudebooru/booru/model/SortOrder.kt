package app.dudebooru.booru.model

import kotlinx.serialization.Serializable

/** Общий список сортировок для всех источников. Что источник не умеет — в листе не показывается. */
@Serializable
enum class SortOrder {
    NEW,
    HOT,
    POPULAR_DAY,
    POPULAR_WEEK,
    POPULAR_MONTH,
    POPULAR_YEAR,
    BEST,
    FAVCOUNT,
    MPIXELS,
    LANDSCAPE,
    PORTRAIT,
    RANDOM;

    val isPopular: Boolean
        get() = this == POPULAR_DAY || this == POPULAR_WEEK || this == POPULAR_MONTH || this == POPULAR_YEAR
}
