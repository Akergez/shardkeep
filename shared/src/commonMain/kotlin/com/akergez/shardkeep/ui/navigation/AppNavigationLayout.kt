package com.akergez.shardkeep.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which of Material 3's navigation containers a given window width calls for.
 *
 * The breakpoints are M3's window size classes. A collapsed rail is specified for medium and
 * expanded windows; once a window reaches the large class there is room to spell the
 * destinations out, and the rail expands — the same items with the label beside the icon
 * rather than stacked under it. Stacked labels are a phone and tablet convention, and a
 * desktop window sits well past the width where they still read as intentional.
 *
 * The expanded rail is the narrow end of what M3 offers here: 220dp against the 360dp of a
 * permanent drawer, which is the other container specified for this width. Rows stay 56dp
 * either way — that is the spec's own metric, not a default left unconsidered.
 */
enum class AppNavigationLayout { Bar, Rail, Sidebar }

/** Start of M3's medium window size class: below this a window is compact. */
private val MediumWindowWidth = 600.dp

/** Start of M3's large window size class, where the rail gives way to a permanent drawer. */
private val LargeWindowWidth = 1200.dp

fun navigationLayoutFor(width: Dp, finePointer: Boolean): AppNavigationLayout = when {
    width < MediumWindowWidth -> AppNavigationLayout.Bar
    width < LargeWindowWidth -> AppNavigationLayout.Rail
    // A large tablet in landscape is as wide as a laptop, so width alone would hand it the
    // sidebar — whose rows are sized for a cursor and sit under the touch target a finger
    // needs. Touch keeps the rail, which is both M3's answer and what these devices had.
    finePointer -> AppNavigationLayout.Sidebar
    else -> AppNavigationLayout.Rail
}
