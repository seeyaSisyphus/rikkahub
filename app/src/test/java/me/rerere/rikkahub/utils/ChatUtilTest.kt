package me.rerere.rikkahub.utils

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUtilTest {
    @Test
    fun `allows agc camera configs as text documents`() {
        assertTrue(isAllowedFileType("camera-config.agc", "application/octet-stream"))
        assertTrue(isAllowedFileType("CAMERA-CONFIG.AGC", "application/octet-stream"))
    }

    @Test
    fun `still rejects unknown binary file types`() {
        assertFalse(isAllowedFileType("archive.unknown", "application/octet-stream"))
    }

    @Test
    fun `copy text strips markdown and keeps parts separate`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("### 标题\n\n**重点**"),
                UIMessagePart.Text("```kotlin\nval a = 1\n```"),
            ),
        )
        assertEquals("标题\n\n重点\n\nval a = 1", message.toCopyText())
    }
}
