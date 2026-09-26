package com.volkskamera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volkskamera.app.ui.theme.FilmWhite
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Rot des kleinen Pfeils aus der Skizze */
val IndicatorRed = Color(0xFFE8432A)

/**
 * Stellrad wie auf der Skizze: gewählter Wert groß zwischen zwei Linien, roter Pfeil links,
 * Nachbarwerte werden mit Abstand kleiner und blasser (Walzen-Optik). Scrollen mit dem Finger,
 * rastet beim Loslassen ein; Antippen eines Nachbarwerts dreht dorthin.
 */
@Composable
fun <T> DialWheel(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    visible: Int = 5,
    itemHeight: Dp = 26.dp,
    width: Dp = 72.dp,
    enabled: Boolean = true,
) {
    val selIdx = items.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selIdx)
    val scope = rememberCoroutineScope()
    val half = visible / 2

    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Welcher Eintrag steht gerade in der Mitte? Der, dessen Mitte der Viewport-Mitte am nächsten ist.
    val centerIdx by remember {
        derivedStateOf {
            val li = state.layoutInfo
            val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2
            li.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selIdx
        }
    }
    // Nach dem Einrasten übernehmen
    LaunchedEffect(state, items) {
        snapshotFlow { state.isScrollInProgress to centerIdx }
            .filter { !it.first }
            .distinctUntilChanged()
            .collect { (_, idx) -> items.getOrNull(idx)?.let { if (it != currentSelected) currentOnSelect(it) } }
    }
    // Von außen geänderte Auswahl (z.B. Preset) ins Rad übernehmen
    LaunchedEffect(selIdx) {
        if (!state.isScrollInProgress && centerIdx != selIdx) state.animateScrollToItem(selIdx)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (title != null) {
            Text(title, color = FilmWhite.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(width).height(itemHeight * visible)) {
            LazyColumn(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                userScrollEnabled = enabled,
                contentPadding = PaddingValues(vertical = itemHeight * half),
                modifier = Modifier.fillMaxWidth().height(itemHeight * visible)
                    // kräftiges Ausblenden oben und unten: die Walze "verschwindet", keine harte Kante
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent, 0.3f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(items) { i, item ->
                    val d = abs(i - centerIdx)
                    val scale = when (d) { 0 -> 1f; 1 -> 0.68f; else -> 0.5f }
                    val alpha = when (d) { 0 -> 1f; 1 -> 0.7f; else -> 0.4f }
                    Box(
                        Modifier.height(itemHeight).fillMaxWidth()
                            .clickable(enabled = enabled) { scope.launch { state.animateScrollToItem(i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(item),
                            color = FilmWhite.copy(alpha = if (enabled) alpha else alpha * 0.4f),
                            fontSize = 20.sp,
                            fontWeight = if (d == 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                    }
                }
            }
            // Linien über/unter dem gewählten Wert und der rote Pfeil – rein dekorativ
            Canvas(Modifier.width(width).height(itemHeight * visible)) {
                val top = itemHeight.toPx() * half
                val bottom = top + itemHeight.toPx()
                val line = FilmWhite.copy(alpha = 0.75f)
                val inset = size.width * 0.18f
                drawLine(line, Offset(inset, top), Offset(size.width - inset * 0.4f, top), 2.5f)
                drawLine(line, Offset(inset * 0.6f, bottom), Offset(size.width, bottom), 2.5f)
                val cy = (top + bottom) / 2
                val s = itemHeight.toPx() * 0.22f
                drawPath(Path().apply {
                    moveTo(0f, cy - s); lineTo(s * 1.4f, cy); lineTo(0f, cy + s); close()
                }, IndicatorRed)
            }
        }
    }
}
