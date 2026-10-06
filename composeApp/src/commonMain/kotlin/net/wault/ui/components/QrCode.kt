package net.wault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.wault.qr.encodeQr
import kotlin.math.floor

@Composable
fun QrCode(
    payload: String,
    modifier: Modifier = Modifier,
    quietZoneModules: Int = 4
) {
    val matrix = remember(payload) { encodeQr(payload) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (matrix == null) {
            Text(
                text = payload.take(64),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black
            )
            return@Box
        }

        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val total = matrix.size + quietZoneModules * 2
            val module = floor(minOf(size.width, size.height) / total)
            if (module <= 0f) return@Canvas
            val rendered = module * total
            val originX = (size.width - rendered) / 2f + module * quietZoneModules
            val originY = (size.height - rendered) / 2f + module * quietZoneModules

            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (!matrix[x, y]) continue
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(originX + x * module, originY + y * module),
                        size = Size(module, module)
                    )
                }
            }
        }
    }
}
