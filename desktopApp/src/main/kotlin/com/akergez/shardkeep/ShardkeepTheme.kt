package com.akergez.shardkeep

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.nativecolors.NativeColorsTheme
import com.akergez.nativecolors.NativeFlavor
import com.akergez.nativecolors.NativeTheme

/** Aqua draws its controls rounder than Material's defaults. */
private val MacOsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * The palette comes from [NativeTheme], which reads the accent colour and the light/dark
 * preference of whichever desktop the app was launched on and follows it while it runs.
 *
 * The root fill is the palette's `viewBackground`, not `windowBackground`. The two are a
 * genuine distinction on macOS, not a shade apart by accident: `windowBackgroundColor`
 * (#ECECEC in light Aqua) belongs behind grouped content — the grey a preference pane
 * shows *around* its cards — while lists, editors and other panes that hold data sit on
 * `textBackgroundColor`, which is white. Shardkeep is a list from edge to edge, so the
 * window colour applied to the whole surface read as an unlit grey slab next to the white
 * rail and toolbar the Material defaults were already drawing.
 */
@Composable
fun ShardkeepTheme(content: @Composable () -> Unit) {
    NativeTheme {
        val colors = NativeColorsTheme.colors
        MaterialTheme(
            colorScheme = colors.material,
            shapes = if (colors.flavor == NativeFlavor.MACOS) MacOsShapes else MaterialTheme.shapes,
            typography = MaterialTheme.typography,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.viewBackground,
                contentColor = colors.onSurface,
                content = content,
            )
        }
    }
}
