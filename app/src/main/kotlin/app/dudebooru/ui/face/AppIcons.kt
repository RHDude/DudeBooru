package app.dudebooru.ui.face

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.dudebooru.R

/** Набор на экране выбора: с персонажем или знаки без него. */
enum class IconSet { DUDI, SIGNS }

/**
 * Иконки приложения. Каждая — отдельный activity-alias в манифесте; включён ровно один.
 * [tint] — цвет фона превью на экране выбора.
 */
enum class AppIcon(val alias: String, @StringRes val label: Int, val set: IconSet, val bg: Int, val fg: Int, val tint: Color) {
    DUDI("Dudi", R.string.icon_dudi, IconSet.DUDI, R.mipmap.ic_dudi_bg, R.mipmap.ic_dudi_fg, Color(0xFFB8D4FF)),
    MONET("Monet", R.string.icon_monet, IconSet.DUDI, R.mipmap.ic_monet_bg, R.mipmap.ic_monet_fg, Color(0xFF2E3140)),
    NIGHT("Night", R.string.icon_night, IconSet.DUDI, R.mipmap.ic_night_bg, R.mipmap.ic_night_fg, Color(0xFF14131F)),
    SAKURA("Sakura", R.string.icon_sakura, IconSet.DUDI, R.mipmap.ic_sakura_bg, R.mipmap.ic_sakura_fg, Color(0xFFFFD9E4)),
    SLEEPY("Sleepy", R.string.icon_sleepy, IconSet.DUDI, R.mipmap.ic_sleepy_bg, R.mipmap.ic_sleepy_fg, Color(0xFF2A2757)),
    RETRO("Retro", R.string.icon_retro, IconSet.DUDI, R.mipmap.ic_retro_bg, R.mipmap.ic_retro_fg, Color(0xFFDDF5C8)),
    OCEAN("Ocean", R.string.icon_ocean, IconSet.DUDI, R.mipmap.ic_ocean_bg, R.mipmap.ic_ocean_fg, Color(0xFF2A6FC8)),
    CYBER("Cyber", R.string.icon_cyber, IconSet.DUDI, R.mipmap.ic_cyber_bg, R.mipmap.ic_cyber_fg, Color(0xFF120F22)),
    NEON("Neon", R.string.icon_neon, IconSet.SIGNS, R.mipmap.ic_neon_bg, R.mipmap.ic_neon_fg, Color(0xFF0B0B14)),
    TAG("Tag", R.string.icon_tag, IconSet.SIGNS, R.mipmap.ic_tag_bg, R.mipmap.ic_tag_fg, Color(0xFF5B6CF0)),
    MINIMAL("Minimal", R.string.icon_minimal, IconSet.SIGNS, R.mipmap.ic_minimal_bg, R.mipmap.ic_minimal_fg, Color(0xFFF2F1FA)),
    NEUTRAL("Neutral", R.string.icon_neutral, IconSet.SIGNS, R.mipmap.ic_neutral_bg, R.mipmap.ic_neutral_fg, Color(0xFFE6E7EC));

    /** Класс alias'а — от namespace, а пакет — applicationId (у отладочной сборки с суффиксом). */
    fun component(context: Context) = ComponentName(context.packageName, "app.dudebooru.icon.$alias")
}

object IconManager {
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(icon.component(context))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DUDI
                else -> false
            }
        } ?: AppIcon.DUDI
    }

    /** Сначала включаем новую, потом выключаем остальные — иначе на миг иконки нет совсем. */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
