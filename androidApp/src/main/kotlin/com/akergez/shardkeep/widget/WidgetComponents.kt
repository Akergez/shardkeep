package com.akergez.shardkeep.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.compose.ui.unit.dp
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/** Title + ▾/▴ chevron that toggles the widget's item picker. Tapping anywhere fires [toggleAction]. */
@Composable
fun DropdownHeader(title: String, expanded: Boolean, toggleAction: Action) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(toggleAction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = if (expanded) "▴" else "▾",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

/** One selectable row in the picker; the current selection is marked with a filled dot. */
@Composable
fun PickerRow(title: String, isSelected: Boolean, action: Action) {
    Text(
        text = (if (isSelected) "●  " else "○  ") + title,
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(action),
        style = TextStyle(
            color = if (isSelected) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
            fontSize = 14.sp,
        ),
    )
}
