package com.akergez.shardkeep

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Shardkeep",
        icon = shardkeepAppIconPainter(),
    ) {
        App()
    }
}
