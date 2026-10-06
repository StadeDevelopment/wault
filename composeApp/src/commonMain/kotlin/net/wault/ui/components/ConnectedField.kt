package net.wault.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val OUTER = 28.dp
private val INNER = 4.dp

object ConnectedField {
    val leading = RoundedCornerShape(
        topStart = OUTER,
        bottomStart = OUTER,
        topEnd = INNER,
        bottomEnd = INNER
    )

    val trailing = RoundedCornerShape(
        topStart = INNER,
        bottomStart = INNER,
        topEnd = OUTER,
        bottomEnd = OUTER
    )

    val standalone = RoundedCornerShape(OUTER)
}
