package me.rerere.rikkahub.ui.components.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputTest {
    @Test
    fun `empty input shows and invokes stop while generating`() {
        assertTrue(isStopGenerationAction(loading = true, inputEmpty = true))
    }

    @Test
    fun `draft input keeps send action while generating`() {
        assertFalse(isStopGenerationAction(loading = true, inputEmpty = false))
    }

    @Test
    fun `idle input never uses stop action`() {
        assertFalse(isStopGenerationAction(loading = false, inputEmpty = true))
        assertFalse(isStopGenerationAction(loading = false, inputEmpty = false))
    }

    @Test
    fun `draft input shows queued clock action while generating unless editing`() {
        assertTrue(isQueuedSendAction(loading = true, inputEmpty = false, isEditing = false))
        assertFalse(isQueuedSendAction(loading = true, inputEmpty = true, isEditing = false))
        assertFalse(isQueuedSendAction(loading = true, inputEmpty = false, isEditing = true))
        assertFalse(isQueuedSendAction(loading = false, inputEmpty = false, isEditing = false))
    }
}
