package app.dudebooru.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dudebooru.R
import kotlin.random.Random

/**
 * Раннер в духе динозавра Chrome, только бежит Дуди. Тап — прыжок, долгий тап — прыжок выше.
 * Препятствия в тему приложения: теги-ценники, кнопки скачивания, «404». Счёт и рекорд.
 */
@Composable
fun RunnerGame(record: Int, onRecord: (Int) -> Unit, modifier: Modifier = Modifier) {
    val run1 = ImageBitmap.imageResource(R.drawable.dudi_run1)
    val run2 = ImageBitmap.imageResource(R.drawable.dudi_run2)
    val sad = ImageBitmap.imageResource(R.drawable.dudi_sad)
    val measurer = rememberTextMeasurer()
    val world = remember { RunnerWorld() }
    var tick by remember { mutableLongStateOf(0L) }
    var best by remember { mutableIntStateOf(record) }

    val colors = MaterialTheme.colorScheme
    val tapToStart = stringResource(R.string.game_tap_to_start)
    val gameOver = stringResource(R.string.game_over)
    val scoreLabel = stringResource(R.string.game_score)
    val recordLabel = stringResource(R.string.game_record)

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) world.step(((now - last) / 1e9f).coerceAtMost(0.05f))
                last = now
                tick = now
            }
            if (world.justDied) {
                world.justDied = false
                if (world.score > best) {
                    best = world.score
                    onRecord(best)
                }
            }
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(world) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    world.press()
                    waitForUpOrCancellation()
                    world.release()
                }
            },
    ) {
        tick // перерисовка каждый кадр
        world.resize(size.width / density, size.height / density)
        val u = density
        val groundY = world.groundY * u

        // Облака-пиксели, медленно.
        world.clouds.forEach { c ->
            drawRoundRect(colors.surfaceContainerHighest, Offset(c.x * u, c.y * u), Size(c.w * u, 10 * u), CornerRadius(5 * u))
        }
        // Земля с камушками.
        drawLine(colors.onSurfaceVariant, Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 2 * u)
        world.pebbles.forEach { p ->
            drawRect(colors.outline, Offset(p.x * u, groundY + p.y * u), Size(p.w * u, 2 * u))
        }

        world.obstacles.forEach { drawObstacle(it, u, groundY, measurer, colors.primary, colors.tertiary, colors.onPrimary, colors.error) }

        // Дуди.
        val sprite = when {
            world.state == RunnerWorld.State.OVER -> sad
            world.state == RunnerWorld.State.READY -> run1
            !world.onGround -> run2
            (world.distance / 22).toInt() % 2 == 0 -> run1
            else -> run2
        }
        val dudiH = RunnerWorld.DUDI_H * u
        val dudiW = dudiH * sprite.width / sprite.height
        drawImage(
            sprite,
            dstOffset = IntOffset((RunnerWorld.DUDI_X * u).toInt(), (groundY - dudiH - world.y * u).toInt()),
            dstSize = IntSize(dudiW.toInt(), dudiH.toInt()),
            filterQuality = FilterQuality.None,
        )

        // Счёт и рекорд.
        val hud = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        val scoreText = "$scoreLabel ${world.score.toString().padStart(5, '0')}   $recordLabel ${maxOf(best, world.score).toString().padStart(5, '0')}"
        val layout = measurer.measure(scoreText, hud)
        drawText(layout, topLeft = Offset(size.width - layout.size.width - 12 * u, 10 * u))

        val message = when (world.state) {
            RunnerWorld.State.READY -> tapToStart
            RunnerWorld.State.OVER -> gameOver
            RunnerWorld.State.RUNNING -> null
        }
        if (message != null) {
            val m = measurer.measure(message, TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            drawText(m, topLeft = Offset((size.width - m.size.width) / 2, groundY * 0.38f))
        }
    }
}

