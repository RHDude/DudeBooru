@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.face

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.ui.icons.DudeIcons

/**
 * Иконка из двух слоёв (фон и передний план) — видимая область 72 из 108dp, как в лаунчере.
 * Пиксели масштабируются без сглаживания.
 */
@Composable
fun IconPreview(icon: AppIcon, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(28)) {
    val bg = ImageBitmap.imageResource(icon.bg)
    val fg = ImageBitmap.imageResource(icon.fg)
    Canvas(modifier.clip(shape)) {
        val full = bg.width
        val inset = full * 18 / 108
        val src = IntSize(full - inset * 2, full - inset * 2)
        val dst = IntSize(size.width.toInt(), size.height.toInt())
        for (layer in listOf(bg, fg)) {
            drawImage(layer, srcOffset = IntOffset(inset, inset), srcSize = src, dstSize = dst, filterQuality = FilterQuality.None)
        }
    }
}

/** Выбор иконки как в exteraGram и AyuGram: крупное превью сверху, сетка по 4, «Выбрать». */
@Composable
fun IconPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val current = remember { IconManager.current(context) }
    var selected by remember { mutableStateOf(current) }
    var set by remember { mutableStateOf(current.set) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.radialGradient(listOf(selected.tint.copy(alpha = 0.9f), MaterialTheme.colorScheme.background)))
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp)) {
                Icon(DudeIcons.Back, stringResource(R.string.back))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
                IconPreview(selected, Modifier.size(112.dp))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(selected.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(AppIcon.entries.filter { it.set == set }, key = { it.name }) { icon ->
                val isSelected = icon == selected
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .clickable { selected = icon }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        IconPreview(icon, Modifier.size(56.dp))
                        if (icon == current) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.BottomEnd).size(18.dp),
                            ) {
                                Icon(DudeIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(icon.label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SingleChoiceSegmentedButtonRow {
                listOf(IconSet.DUDI to R.string.icon_set_dudi, IconSet.SIGNS to R.string.icon_set_signs).forEachIndexed { i, (value, label) ->
                    SegmentedButton(selected = set == value, onClick = { set = value }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(stringResource(label))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (selected != current) {
                        IconManager.apply(context, selected)
                        Toast.makeText(context, context.getString(R.string.icon_applied), Toast.LENGTH_LONG).show()
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (selected == current) R.string.icon_keep else R.string.icon_choose))
            }
        }
    }
}

/** Дуди в приложении: спрайт без сглаживания. */
@Composable
fun Dudi(emotion: DudiEmotion, modifier: Modifier = Modifier) {
    val bitmap = ImageBitmap.imageResource(emotion.res)
    Canvas(modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
        drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
    }
}

enum class DudiEmotion(val res: Int) {
    NORMAL(R.drawable.dudi_normal),
    SLEEPY(R.drawable.dudi_sleepy),
    SAD(R.drawable.dudi_sad),
    BORED(R.drawable.dudi_bored),
    WAVE(R.drawable.dudi_wave),
    RUN1(R.drawable.dudi_run1),
    RUN2(R.drawable.dudi_run2),
}
