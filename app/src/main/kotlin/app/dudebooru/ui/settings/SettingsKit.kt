@file:OptIn(ExperimentalMaterial3Api::class)

package app.dudebooru.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dudebooru.R
import app.dudebooru.ui.icons.DudeIcons

/** Плашка значка: цвет акцента темы и тёмный знак на нём, как в exteraGram и AyuGram. */
@Composable
fun SettingsTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.6f))
    }
}

/** Страница раздела: заголовок по центру, «назад» в круглой кнопке, список уходит под шапку с затуханием. */
@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    SettingsFrame(title, onBack, actions) { padding -> SettingsList(padding, content) }
}

/**
 * Каркас экрана настроек как в exteraGram: без плотной шапки — поверх содержимого только кнопки
 * и название, а под ними фон плавно гаснет в прозрачность.
 */
@Composable
fun SettingsFrame(
    title: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    body: @Composable (PaddingValues) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val bars = WindowInsets.systemBars.asPaddingValues()
    val top = bars.calculateTopPadding()
    Box(Modifier.fillMaxSize().background(background)) {
        body(PaddingValues(top = top + HeaderHeight, bottom = bars.calculateBottomPadding()))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to background, 0.72f to background.copy(alpha = 0.92f), 1f to background.copy(alpha = 0f)))
                .padding(top = top)
                .height(HeaderHeight + 18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(DudeIcons.Back, stringResource(R.string.back), onBack)
                Spacer(Modifier.weight(1f))
                actions()
            }
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = 72.dp).wrapContentHeight(),
                )
            }
        }
    }
}

private val HeaderHeight = 64.dp

/** Круглая кнопка шапки на подложке: видна и когда под ней проезжает список. */
@Composable
fun RoundIconButton(icon: ImageVector, description: String?, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(24.dp)) }
    }
}

/** Список групп с отступами по краям; клавиатура поджимает его, а не перекрывает поля. */
@Composable
fun SettingsList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        content = content,
    )
}

/** Строки группы собираются заранее: каждой нужна своя форма — крупные углы у крайних, мелкие между. */
class SettingsGroupScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

private val OuterCorner = 26.dp
private val InnerCorner = 6.dp

fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Группа как в Android 16 и exteraGram: у каждой строки своя подложка с зазором в 2 dp,
 * группа скруглена крупно, строки внутри — чуть-чуть. Подпись сверху, пояснение снизу.
 */
@Composable
fun SettingsGroup(title: String? = null, footer: String? = null, content: SettingsGroupScope.() -> Unit) {
    val items = SettingsGroupScope().apply(content).items
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                key(index) {
                    Surface(
                        shape = segmentShape(index, items.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) { item() }
                }
            }
        }
        if (footer != null) {
            SettingsFooter(footer, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SettingsFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

/**
 * Строка: плашка со значком, название, пояснение и значение справа цветом акцента («Русский», «12»).
 * С [onClick] — переход или действие.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            icon != null -> {
                SettingsTile(icon)
                Spacer(Modifier.width(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Переключатель во всю строку: тап по тексту тоже переключает. */
@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsTile(icon)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Внутренние отступы для полей, кнопок и ползунков внутри строки группы. */
@Composable
fun SettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
