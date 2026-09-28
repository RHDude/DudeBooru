package app.dudebooru.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Переход из ленты в пост: картинка карточки перелетает в просмотр. Без анимаций в системе — выключен. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalRouteScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPost(key: String, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val shared = LocalSharedScope.current ?: return this
    val route = LocalRouteScope.current ?: return this
    return with(shared) {
        this@sharedPost.sharedElement(rememberSharedContentState("post:$key"), route)
    }
}
