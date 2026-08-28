package com.akergez.shardkeep.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akergez.shardkeep.ui.AppSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Свёрнутый рельс — средние и широкие окна, а также планшеты под палец. */
@Composable
fun AppNavigationRail(
    section: AppSection,
    showNotes: Boolean,
    onSectionChange: (AppSection) -> Unit,
) {
    NavigationRail {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            AppNavigationDestinations(
                showNotes = showNotes,
                item = { destination, icon, label ->
                    NavigationRailItem(
                        selected = section == destination,
                        onClick = { onSectionChange(destination) },
                        icon = { Icon(painterResource(icon), contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                },
            )
        }
    }
}
