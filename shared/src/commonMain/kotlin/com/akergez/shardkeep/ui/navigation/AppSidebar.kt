package com.akergez.shardkeep.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.ui.AppSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val SidebarWidth = 200.dp
private val SidebarRowHeight = 34.dp
private val SidebarIconSize = 18.dp

/**
 * The wide navigation area, built here rather than taken from Material 3.
 *
 * M3's expanded rail was the obvious candidate and it does not survive contact with a mouse.
 * Its metrics are finger-sized — a 56dp row against this list's 34dp — and the parts that
 * make it look wrong are not defaults but the spec: items sit flush against each other, since
 * the expanded rail sets its vertical spacing to zero, and their content is centred inside the
 * indicator, so icons land at a different offset on every row depending on how long the label
 * is. Neither is reachable through a parameter, and the container forces a 48dp floor on any
 * child, so a smaller row cannot be passed in either.
 *
 * What is kept is M3's colour semantics: the selected row uses the secondary container that
 * the rail's own active indicator uses, and the rest read as `onSurfaceVariant`. So the theme
 * still drives this — only the measurements are the desktop's rather than the phone's.
 */
@Composable
fun AppSidebar(
    section: AppSection,
    showNotes: Boolean,
    onSectionChange: (AppSection) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(SidebarWidth),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppNavigationDestinations(
                showNotes = showNotes,
                item = { destination, icon, label ->
                    val selected = section == destination
                    val content = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SidebarRowHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSectionChange(destination) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(SidebarIconSize),
                            tint = content,
                        )
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = content,
                        )
                    }
                },
            )
        }
    }
}
