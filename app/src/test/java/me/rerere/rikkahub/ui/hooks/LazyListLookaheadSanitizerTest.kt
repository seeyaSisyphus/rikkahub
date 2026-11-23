package me.rerere.rikkahub.ui.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LazyListLookaheadSanitizerTest {
    private fun anchor(
        index: Int = 47,
        offset: Int = 800,
        totalItems: Int = 51,
        viewportHeight: Int = 2200,
        afterContentPadding: Int = 400,
        lastKey: Any? = "ScrollBottomKey",
        lastOffset: Int = 1797,
        lastSize: Int = 3,
    ) = BottomSanitizeAnchor(
        firstVisibleItemIndex = index,
        firstVisibleItemScrollOffset = offset,
        totalItemsCount = totalItems,
        viewportHeight = viewportHeight,
        afterContentPadding = afterContentPadding,
        lastVisibleItemKey = lastKey,
        lastVisibleItemOffset = lastOffset,
        lastVisibleItemSize = lastSize,
    )

    @Test
    fun `initial bottom settle requests single sanitize remeasure then stops`() {
        val tracker = LookaheadScrollBackSanitizerTracker()
        val a0 = anchor()

        val target = tracker.onLayoutSnapshot(
            anchor = a0,
            canScrollForward = false,
            canScrollBackward = true,
            isScrollInProgress = false,
        )
        assertEquals(SanitizeScrollTarget(index = 47, scrollOffset = 800), target)

        // 清理重测完成后再次收到相同锚点，应结束本轮不再循环
        val followUp = tracker.onLayoutSnapshot(
            anchor = a0,
            canScrollForward = false,
            canScrollBackward = true,
            isScrollInProgress = false,
        )
        assertNull(followUp)
    }

    @Test
    fun `repeated jump to bottom with identical anchor triggers fresh sanitize`() {
        val tracker = LookaheadScrollBackSanitizerTracker()
        val a0 = anchor()

        // 首次进入并清理完成
        assertEquals(
            SanitizeScrollTarget(47, 800),
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )
        assertNull(
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )

        // 在底部再次触发 scrollToItem(bottom) 产出新的 MeasureResult（即使锚点数值不变）
        assertEquals(
            SanitizeScrollTarget(47, 800),
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )
        assertNull(
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )
    }

    @Test
    fun `ignores short list or non-bottom or active scroll`() {
        val tracker = LookaheadScrollBackSanitizerTracker()
        val a0 = anchor()

        // 短对话无法向上回滚
        assertNull(
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = false,
                isScrollInProgress = false
            ),
        )
        // 未在底部
        assertNull(
            tracker.onLayoutSnapshot(a0, canScrollForward = true, canScrollBackward = true, isScrollInProgress = false),
        )
        // 正在滚动/拖动中
        assertNull(
            tracker.onLayoutSnapshot(a0, canScrollForward = false, canScrollBackward = true, isScrollInProgress = true),
        )
        // 空列表或视口未就绪
        assertNull(
            tracker.onLayoutSnapshot(
                null,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )
        assertNull(
            tracker.onLayoutSnapshot(
                anchor(viewportHeight = 0),
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun `monotonic ime closing frames sanitize through to final settled padding`() {
        val tracker = LookaheadScrollBackSanitizerTracker()

        // 模拟软键盘收起 12 帧，每帧 afterContentPadding 单调缩小且列表在底部自动下移
        for (step in 1..12) {
            val pad = 1200 - step * 60
            val offset = 800 - step * 30
            val result = tracker.onLayoutSnapshot(
                anchor = anchor(offset = offset, afterContentPadding = pad),
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false,
            )
            assertEquals(SanitizeScrollTarget(47, offset), result)
        }

        // 最后一帧稳定后的清理重测命中 pendingAnchor，停止重测
        val finalAnchor = anchor(offset = 800 - 12 * 30, afterContentPadding = 1200 - 12 * 60)
        assertNull(
            tracker.onLayoutSnapshot(
                anchor = finalAnchor,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun `oscillating anchors trip circuit breaker to prevent infinite remeasure`() {
        val tracker = LookaheadScrollBackSanitizerTracker()
        val a = anchor(offset = 800)
        val b = anchor(offset = 801)

        assertEquals(
            SanitizeScrollTarget(47, 800),
            tracker.onLayoutSnapshot(a, canScrollForward = false, canScrollBackward = true, isScrollInProgress = false),
        )
        assertEquals(
            SanitizeScrollTarget(47, 801),
            tracker.onLayoutSnapshot(b, canScrollForward = false, canScrollBackward = true, isScrollInProgress = false),
        )
        // 再次回到已见过的 A，触发熔断返回 null
        assertNull(
            tracker.onLayoutSnapshot(a, canScrollForward = false, canScrollBackward = true, isScrollInProgress = false),
        )
    }

    @Test
    fun `cancelPending allows next bottom snapshot to be sanitized`() {
        val tracker = LookaheadScrollBackSanitizerTracker()
        val a0 = anchor()

        assertEquals(
            SanitizeScrollTarget(47, 800),
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )
        // 实时状态检查未通过（例如用户恰好在同帧开始拖动），放弃本次发起
        tracker.cancelPending()

        // 之后重新静止到底部时，不应误判为已完成清理
        assertEquals(
            SanitizeScrollTarget(47, 800),
            tracker.onLayoutSnapshot(
                a0,
                canScrollForward = false,
                canScrollBackward = true,
                isScrollInProgress = false
            ),
        )
    }
}
