package net.wault.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val WAULT_SHIELD_PATH =
    "M49.72,0.00L50.47,0.19L58.61,4.86L90.22,23.11L92.28,24.51L92.28,34.61L69.74,34.61L69.55,34.80" +
        "L69.55,41.44L73.76,41.53L73.76,41.81L65.25,58.75L65.06,58.75L56.55,41.72L56.55,41.53L62.82,41.53" +
        "L63.00,41.35L63.00,34.80L62.82,34.61L37.18,34.61L37.09,41.44L41.77,41.53L44.01,45.46L36.34,58.93" +
        "L26.89,41.81L26.89,41.53L29.79,41.44L29.79,34.80L29.61,34.61L7.53,34.61L7.53,24.60L7.72,24.23" +
        "L49.72,0.00Z" +
        "M7.62,41.53L12.11,41.53L12.30,41.72L30.92,71.84L38.96,71.75L48.78,53.51L49.25,53.98L59.92,71.84" +
        "L67.77,71.84L84.71,41.63L92.28,41.53L92.38,42.00L91.53,50.05L90.51,54.54L88.63,60.43L87.61,63.14" +
        "L84.89,68.66L81.34,74.09L76.19,80.26L73.20,83.26L68.52,87.37L60.66,93.26L54.30,97.38L50.56,99.63" +
        "L49.72,99.91L39.62,93.64L31.29,87.56L26.33,83.26L23.53,80.45L20.35,76.89L17.91,73.71L14.27,67.91" +
        "L11.74,62.49L10.43,58.84L8.47,50.70L7.81,46.21L7.62,41.53Z"

private const val STADE_SHIELD_PATH =
    "M49.86,0.00L92.29,24.49L92.20,42.06L91.36,49.16L50.14,25.33L39.58,31.31L88.74,59.91L87.15,64.30" +
        "L84.16,70.00L80.23,75.70L76.12,80.47L71.07,85.33L64.44,90.65L50.14,99.91L43.04,95.89L33.69,89.44" +
        "L27.06,83.93L21.07,77.76L16.59,71.78L13.32,65.98L10.42,58.60L8.83,51.68L50.33,75.70L54.25,72.99" +
        "L58.46,68.22L7.62,38.60L7.90,24.21L49.86,0.00Z"

private fun markOf(name: String, pathData: String, viewport: Float): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewport,
        viewportHeight = viewport
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

val WaultMark: ImageVector by lazy { markOf("WaultMark", WAULT_SHIELD_PATH, 100f) }

val StadeMark: ImageVector by lazy { markOf("StadeMark", STADE_SHIELD_PATH, 100f) }
