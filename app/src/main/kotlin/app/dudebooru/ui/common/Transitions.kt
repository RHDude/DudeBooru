package app.dudebooru.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Переход из ленты в пост: картинка карточки перелетает в просмотр. Без анимаций в системе — выключен. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalRouteScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Пост, который сейчас перелетает: открытый из ленты или показанный в просмотре.
 * Общий элемент есть только у него — у каждой карточки ленты он отнимал время на каждом кадре прокрутки.
 */
val LocalSharedKey = staticCompositionLocalOf<MutableState<String?>> { mutableStateOf(null) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPost(key: String, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val shared = LocalSharedScope.current ?: return this
    val route = LocalRouteScope.current ?: return this
    val holder = LocalSharedKey.current
    val active by remember(holder, key) { derivedStateOf { holder.value == key } }
    if (!active) return this
    return with(shared) {
        this@sharedPost.sharedElement(rememberSharedContentState("post:$key"), route)
    }
}
