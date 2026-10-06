package net.wault.ui

import java.io.File
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

private const val VIEWPORT = 108.0
private const val SAFE_RADIUS = 36.0
private const val MASKED_VIEWPORT = 72.0
private const val MIN_FILL = 0.62
private const val MAX_FILL = 0.80

private data class IconGeometry(
    val maxRadius: Double,
    val width: Double,
    val height: Double,
    val centerX: Double,
    val centerY: Double
)

class LauncherIconTest {

    private fun drawable(name: String): File {
        val candidates = listOf(
            File("src/androidMain/res/drawable/$name.xml"),
            File("composeApp/src/androidMain/res/drawable/$name.xml")
        )
        return candidates.firstOrNull { it.isFile }
            ?: fail("could not find $name.xml from ${File(".").absolutePath}")
    }

    private fun geometryOf(name: String): IconGeometry {
        val text = drawable(name).readText()

        val scale = Regex("""android:scaleX="([\d.]+)"""").find(text)?.groupValues?.get(1)?.toDouble()
            ?: fail("$name has no scaleX")
        val translate = Regex("""android:translateX="([\d.]+)"""").find(text)?.groupValues?.get(1)?.toDouble()
            ?: fail("$name has no translateX")

        val points = Regex("""android:pathData="([^"]+)"""").findAll(text).flatMap { match ->
            match.groupValues[1]
                .replace("M", "L")
                .removeSuffix("Z")
                .split("L")
                .mapNotNull { segment ->
                    val parts = segment.trim().split(",")
                    if (parts.size != 2) return@mapNotNull null
                    val x = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                    val y = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                    (x * scale + translate) to (y * scale + translate)
                }
        }.toList()

        assertTrue(points.size > 20, "$name should have a real path, found ${points.size} points")

        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val centerX = (minX + maxX) / 2
        val centerY = (minY + maxY) / 2

        return IconGeometry(
            maxRadius = points.maxOf { hypot(it.first - centerX, it.second - centerY) },
            width = maxX - minX,
            height = maxY - minY,
            centerX = centerX,
            centerY = centerY
        )
    }

    @Test
    fun `the foreground survives any launcher mask`() {
        val geometry = geometryOf("ic_launcher_foreground")
        assertTrue(
            geometry.maxRadius <= SAFE_RADIUS,
            "foreground reaches ${geometry.maxRadius} from its centre; anything over $SAFE_RADIUS " +
                "is clipped by a circular mask"
        )
    }

    @Test
    fun `the monochrome layer survives any launcher mask`() {
        val geometry = geometryOf("ic_launcher_monochrome")
        assertTrue(
            geometry.maxRadius <= SAFE_RADIUS,
            "monochrome reaches ${geometry.maxRadius} from its centre; anything over $SAFE_RADIUS " +
                "is clipped by a circular mask"
        )
    }

    @Test
    fun `both layers sit in the middle of the canvas`() {
        listOf("ic_launcher_foreground", "ic_launcher_monochrome").forEach { name ->
            val geometry = geometryOf(name)
            val offX = geometry.centerX - VIEWPORT / 2
            val offY = geometry.centerY - VIEWPORT / 2
            assertTrue(
                kotlin.math.abs(offX) < 0.5 && kotlin.math.abs(offY) < 0.5,
                "$name is off-centre by ($offX, $offY)"
            )
        }
    }

    @Test
    fun `neither layer crowds the masked viewport`() {
        listOf("ic_launcher_foreground", "ic_launcher_monochrome").forEach { name ->
            val fill = geometryOf(name).height / MASKED_VIEWPORT
            assertTrue(
                fill in MIN_FILL..MAX_FILL,
                "$name fills ${"%.0f".format(fill * 100)}% of the 72dp masked viewport. " +
                    "Only that inner 72dp is ever visible, so anything near 100% looks cramped " +
                    "even though it is not technically clipped. Stade sits at 71%."
            )
        }
    }
}
