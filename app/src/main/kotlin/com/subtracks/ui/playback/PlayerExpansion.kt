package com.subtracks.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize

class PlayerExpansion(
    val progress: () -> Float,
    val sheetStartY: () -> Float,
) {
    internal var cover by mutableStateOf<Rect?>(null)
    internal var metadata by mutableStateOf<Rect?>(null)
    internal var transport by mutableStateOf<Rect?>(null)
}

internal fun Modifier.captureAnchor(onBounds: (Rect) -> Unit): Modifier =
    onGloballyPositioned { onBounds(Rect(it.positionInRoot(), it.size.toSize())) }

@Composable
internal fun expansionModifier(
    transition: PlayerExpansion?,
    root: () -> LayoutCoordinates?,
    anchor: () -> Rect?,
    artwork: Boolean = false,
): Modifier {
    if (transition == null) return Modifier
    var target by remember { mutableStateOf<Rect?>(null) }
    return Modifier
        .onGloballyPositioned { child ->
            val parent = root()
            if (parent != null && parent.isAttached && child.isAttached) {
                target = Rect(parent.localPositionOf(child, Offset.Zero), child.size.toSize())
            }
        }.graphicsLayer {
            val remaining = 1f - transition.progress().coerceIn(0f, 1f)
            val start = anchor()
            val end = target
            if (remaining > 0f && start != null && end != null && end.minDimension() > 0f) {
                val ratio = if (artwork) start.minDimension() / end.minDimension() else start.height / end.height
                scaleX = 1f + (ratio - 1f) * remaining
                scaleY = scaleX
                translationX = (start.center.x - end.center.x) * remaining
                translationY = (start.center.y - transition.sheetStartY() - end.center.y) * remaining
            }
        }
}

private fun Rect.minDimension(): Float = minOf(width, height)
