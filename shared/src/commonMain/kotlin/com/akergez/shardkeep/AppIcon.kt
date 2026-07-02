package com.akergez.shardkeep

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.app_icon

@Composable
fun shardkeepAppIconPainter(): Painter = painterResource(Res.drawable.app_icon)
