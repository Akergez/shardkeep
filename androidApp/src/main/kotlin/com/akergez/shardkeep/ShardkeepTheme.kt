package com.akergez.shardkeep

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akergez.nativecolors.NativeColorsTheme
import com.akergez.nativecolors.NativeTheme

/**
 * The palette comes from [NativeTheme]: Material You on Android 12+, a palette generated from
 * the accent below that, and light or dark following the system.
 */
@Composable
fun ShardkeepTheme(content: @Composable () -> Unit) {
    NativeTheme {
        val colors = NativeColorsTheme.colors
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.windowBackground,
            contentColor = colors.onSurface,
            content = content,
        )
    }
}
