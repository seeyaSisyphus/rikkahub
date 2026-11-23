package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

internal data class BottomSanitizeAnchor(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val totalItemsCount: Int,
    val viewportHeight: Int,
    val afterContentPadding: Int,
    val lastVisibleItemKey: Any?,
    val lastVisibleItemOffset: Int,
    val lastVisibleItemSize: Int,
)

internal data class SanitizeScrollTarget(
    val index: Int,
    val scrollOffset: Int,
)

/**
 * [FORK] 保留项（sync-upstream 合并上游时禁止删除）：
 * 追踪 `LazyListState` 停留在底部时的状态机，用来规避 Compose Foundation 在 `LookaheadScope`
 * （`RouteActivity` 的 `SharedTransitionLayout`）下的 `scrollBackAmount` 残留 bug：
 *
 * 1. 进入会话 `requestScrollToItem(bottom)`、点击滚底、流式贴底、软键盘收起或底部消息收缩时，
 *    Lookahead 测量因触底回滚会在 `LazyListMeasureResult.scrollBackAmount` 上留下一个大值（约一整屏）。
 * 2. 用户随后手指下滑（往上看历史消息）时，只要最后一个 item 还在 `afterContentPadding`（输入区高度）范围内，
 *    `LazyListState.onScroll` 会走 `copyWithScrollDeltaWithoutRemeasure` 快路径；该函数把旧的
 *    `scrollBackAmount` 原样复制过来，且 `applyMeasureResult(isLookingAhead = hasLookaheadOccurred)`
 *    在每一帧快路径拖动中反复调用 `updateScrollDeltaForApproach(scrollBackAmount)`，把负向偏移不断累加到
 *    Approach 动画状态里。
 * 3. 等滑过输入区高度、快路径失效触发首次 `forceRemeasure()` 时，Approach 读到这个巨大的负向偏移，
 *    瞬间把画面拉回底部，再由 spring 动画弹回手指位置。
 *
 * 规避方式：当列表静止停在可回滚的底部时，在当前已落位的 `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`
 * 上触发一次幂等的 `requestScrollToItem` 重测。由于该位置已经铺满视口、无需回滚，Lookahead 会产出
 * `scrollBackAmount = 0f` 的新结果，同时 `positionChanged == false` 不移动画面也不重置 item 动画。
 */
internal class LookaheadScrollBackSanitizerTracker {
    private var pendingAnchor: BottomSanitizeAnchor? = null
    private val seenAnchorsInBurst = mutableSetOf<BottomSanitizeAnchor>()

    /**
     * 处理一次新的布局快照。
     * 返回非 null 时调用方应在确认实时状态仍匹配后触发一次幂等 `requestScrollToItem`。
     */
    fun onLayoutSnapshot(
        anchor: BottomSanitizeAnchor?,
        canScrollForward: Boolean,
        canScrollBackward: Boolean,
        isScrollInProgress: Boolean,
    ): SanitizeScrollTarget? {
        if (
            anchor == null ||
            anchor.viewportHeight <= 0 ||
            anchor.totalItemsCount <= 0 ||
            isScrollInProgress ||
            canScrollForward ||
            !canScrollBackward
        ) {
            reset()
            return null
        }

        if (pendingAnchor == anchor) {
            // 刚刚发起的幂等重测已落地且位置未变，scrollBackAmount 已归零，结束本轮。
            reset()
            return null
        }

        if (anchor in seenAnchorsInBurst) {
            // 极端情况下布局在同一批锚点间来回震荡（如 A <-> B），熔断避免死循环重测。
            reset()
            return null
        }

        seenAnchorsInBurst.add(anchor)
        pendingAnchor = anchor
        return SanitizeScrollTarget(
            index = anchor.firstVisibleItemIndex,
            scrollOffset = anchor.firstVisibleItemScrollOffset,
        )
    }

    /** 实时状态校验未通过、实际未发起重测时调用，避免误以为下一帧是自己的清理重测。 */
    fun cancelPending() {
        reset()
    }

    private fun reset() {
        pendingAnchor = null
        seenAnchorsInBurst.clear()
    }
}

private data class LookaheadLayoutEmission(
    // 保留 layoutInfo 引用本身，利用引用不等让每次新的 Lookahead 测量结果都能触发 snapshotFlow 发射
    // （即使在底部再次调用 scrollToItem(bottom) 后 anchor 数值完全相同也能捕获）
    val layoutInfo: LazyListLayoutInfo,
    val anchor: BottomSanitizeAnchor?,
    val canScrollForward: Boolean,
    val canScrollBackward: Boolean,
    val isScrollInProgress: Boolean,
)

/**
 * [FORK] 在 `SharedTransitionLayout`（`LookaheadScope`）内的垂直 `LazyColumn` 上挂载，
 * 自动清理触底回滚残留的 `scrollBackAmount`。合并上游时务必保留。
 */
@Composable
fun LazyListLookaheadScrollBackSanitizer(
    lazyListState: LazyListState,
) {
    LaunchedEffect(lazyListState) {
        val tracker = LookaheadScrollBackSanitizerTracker()
        snapshotFlow {
            val info = lazyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            val anchor = if (info.viewportSize.height > 0 && info.totalItemsCount > 0 && lastVisible != null) {
                BottomSanitizeAnchor(
                    firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
                    totalItemsCount = info.totalItemsCount,
                    viewportHeight = info.viewportSize.height,
                    afterContentPadding = info.afterContentPadding,
                    lastVisibleItemKey = lastVisible.key,
                    lastVisibleItemOffset = lastVisible.offset,
                    lastVisibleItemSize = lastVisible.size,
                )
            } else {
                null
            }
            LookaheadLayoutEmission(
                layoutInfo = info,
                anchor = anchor,
                canScrollForward = lazyListState.canScrollForward,
                canScrollBackward = lazyListState.canScrollBackward,
                isScrollInProgress = lazyListState.isScrollInProgress,
            )
        }.collect { emission ->
            val target = tracker.onLayoutSnapshot(
                anchor = emission.anchor,
                canScrollForward = emission.canScrollForward,
                canScrollBackward = emission.canScrollBackward,
                isScrollInProgress = emission.isScrollInProgress,
            ) ?: return@collect

            if (
                !lazyListState.isScrollInProgress &&
                !lazyListState.canScrollForward &&
                lazyListState.canScrollBackward &&
                lazyListState.firstVisibleItemIndex == target.index &&
                lazyListState.firstVisibleItemScrollOffset == target.scrollOffset
            ) {
                lazyListState.requestScrollToItem(target.index, target.scrollOffset)
            } else {
                tracker.cancelPending()
            }
        }
    }
}
