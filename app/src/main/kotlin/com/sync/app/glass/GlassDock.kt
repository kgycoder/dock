package com.sync.app.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.sync.app.glass.kyant.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Kyant `LiquidButton` default press "pop" (scale-up + drag-stretch + damped drag offset), extracted verbatim so every
 * glass surface (tabs keep their own, buttons / mini player / search capsule use this) shares the same feel.
 */
internal fun GraphicsLayerScope.liquidPop(h: InteractiveHighlight) {
    val width = size.width
    val height = size.height

    val progress = h.pressProgress
    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = h.offset
    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

    val maxDragScale = 4f.dp.toPx() / size.height
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX =
        scale +
                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                (width / height).fastCoerceAtMost(1f)
    scaleY =
        scale +
                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                (height / width).fastCoerceAtMost(1f)
}

/** Round single-icon glass button (Kyant `LiquidButton` surface: vibrancy + blur 2dp + lens 12/24dp + press pop). */
@Composable
internal fun GlassCircle(
    backdrop: Backdrop,
    modifier: Modifier,
    appear: () -> Float,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = {
                    liquidPop(highlight)
                    val a = appear().coerceIn(0f, 1f)
                    val pop = popScale(a)
                    scaleX *= pop
                    scaleY *= pop
                    alpha = a
                },
                onDrawSurface = { drawRect(GlassTheme.Container) }
            )
            .tap(onClick = onClick)
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}
