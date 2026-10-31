package com.sync.app.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.sync.app.glass.kyant.LiquidBottomTab
import com.sync.app.glass.kyant.LiquidBottomTabs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Apple-Music style dock.
 *
 *   expanded : [ 홈 · 즐겨찾기 · 대기열 · 플레이리스트 ]  (◯ 검색)       mini player floats above the row
 *   collapsed: (◯ 홈)  [ ▢ mini player ]  (◯ 검색)                      reached by scrolling down while music plays
 *
 * DOM indexes (glass-bridge `nav.active`): 0 home · 1 search · 2 fav · 3 queue · 4 playlists.
 * Tab indexes (the 4-tab capsule): 0 home · 1 fav · 2 queue · 3 playlists. Search lives in its own circle.
 */
private val TabLabels = listOf("홈", "즐겨찾기", "대기열", "플레이리스트")
private val TabViews = listOf("home", "fav", "queue", "playlists")
private const val DOCK_GAP = 10f

private fun tabOfDom(dom: Int): Int = when (dom) { 0 -> 0; 2 -> 1; 3 -> 2; 4 -> 3; else -> -1 }
private fun domOfTab(tab: Int): Int = when (tab) { 0 -> 0; 1 -> 2; 2 -> 3; else -> 4 }

private fun Modifier.dpBox(x: Float, y: Float, w: Float, h: Float): Modifier = this
    .offset { IntOffset((x * density).roundToInt(), (y * density).roundToInt()) }
    .size(w.dp, h.dp)

@Composable
internal fun DockGlass(nav: NavState, bar: BarState?, backdrop: Backdrop, c: GlassController) {
    val r = nav.rect
    val h = r.h
    val tabsW = r.w - h - DOCK_GAP

    // 0 = expanded, 1 = merged. Slightly under-damped so the capsules overshoot and settle like a drop of liquid.
    val collapsedTarget = bar != null && c.dockCollapsed
    val p = remember { Animatable(if (collapsedTarget) 1f else 0f) }
    LaunchedEffect(collapsedTarget) {
        p.animateTo(if (collapsedTarget) 1f else 0f, spring(0.62f, 230f, 0.002f))
    }
    val popIn = rememberPopIn()
    val appear = remember(popIn) { { popIn.value } }

    val tabsShown by remember { derivedStateOf { p.value < 0.995f } }
    val homeShown by remember { derivedStateOf { p.value > 0.005f } }

    val domActive = rememberUpdatedState(nav.active)
    val lastTab = remember { mutableIntStateOf(tabOfDom(nav.active).coerceAtLeast(0)) }
    LaunchedEffect(nav.active) { tabOfDom(nav.active).let { if (it >= 0) lastTab.intValue = it } }
    val selected = remember { { lastTab.intValue } }
    val goDom = remember(c) {
        { dom: Int, view: String -> if (dom != domActive.value) c.cmd { put("t", "nav"); put("v", view) } }
    }
    val onTab = remember(goDom) { { index: Int -> goDom(domOfTab(index), TabViews[index]) } }
    val accent = LocalGlassAccent.current

    // ── tab capsule (expanded) ──
    if (tabsShown) {
        Box(
            Modifier
                .dpBox(r.x, r.y, tabsW, h)
                .graphicsLayer {
                    val q = p.value.coerceIn(0f, 1f)
                    val s = lerp(1f, 0.55f, q) * popScale(popIn.value)
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = (1f - q * 1.7f).coerceIn(0f, 1f) * popIn.value.coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center
        ) {
            LiquidBottomTabs(
                selectedTabIndex = selected,
                onTabSelected = onTab,
                backdrop = backdrop,
                tabsCount = TabLabels.size,
                accentColor = accent,
                tabHeight = h.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(TabLabels.size) { index ->
                    LiquidBottomTab({ onTab(index) }) {
                        Canvas(Modifier.size(22.dp)) {
                            strokeIcon(tabIcon(index), 20f, GlassTheme.Content.copy(0.8f), 1.6f)
                        }
                        BasicText(
                            TabLabels[index],
                            style = TextStyle(GlassTheme.Content.copy(0.8f), 10.sp, FontWeight.Medium),
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }

    // ── home circle (merged) ──
    if (homeShown) {
        GlassCircle(
            backdrop = backdrop,
            modifier = Modifier
                .dpBox(r.x, r.y, h, h)
                .graphicsLayer {
                    val q = p.value.coerceIn(0f, 1f)
                    // the circle is what the tab capsule folds into → it only becomes visible late in the morph
                    val s = lerp(0.55f, 1f, q)
                    scaleX = s; scaleY = s
                    alpha = (q * 1.7f - 0.7f).coerceIn(0f, 1f)
                },
            appear = appear,
            onClick = { goDom(0, "home") }
        ) {
            Canvas(Modifier.size(24.dp)) {
                strokeIcon(GlassIcons.Home, 20f, if (domActive.value == 0) accent else GlassTheme.Content.copy(0.9f), 1.7f)
            }
        }
    }

    // ── search circle (always) ──
    GlassCircle(
        backdrop = backdrop,
        modifier = Modifier.dpBox(r.x + r.w - h, r.y, h, h),
        appear = appear,
        onClick = { goDom(1, "search") }
    ) {
        Canvas(Modifier.size(24.dp)) {
            strokeIcon(GlassIcons.Search, 20f, if (domActive.value == 1) accent else GlassTheme.Content.copy(0.9f), 1.7f)
        }
    }

    // ── mini player: floats above the dock, or sits between the two circles ──
    if (bar != null) {
        val br = bar.rect
        val sideW = h + DOCK_GAP
        val placement = Modifier
            .offset {
                val q = p.value
                val x = lerp(br.x, r.x + sideW, q)
                val y = lerp(br.y, r.y, q)
                IntOffset((x * density).roundToInt(), (y * density).roundToInt())
            }
            .layout { m, _ ->
                val q = p.value
                val w = max(1, (lerp(br.w, r.w - 2f * sideW, q) * density).roundToInt())
                val hh = max(1, (lerp(br.h, h, q) * density).roundToInt())
                val pl = m.measure(Constraints.fixed(w, hh))
                layout(w, hh) { pl.place(0, 0) }
            }
        MiniPlayerGlass(bar, backdrop, c, placement) { p.value }
    }
}

private fun tabIcon(index: Int) = when (index) {
    0 -> GlassIcons.Home
    1 -> GlassIcons.Fav
    2 -> GlassIcons.Queue
    else -> GlassIcons.Playlists
}
