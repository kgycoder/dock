package com.sync.app.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.sync.app.glass.kyant.InteractiveHighlight
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Stand-alone mini player (used only when the dock itself is not on screen). */
@Composable
internal fun BarGlass(bar: BarState, backdrop: Backdrop, c: GlassController) {
    MiniPlayerGlass(bar, backdrop, c, Modifier.cssRect(bar.rect)) { 0f }
}

/** Square whose edge length eases from [fromDp] to [toDp] with the dock collapse progress (layout phase only). */
private fun Modifier.lerpSquare(fromDp: Float, toDp: Float, q: () -> Float): Modifier = this.layout { m, _ ->
    val s = lerp(fromDp, toDp, q().coerceIn(0f, 1f)).dp.roundToPx()
    val pl = m.measure(Constraints.fixed(s, s))
    layout(s, s) { pl.place(0, 0) }
}

/** Slot that is [fullDp] wide at progress 0 and folds to zero width (and fades) at progress 1. */
private fun Modifier.foldWidth(fullDp: Float, heightDp: Float, q: () -> Float): Modifier = this
    .graphicsLayer { alpha = (1f - q().coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f) }
    .clipToBounds()
    .layout { m, _ ->
        val fw = fullDp.dp.roundToPx()
        val fh = heightDp.dp.roundToPx()
        val w = (fw * (1f - q().coerceIn(0f, 1f))).roundToInt().coerceAtLeast(0)
        val pl = m.measure(Constraints.fixed(fw, fh))
        layout(w, fh) { pl.place(0, 0) }
    }

/**
 * Mini player glass capsule: artwork · title/channel · play/pause (flat white glyph, no disc) · NEXT.
 *
 * [placement] gives it its rect; [collapse] (0 = floating above the dock, 1 = merged into the dock row) only drives the
 * NEXT slot and the squash/stretch "bloop" — geometry itself is animated by the caller through [placement].
 */
@Composable
internal fun MiniPlayerGlass(
    bar: BarState,
    backdrop: Backdrop,
    c: GlassController,
    placement: Modifier,
    collapse: () -> Float
) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val popIn = rememberPopIn()
    val art = rememberRemoteImage(bar.art)

    Box(
        placement
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = {
                    liquidPop(highlight)
                    // blob squash while the capsule travels between the two layouts
                    val pulse = sin(PI.toFloat() * collapse().coerceIn(0f, 1f))
                    val a = popIn.value.coerceIn(0f, 1f)
                    val pop = popScale(a)
                    scaleX *= pop * (1f + 0.04f * pulse)
                    scaleY *= pop * (1f - 0.035f * pulse)
                    alpha = a
                },
                onDrawSurface = { drawRect(GlassTheme.Container) }
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .lerpSquare(44f, 40f, collapse)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(0.08f))
                    .tap { c.cmd { put("t", "bar"); put("a", "open") } }
            ) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .tap { c.cmd { put("t", "bar"); put("a", "open") } },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                BasicText(
                    bar.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(Color.White.copy(0.96f), 13.5f.sp, FontWeight.SemiBold)
                )
                BasicText(
                    bar.channel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(Color.White.copy(0.55f), 11.sp)
                )
            }
            // play / pause — flat solid white glyph (same treatment as NEXT), no background disc
            Box(
                Modifier.size(width = 42.dp, height = 40.dp).tap { c.cmd { put("t", "bar"); put("a", "play") } },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(18.dp)) {
                    fillIcon(if (bar.playing) GlassIcons.PauseFill else GlassIcons.PlayFill, 512f, Color.White)
                }
            }
            // NEXT track (source path is a "previous" glyph, mirrored like the original CSS scaleX(-1))
            Box(
                Modifier
                    .foldWidth(42f, 40f, collapse)
                    .tap { c.cmd { put("t", "bar"); put("a", "next") } },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    scale(-1f, 1f, pivot = center) {
                        fillIcon(GlassIcons.NextFill, 512f, Color.White)
                    }
                }
            }
        }
    }
}
