package com.akergez.shardkeep.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Подпись пункта нижней панели и рельса. На пункт приходится узкая колонка, и с крупным
 * системным шрифтом подписи переносились по слову в строку и прижимались влево. Поэтому
 * подпись всегда в одну строку и по центру, а растёт с системным шрифтом не дальше
 * обычного размера: рядом есть иконка, а одинаковый размер у всех пунктов читается
 * лучше, чем подписи, ужатые каждая по-своему (так делает автоподбор размера).
 * Шрифт — labelSmall без разрядки: иначе «Все задачи» и «Настройки» не влезают
 * в колонку телефона шириной в 360dp и при обычном шрифте.
 */
@Composable
fun NavigationLabel(text: String) {
    val style = MaterialTheme.typography.labelSmall
    val fontScale = LocalDensity.current.fontScale
    Text(
        text,
        fontSize = capped(style.fontSize, fontScale),
        lineHeight = capped(style.lineHeight, fontScale),
        // Разрядка у labelSmall съедает ширину, которой в колонке и так нет.
        letterSpacing = 0.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

private const val MAX_LABEL_FONT_SCALE = 1f

/** Величина в sp, которая после умножения на [fontScale] даёт не больше [MAX_LABEL_FONT_SCALE] от [base]. */
private fun capped(base: TextUnit, fontScale: Float): TextUnit =
    if (base.isSpecified && fontScale > MAX_LABEL_FONT_SCALE) {
        (base.value * MAX_LABEL_FONT_SCALE / fontScale).sp
    } else {
        base
    }
