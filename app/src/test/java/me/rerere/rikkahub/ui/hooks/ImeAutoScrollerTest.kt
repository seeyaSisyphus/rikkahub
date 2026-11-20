package me.rerere.rikkahub.ui.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeAutoScrollerTest {
    @Test
    fun `expanding afterContentPadding returns full positive delta`() {
        val delta = computeImeAutoScrollDelta(
            previousAfterPadding = 120,
            currentAfterPadding = 680,
            prevVisibleOffsets = mapOf("msg-1" to 400, "msg-2" to 620),
            currentVisibleOffsets = listOf("msg-1" to 400, "msg-2" to 620),
        )

        assertEquals(560f, delta, 0.001f)
    }

    @Test
    fun `shrinking at bottom where LazyColumn shifted items down returns zero`() {
        val delta = computeImeAutoScrollDelta(
            previousAfterPadding = 680,
            currentAfterPadding = 120,
            prevVisibleOffsets = mapOf("msg-1" to 100, "msg-2" to 320),
            currentVisibleOffsets = listOf("msg-1" to 660, "msg-2" to 880),
        )

        assertEquals(0f, delta, 0.001f)
    }

    @Test
    fun `shrinking in middle without auto shift returns full negative delta`() {
        val delta = computeImeAutoScrollDelta(
            previousAfterPadding = 680,
            currentAfterPadding = 120,
            prevVisibleOffsets = mapOf("msg-1" to 100, "msg-2" to 320),
            currentVisibleOffsets = listOf("msg-1" to 100, "msg-2" to 320),
        )

        assertEquals(-560f, delta, 0.001f)
    }

    @Test
    fun `shrinking with partial bottom alignment compensates only remaining delta`() {
        val delta = computeImeAutoScrollDelta(
            previousAfterPadding = 680,
            currentAfterPadding = 120,
            prevVisibleOffsets = mapOf("msg-1" to 100, "msg-2" to 320),
            currentVisibleOffsets = listOf("msg-1" to 300, "msg-2" to 520),
        )

        assertEquals(-360f, delta, 0.001f)
    }

    @Test
    fun `unchanged afterContentPadding returns zero`() {
        val delta = computeImeAutoScrollDelta(
            previousAfterPadding = 240,
            currentAfterPadding = 240,
            prevVisibleOffsets = mapOf("msg-1" to 100),
            currentVisibleOffsets = listOf("msg-1" to 100),
        )

        assertEquals(0f, delta, 0.001f)
    }
}
