package app.dudebooru.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dudebooru.ui.face.LaceEdge
import app.dudebooru.ui.icons.DudeIcons
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File

val ThemeBackground.active: Boolean get() = kind != ThemeBackground.Kind.NONE

/**
 * Фон ленты и меню: цвет, градиент или картинка с размытием и затемнением, как обои чатов в Telegram.
 * Затемнение — слой цвета фона темы: в тёмной теме темнит, в светлой высветляет, текст читается в обеих.
 */
@Composable
fun ThemeBackdrop(background: ThemeBackground, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    Box(modifier.background(base)) {
        when (background.kind) {
            ThemeBackground.Kind.NONE -> return@Box
            ThemeBackground.Kind.COLOR -> Box(Modifier.fillMaxSize().background(Color(background.color1)))
            ThemeBackground.Kind.GRADIENT -> Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(background.color1), Color(background.color2)))),
            )
            ThemeBackground.Kind.IMAGE -> {
                val path = background.imagePath ?: return@Box
                val context = LocalContext.current
                val blurDp = (background.blur * 28).dp
                // Размытие Compose есть с Android 12; на старых — маленькая копия, растянутая мягко.
                val native = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val request = ImageRequest.Builder(context).data(File(path)).apply {
                    if (!native && background.blur > 0.05f) size((360 * (1f - background.blur)).toInt().coerceAtLeast(24))
                }.build()
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (native && background.blur > 0.02f) Modifier.blur(blurDp) else Modifier),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(base.copy(alpha = background.dim)))
    }
}

/** Подпись макета: одна строка, плотно, как на настоящем экране в миниатюре. */
@Composable
private fun MiniText(
    text: String,
    size: Number,
    fontWeight: FontWeight? = null,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    Text(
        text,
        fontSize = size.toFloat().sp,
        lineHeight = (size.toFloat() * 1.2f).sp,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Макет главного экрана для предпросмотра темы: шапка, папки, пост. Цвета — из текущей темы. */
@Composable
fun MiniMainPreview(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val scheme = MaterialTheme.colorScheme
    val radius = theme.cornerRadius.dp
    Box(
        modifier
            .aspectRatio(9f / 17f)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.background),
    ) {
        ThemeBackdrop(theme.background, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DudeIcons.Menu, null, Modifier.size(12.dp), tint = scheme.onSurface)
                Text(
                    "Danbooru",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Icon(DudeIcons.Sort, null, Modifier.size(11.dp), tint = scheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Icon(DudeIcons.Search, null, Modifier.size(11.dp), tint = scheme.onSurface)
            }
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(scheme.secondaryContainer).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    MiniText("Danbooru", 6, color = scheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) { MiniText("Yande.re", 6, color = scheme.onSurfaceVariant) }
                Box(Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) { MiniText("Konachan", 6, color = scheme.onSurfaceVariant) }
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF7B7C9), Color(0xFF9B6FD6)))))
                Spacer(Modifier.width(5.dp))
                Column {
                    MiniText("artist_name", 6.5, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    MiniText(androidx.compose.ui.res.stringResource(app.dudebooru.R.string.preview_post_date), 5, color = scheme.onSurfaceVariant)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (theme.cornerRadius > 0) 5.dp else 0.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(radius / 2))
                    .background(Brush.linearGradient(listOf(Color(0xFFA8E0FF), Color(0xFF6FA8DC), Color(0xFF5B5FC7)))),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DudeIcons.Heart, null, Modifier.size(11.dp), tint = scheme.primary)
                Spacer(Modifier.weight(1f))
                Icon(DudeIcons.Share, null, Modifier.size(11.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Icon(DudeIcons.Save, null, Modifier.size(11.dp), tint = scheme.primary)
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB9E3A6), Color(0xFF3D9B8F)))))
                Spacer(Modifier.width(5.dp))
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurface.copy(alpha = 0.3f)))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (theme.pattern == ThemePattern.LACE) LaceEdge(Modifier.fillMaxWidth().height(8.dp).align(Alignment.TopCenter))
    }
}
