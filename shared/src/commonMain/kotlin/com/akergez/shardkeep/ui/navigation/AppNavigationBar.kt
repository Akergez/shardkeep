package com.akergez.shardkeep.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.akergez.shardkeep.ui.AppSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Нижняя панель — компактные окна (телефон). */
@Composable
fun AppNavigationBar(
    section: AppSection,
    showNotes: Boolean,
    onSectionChange: (AppSection) -> Unit,
) {
    NavigationBar {
        AppNavigationDestinations(
            showNotes = showNotes,
            item = { destination, icon, label ->
                NavigationBarItem(
                    selected = section == destination,
                    onClick = { onSectionChange(destination) },
                    icon = { Icon(painterResource(icon), contentDescription = null) },
                    label = { Text(stringResource(label)) },
                )
            },
        )
    }
}