private fun DrawScope.drawObstacle(
    o: RunnerWorld.Obstacle,
    u: Float,
    groundY: Float,
    measurer: TextMeasurer,
    primary: Color,
    tertiary: Color,
    onPrimary: Color,
    error: Color,
) {
    val x = o.x * u
    val w = o.w * u
    val h = o.h * u
    val top = groundY - h
    when (o.kind) {
        RunnerWorld.Kind.TAG -> {
            // Тег-ценник: прямоугольник с остриём слева и дырочкой.
            val tip = h * 0.45f
            val path = Path().apply {
                moveTo(x, top + h / 2)
                lineTo(x + tip, top)
                lineTo(x + w, top)
                lineTo(x + w, top + h)
                lineTo(x + tip, top + h)
                close()
            }
            drawPath(path, tertiary)
            drawCircle(onPrimary, radius = 2.5f * u, center = Offset(x + tip * 0.8f, top + h / 2))
            val t = measurer.measure(o.label, TextStyle(color = onPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            drawText(t, topLeft = Offset(x + tip + 3 * u, top + (h - t.size.height) / 2))
        }
        RunnerWorld.Kind.TALL_TAG -> {
            drawRoundRect(tertiary, Offset(x, top), Size(w, h), CornerRadius(4 * u))
            drawCircle(onPrimary, radius = 2.5f * u, center = Offset(x + w / 2, top + 7 * u))
            translate(x + w / 2, top + h / 2) {
                val t = measurer.measure(o.label, TextStyle(color = onPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                // Надпись вдоль узкого тега.
                rotate(-90f, Offset.Zero) { drawText(t, topLeft = Offset(-t.size.width / 2f, -t.size.height / 2f)) }
            }
        }
        RunnerWorld.Kind.DOWNLOAD -> {
            val r = w / 2
            val c = Offset(x + r, groundY - r)
            drawCircle(primary, r, c)
            val s = Stroke(width = 2.5f * u, cap = StrokeCap.Round)
            val arrow = Path().apply {
                moveTo(c.x, c.y - r * 0.5f); lineTo(c.x, c.y + r * 0.25f)
                moveTo(c.x - r * 0.35f, c.y - r * 0.05f); lineTo(c.x, c.y + r * 0.3f); lineTo(c.x + r * 0.35f, c.y - r * 0.05f)
                moveTo(c.x - r * 0.45f, c.y + r * 0.55f); lineTo(c.x + r * 0.45f, c.y + r * 0.55f)
            }
            drawPath(arrow, onPrimary, style = s)
        }
        RunnerWorld.Kind.E404 -> {
            drawRoundRect(error.copy(alpha = 0.15f), Offset(x, top), Size(w, h), CornerRadius(6 * u))
            drawRoundRect(error, Offset(x, top), Size(w, h), CornerRadius(6 * u), style = Stroke(1.5f * u))
            val t = measurer.measure("404", TextStyle(color = error, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            drawText(t, topLeft = Offset(x + (w - t.size.width) / 2, top + (h - t.size.height) / 2))
        }
    }
}

/** Физика и препятствия отдельно от рисования; единицы — dp. */
internal class RunnerWorld {
    enum class State { READY, RUNNING, OVER }
    enum class Kind { TAG, TALL_TAG, DOWNLOAD, E404 }

    data class Obstacle(val kind: Kind, var x: Float, val w: Float, val h: Float, val label: String = "")
    data class Cloud(var x: Float, val y: Float, val w: Float)
    data class Pebble(var x: Float, val y: Float, val w: Float)

    companion object {
        const val DUDI_X = 28f
        const val DUDI_H = 44f
        private const val GRAVITY = 2600f
        private const val JUMP = 568f
        private const val HOLD_MS = 200f
        private val TAGS = listOf("1girl", "solo", "tagme", "long_hair", "smile", "highres", "absurdres", "blush", "commentary", "sky")
    }

    var state = State.READY
    var width = 360f
    var height = 220f
    val groundY get() = height - 28f
    var y = 0f
    private var vy = 0f
    private var holding = false
    private var holdTime = 0f
    val onGround get() = y <= 0f && vy <= 0f
    var distance = 0f
    private var speed = 300f
    private var nextGap = 300f
    var justDied = false
    val score get() = (distance / 12).toInt()
    val obstacles = ArrayList<Obstacle>()
    val clouds = arrayListOf(Cloud(60f, 30f, 46f), Cloud(220f, 54f, 30f), Cloud(320f, 24f, 38f))
    val pebbles = ArrayList<Pebble>()

    fun resize(w: Float, h: Float) {
        width = w
        height = h
        if (pebbles.isEmpty()) repeat(14) { pebbles += Pebble(Random.nextFloat() * w, 4f + Random.nextFloat() * 14f, 2f + Random.nextFloat() * 6f) }
    }

    fun press() {
        when (state) {
            State.READY -> start()
            State.OVER -> if (overFor > 0.4f) start()
            State.RUNNING -> if (onGround) {
                vy = JUMP
                holding = true
                holdTime = 0f
            }
        }
    }

    fun release() {
        holding = false
    }

    private var overFor = 0f

    private fun start() {
        state = State.RUNNING
        obstacles.clear()
        distance = 0f
        speed = 300f
        y = 0f
        vy = 0f
        nextGap = 260f
    }

    fun step(dt: Float) {
        clouds.forEach { c ->
            c.x -= dt * (if (state == State.RUNNING) 30f else 10f)
            if (c.x + c.w < 0) c.x = width + Random.nextFloat() * 80f
        }
        if (state == State.OVER) overFor += dt
        if (state != State.RUNNING) return
        // Долгий тап: пока держат в начале прыжка, тяжесть слабее — прыжок выше.
        val g = if (holding && holdTime * 1000 < HOLD_MS && vy > 0) GRAVITY * 0.6f else GRAVITY
        holdTime += dt
        vy -= g * dt
        y = (y + vy * dt).coerceAtLeast(0f)
        if (y == 0f && vy < 0) vy = 0f

        val dx = speed * dt
        distance += dx
        speed = (speed + dt * 9f).coerceAtMost(820f)
        pebbles.forEach { p ->
            p.x -= dx
            if (p.x + p.w < 0) p.x = width + Random.nextFloat() * 40f
        }
        obstacles.forEach { it.x -= dx }
        obstacles.removeAll { it.x + it.w < -20f }
        nextGap -= dx
        if (nextGap <= 0) spawn()

        // Столкновение: хитбокс Дуди чуть меньше спрайта.
        val dudiW = DUDI_H * 18f / 20f
        val me = Rect(DUDI_X + 6f, groundY - DUDI_H - y + 6f, DUDI_X + dudiW - 6f, groundY - y - 2f)
        val hit = obstacles.any { o ->
            val r = Rect(o.x + 3f, groundY - o.h + 3f, o.x + o.w - 3f, groundY)
            r.overlaps(me)
        }
        if (hit) {
            state = State.OVER
            overFor = 0f
            justDied = true
            holding = false
        }
    }

    private fun spawn() {
        val roll = Random.nextFloat()
        val o = when {
            roll < 0.4f -> {
                val label = TAGS.random()
                Obstacle(Kind.TAG, width + 10f, 22f + label.length * 6.2f, 22f, label)
            }
            roll < 0.6f -> Obstacle(Kind.TALL_TAG, width + 10f, 20f, 50f + Random.nextFloat() * 10f, TAGS.random())
            roll < 0.82f -> Obstacle(Kind.DOWNLOAD, width + 10f, 30f, 30f)
            else -> Obstacle(Kind.E404, width + 10f, 48f, 30f)
        }
        obstacles += o
        // Промежуток растёт со скоростью, чтобы игра оставалась проходимой.
        val min = speed * 0.62f + o.w
        nextGap = min + Random.nextFloat() * speed * 0.9f
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GameTopBar(onBack: () -> Unit) {
    androidx.compose.material3.TopAppBar(
        title = { androidx.compose.material3.Text(stringResource(R.string.game_title)) },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(app.dudebooru.ui.icons.DudeIcons.Back, stringResource(R.string.back))
            }
        },
    )
}

/** Пасхалка: игра без обрыва сети — семь тапов по версии в «О приложении». */
@Composable
fun GameScreen(vm: app.dudebooru.ui.main.MainViewModel, onBack: () -> Unit) {
    val record by vm.gameRecord.collectAsStateWithLifecycle()
    androidx.compose.material3.Scaffold(topBar = { GameTopBar(onBack) }) { padding ->
        androidx.compose.foundation.layout.Column(
            Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.material3.Text(
                stringResource(R.string.game_egg_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            RunnerGame(record, onRecord = vm::saveGameRecord)
        }
    }
}
