package app.dudebooru.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Иконки приложения — те же штриховые знаки, что в макете ТЗ (24×24, линия 2, скруглённые концы).
 * Своё небольшое семейство вместо тяжёлой библиотеки иконок.
 */
object DudeIcons {
    val Menu = stroke("Menu", "M4,7h16M4,12h16M4,17h16")
    val Search = stroke("Search", circle(11f, 11f, 6.5f) + "M20,20l-4.2,-4.2")
    val Dots = fill("Dots", circle(12f, 5f, 1.6f) + circle(12f, 12f, 1.6f) + circle(12f, 19f, 1.6f))
    val Back = stroke("Back", "M19,12H5M11,6l-6,6 6,6")
    val Heart = stroke("Heart", HEART)
    val HeartFilled = fill("HeartFilled", HEART)
    val Share = stroke("Share", "M14,5l6,6 -6,6M20,11h-8a7,7 0 0,0 -7,7")
    val Save = stroke("Save", SAVE)
    val SaveFilled = fill("SaveFilled", SAVE)
    val Download = stroke("Download", "M12,4v11M7,10l5,5 5,-5M5,20h14")
    val Spark = stroke("Spark", "M12,3l2,5.5L19.5,10 14,12l-2,6 -2,-6 -5.5,-2L10,8.5z")
    val Gear = stroke("Gear", circle(12f, 12f, 3f) + RAYS)
    val User = stroke("User", circle(12f, 8f, 4f) + "M4,20c1.5,-4 4.5,-5.5 8,-5.5s6.5,1.5 8,5.5")
    val Users = stroke("Users", circle(9f, 8f, 3.5f) + "M2.5,20c1.2,-3.5 3.8,-5 6.5,-5s5.3,1.5 6.5,5" + circle(17f, 7.5f, 2.5f) + "M16.5,12.5c2.4,0 4.3,1.3 5,4")
    val Off = stroke("Off", circle(12f, 12f, 8f) + "M6.5,6.5l11,11")
    val Sun = stroke("Sun", circle(12f, 12f, 3.5f) + RAYS)
    val Moon = stroke("Moon", "M20,14.5A8,8 0 0,1 9.5,4 8,8 0 1,0 20,14.5z")
    val ChevronDown = stroke("ChevronDown", "M6,9l6,6 6,-6")
    val ChevronUp = stroke("ChevronUp", "M6,15l6,-6 6,6")
    val Fire = stroke("Fire", "M12,21c4,0 6,-2.7 6,-6 0,-4 -3,-6 -4,-10 -2,2 -3,4 -3,6 -1,-1 -2,-2 -2,-3 -2,2 -3,4.5 -3,7 0,3.3 2,6 6,6z")
    val Tag = stroke("Tag", "M3,12V4h8l10,10 -8,8z" + circle(7.5f, 8.5f, 1.3f))
    val Sort = stroke("Sort", "M4,7h11M4,12h8M4,17h5M18,5v14M15,16l3,3 3,-3")
    val Copy = stroke("Copy", "M10,8h8a2,2 0 0,1 2,2v8a2,2 0 0,1 -2,2h-8a2,2 0 0,1 -2,-2v-8a2,2 0 0,1 2,-2zM16,8V5a1,1 0 0,0 -1,-1H5a1,1 0 0,0 -1,1v10a1,1 0 0,0 1,1h3")
    val Image = stroke("Image", "M7,4h10a3,3 0 0,1 3,3v10a3,3 0 0,1 -3,3H7a3,3 0 0,1 -3,-3V7a3,3 0 0,1 3,-3z" + circle(9f, 9f, 1.5f) + "M20,15l-5,-5 -9,9")
    val Out = stroke("Out", "M7,17L17,7M9,7h8v8")
    val Hide = stroke("Hide", EYE + "M4,20L20,4")
    val Eye = stroke("Eye", EYE + circle(12f, 12f, 2.5f))
    val Close = stroke("Close", "M6,6l12,12M18,6L6,18")
    val Check = stroke("Check", "M5,12.5l4.5,4.5L19,7.5")
    val Plus = stroke("Plus", "M12,5v14M5,12h14")
    val History = stroke("History", "M4,12a8,8 0 1,0 2.3,-5.6M4,4v4h4M12,8v4l3,2")
    val Clock = stroke("Clock", circle(12f, 12f, 8.5f) + "M12,7.5V12l3,2")
    val Trending = stroke("Trending", "M3,17l6,-6 4,4 8,-8M15,7h6v6")
    val Star = stroke("Star", "M12,3.5l2.6,5.3 5.9,0.9 -4.3,4.1 1,5.8 -5.2,-2.8 -5.2,2.8 1,-5.8 -4.3,-4.1 5.9,-0.9z")
    val Shuffle = stroke("Shuffle", "M4,7h3.5c2,0 3.5,1 4.5,3l1.5,3c1,2 2.5,3 4.5,3H20M17,13l3,3 -3,3M4,17h3.5c1.3,0 2.3,-0.4 3.1,-1.2M13.4,8.2c0.8,-0.8 1.8,-1.2 3.1,-1.2H20M17,4l3,3 -3,3")
    val Expand = stroke("Expand", "M4,9V4h5M20,9V4h-5M4,15v5h5M20,15v5h-5")
    val Landscape = stroke("Landscape", "M5,7h14a2,2 0 0,1 2,2v6a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2V9a2,2 0 0,1 2,-2z")
    val Portrait = stroke("Portrait", "M9,3h6a2,2 0 0,1 2,2v14a2,2 0 0,1 -2,2H9a2,2 0 0,1 -2,-2V5a2,2 0 0,1 2,-2z")
    val Link = stroke("Link", "M10,14a4,4 0 0,0 5.7,0l3,-3a4,4 0 0,0 -5.7,-5.7l-1,1M14,10a4,4 0 0,0 -5.7,0l-3,3a4,4 0 0,0 5.7,5.7l1,-1")
    val Palette = stroke("Palette", "M12,3a9,9 0 1,0 0,18c1.1,0 1.8,-0.9 1.8,-1.8 0,-1.2 -1,-1.6 -1,-2.7 0,-1 0.8,-1.5 1.8,-1.5H17a4,4 0 0,0 4,-4c0,-4.4 -4,-8 -9,-8z" + circle(7.5f, 11f, 1f) + circle(10f, 7f, 1f) + circle(15f, 7.5f, 1f))
    val Refresh = stroke("Refresh", "M20,11a8,8 0 1,0 -2.3,5.7M20,5v6h-6")

