package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/**
 * 计算输入区/软键盘导致的 `afterContentPadding` 变化时需要补偿的列表滚动距离。
 * 增高时全额上移，回落时扣掉 `LazyColumn` 处于底部已自动下移的位移，避免双倍回退。
 */
internal fun computeImeAutoScrollDelta(
    previousAfterPadding: Int,
    currentAfterPadding: Int,
    prevVisibleOffsets: Map<Any, Int>,
    currentVisibleOffsets: List<Pair<Any, Int>>,
): Float {
    val delta = currentAfterPadding - previousAfterPadding
    if (delta == 0) return 0f
    if (delta > 0) return delta.toFloat()
    val shrink = -delta
    val autoShift = currentVisibleOffsets.firstNotNullOfOrNull { (key, offset) ->
        prevVisibleOffsets[key]?.let { prevOffset ->
            (offset - prevOffset).coerceIn(0, shrink)
        }
    } ?: 0
    return -(shrink - autoShift).toFloat()
}

@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
    isSuppressed: () -> Boolean = { false },
) {
    val isSuppressedState by rememberUpdatedState(isSuppressed)
    LaunchedEffect(lazyListState) {
        var prevAfterPadding: Int? = null
        var prevVisibleOffsets: Map<Any, Int> = emptyMap()
        var wasSuppressed = false

        snapshotFlow {
            lazyListState.layoutInfo to isSuppressedState()
        }.collect { (layoutInfo, isSuppressed) ->
            if (isSuppressed) {
                wasSuppressed = true
                return@collect
            }
            if (layoutInfo.viewportSize.height <= 0 || layoutInfo.visibleItemsInfo.isEmpty()) {
                if (layoutInfo.totalItemsCount == 0) {
                    prevAfterPadding = null
                    prevVisibleOffsets = emptyMap()
                }
                return@collect
            }

            val currentAfter = layoutInfo.afterContentPadding
            val currentOffsets = layoutInfo.visibleItemsInfo.associate { it.key to it.offset }

            val previousAfter = prevAfterPadding
            if (wasSuppressed || previousAfter == null) {
                wasSuppressed = false
                prevAfterPadding = currentAfter
                prevVisibleOffsets = currentOffsets
                return@collect
            }

            val scrollDelta = computeImeAutoScrollDelta(
                previousAfterPadding = previousAfter,
                currentAfterPadding = currentAfter,
                prevVisibleOffsets = prevVisibleOffsets,
                currentVisibleOffsets = layoutInfo.visibleItemsInfo.map { it.key to it.offset },
            )
            prevAfterPadding = currentAfter
            if (scrollDelta != 0f) {
                var consumed = 0f
                try {
                    consumed = lazyListState.scrollBy(scrollDelta)
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
                val updatedInfo = lazyListState.layoutInfo
                if (updatedInfo.afterContentPadding == currentAfter) {
                    prevVisibleOffsets = updatedInfo.visibleItemsInfo.associate { it.key to it.offset }
                } else {
                    val shift = consumed.roundToInt()
                    prevVisibleOffsets = currentOffsets.mapValues { (_, offset) -> offset - shift }
                }
            } else {
                prevVisibleOffsets = currentOffsets
            }
        }
    }
}
