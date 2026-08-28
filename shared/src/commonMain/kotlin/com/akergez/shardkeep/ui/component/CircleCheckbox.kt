package com.akergez.shardkeep.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Круглый чекбокс списка: 20dp метка внутри 48dp зоны нажатия. */
@Composable
fun CircleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val fill by animateColorAsState(if (checked) primary else Color.Transparent)
    val stroke by animateColorAsState(if (checked) primary else outline)
    val checkmark by animateColorAsState(if (checked) Color.White else Color.Transparent)

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val r = size.minDimension / 2f
            drawCircle(color = fill, radius = r)
            drawCircle(color = stroke, radius = r, style = Stroke(width = 2.dp.toPx()))
            val path = Path().apply {
                moveTo(size.width * 0.20f, size.height * 0.50f)
                lineTo(size.width * 0.43f, size.height * 0.72f)
                lineTo(size.width * 0.80f, size.height * 0.28f)
            }
            drawPath(
                path = path,
                color = checkmark,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
