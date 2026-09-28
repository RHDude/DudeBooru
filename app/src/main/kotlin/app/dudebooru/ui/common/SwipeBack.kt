package app.dudebooru.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.dudebooru.ui.main.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * «Назад» свайпом, как в Telegram: экран тянется вправо за пальцем из любого места,
 * под ним — предыдущий, чуть сдвинутый и притемнённый. Отпустил дальше трети или быстро — экран уходит.
 *
 * Состояние одно на приложение: его читает и верхний экран (сдвиг), и слой под ним.
 */
@Stable
class SwipeBackState(private val scope: CoroutineScope) {
    /** Экран, который сейчас тянут; остальные не сдвигаются. */
    var route by mutableStateOf<Route?>(null)
        private set

    /** Экран под ним: виден, пока тянешь; когда экран ушёл, на его место встаёт настоящий. */
    var under by mutableStateOf<Route?>(null)
        private set

    /** Экран уходит свайпом — смена экранов без обычного перехода. */
    var popping by mutableStateOf(false)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    internal var width = 1f
    internal var flingVelocity = 1000f

    val progress: Float get() = (offset / width).coerceIn(0f, 1f)

    val dragging: Boolean get() = route != null && !popping && settle?.isActive != true

    private var settle: Job? = null

    internal fun start(top: Route, below: Route) {
        settle?.cancel()
        route = top
        under = below
        popping = false
    }

    internal fun dragBy(dx: Float) {
        offset = (offset + dx).coerceIn(0f, width)
    }

    internal fun release(velocity: Float, onBack: () -> Unit) {
        if (route == null) return
        val leave = velocity > flingVelocity || (velocity > -flingVelocity / 3 && progress > 0.35f)
        settle = scope.launch {
            val target = if (leave) width else 0f
            val duration = (220 * abs(target - offset) / width).toInt().coerceIn(90, 220)
            animate(offset, target, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
            if (leave) {
                // В одном кадре: слой снизу уходит, тот же экран встаёт на его место без перехода.
                // Две копии одного экрана разом не живут — у них общая прокрутка ленты.
                popping = true
                under = null
                onBack()
                // Уходящий экран ещё кадр-другой в AnimatedContent — пусть остаётся за краем.
                delay(120)
            }
            route = null
            under = null
            popping = false
            offset = 0f
        }
    }
}

/**
 * Экран, который можно закрыть свайпом вправо. Жест начинается там, где его не забрал никто внутри:
 * вертикальные списки отдают горизонтальное движение, а карусели и пейджеры — только когда листать
 * уже некуда (остаток приходит через nested scroll).
 */
@Composable
fun SwipeBackScreen(
    state: SwipeBackState,
    route: Route,
    below: Route?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enabled = below != null
    val currentBelow by rememberUpdatedState(below)
    val currentOnBack by rememberUpdatedState(onBack)
    val density = LocalDensity.current
    state.flingVelocity = with(density) { 700.dp.toPx() }
    val shadow = with(density) { 14.dp.toPx() }

    val connection = remember(state, route) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Уже тянем: горизонталь целиком наша, в том числе обратно влево.
                if (source != NestedScrollSource.UserInput || state.route != route || !state.dragging) return Offset.Zero
                state.dragBy(available.x)
                return Offset(available.x, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val next = currentBelow ?: return Offset.Zero
                if (source != NestedScrollSource.UserInput || available.x <= 0f || abs(available.x) < abs(available.y)) return Offset.Zero
                if (state.route != route) state.start(route, next)
                state.dragBy(available.x)
                return Offset(available.x, 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.route != route || !state.dragging) return Velocity.Zero
                state.release(available.x, currentOnBack)
                return available
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationX = if (state.route == route) state.offset else 0f }
            .drawBehind {
                // Тень у левого края уезжающего экрана.
                if (state.route == route && state.offset > 0f) {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f * (1f - state.progress))), startX = -shadow, endX = 0f),
                        topLeft = Offset(-shadow, 0f),
                        size = Size(shadow, size.height),
                    )
                }
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .nestedScroll(connection)
                        .pointerInput(state, route) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val slop = viewConfiguration.touchSlop
                                var dx = 0f
                                var dy = 0f
                                // Сначала решают дети: пейджер, ползунок или список забрали жест — он их.
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (!change.pressed || change.isConsumed || event.changes.size > 1) return@awaitEachGesture
                                    val delta = change.positionChange()
                                    dx += delta.x
                                    dy += delta.y
                                    if (abs(dy) > slop && abs(dy) >= abs(dx)) return@awaitEachGesture
                                    if (dx < -slop) return@awaitEachGesture
                                    if (dx > slop && dx > abs(dy) * 1.2f) {
                                        val next = currentBelow ?: return@awaitEachGesture
                                        change.consume()
                                        state.start(route, next)
                                        state.dragBy(dx - slop)
                                        break
                                    }
                                }
                                // Жест наш: события забираем раньше детей, чтобы список не поехал вбок-вниз.
                                val tracker = VelocityTracker()
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        state.release(0f, currentOnBack)
                                        break
                                    }
                                    tracker.addPointerInputChange(change)
                                    if (!change.pressed) {
                                        change.consume()
                                        state.release(tracker.calculateVelocity().x, currentOnBack)
                                        break
                                    }
                                    state.dragBy(change.positionChange().x)
                                    change.consume()
                                }
                            }
                        }
                },
            ),
    ) {
        content()
    }
}

/** Слой под уезжающим экраном: предыдущий экран чуть левее и в тени, догоняет палец. */
@Composable
fun SwipeBackUnder(state: SwipeBackState, content: @Composable (Route) -> Unit) {
    val under = state.under ?: return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -size.width * 0.28f * (1f - state.progress) }
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = 0.22f * (1f - state.progress)))
            },
    ) {
        content(under)
    }
}