    /** Колокольчик с «волнами» — уведомления включены. */
    val BellRing = stroke("BellRing", BELL + "M3,9.5a9,9 0 0,1 2.4,-5M21,9.5a9,9 0 0,0 -2.4,-5")
    val BellOff = stroke("BellOff", BELL + "M4,4L20,20")

    // Разделы настроек.
    val Key = stroke("Key", circle(7.5f, 12f, 4f) + "M11.5,12H21v3.5M17.5,12v2.5")
    val Shield = stroke("Shield", "M12,3l7.5,3v5.5c0,4.6 -3.2,8.3 -7.5,9.5 -4.3,-1.2 -7.5,-4.9 -7.5,-9.5V6zM8.8,12l2.2,2.2 4.2,-4.4")
    val Feed = stroke("Feed", "M6,3h12a2,2 0 0,1 2,2v7a2,2 0 0,1 -2,2H6a2,2 0 0,1 -2,-2V5a2,2 0 0,1 2,-2zM4,18h16M4,21h10")
    val Globe = stroke("Globe", circle(12f, 12f, 9f) + "M3,12h18M12,3a14,14 0 0,1 0,18a14,14 0 0,1 0,-18z")
    val Info = stroke("Info", circle(12f, 12f, 9f) + "M12,11v5.5" + circle(12f, 7.7f, 0.3f))
    val Translate = stroke("Translate", "M3.5,6h9M8,4v2M10.5,6c-0.8,3.8 -3.1,6.7 -6.5,8.5M5.8,9.6c1.1,1.9 2.8,3.4 4.9,4.4M12.5,21l4,-9 4,9M14,17.6h5")
    val Folder = stroke("Folder", "M3.5,7.5a2,2 0 0,1 2,-2h3.8l2,2.2h7.2a2,2 0 0,1 2,2v7.8a2,2 0 0,1 -2,2h-13a2,2 0 0,1 -2,-2z")
    val ChevronRight = stroke("ChevronRight", "M9.5,6l6,6 -6,6")

    // ---------------------------------------------------------------------------------------

    private const val BELL = "M6,16v-5a6,6 0 0,1 12,0v5l1.5,2h-15zM10,20.5a2,2 0 0,0 4,0"

    private const val HEART = "M12,20s-7,-4.4 -7,-10a4,4 0 0,1 7,-2.6A4,4 0 0,1 19,10c0,5.6 -7,10 -7,10z"
    private const val SAVE = "M6,4h12v16l-6,-4 -6,4z"
    private const val EYE = "M3,12s3.5,-6 9,-6 9,6 9,6 -3.5,6 -9,6 -9,-6 -9,-6z"
    private const val RAYS = "M12,3v2M12,19v2M3,12h2M19,12h2M5.6,5.6l1.4,1.4M17,17l1.4,1.4M5.6,18.4L7,17M17,7l1.4,-1.4"

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

    private fun stroke(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    private fun fill(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
