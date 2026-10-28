package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareSelectionTest {
    private fun node(role: MessageRole, text: String = role.name) = MessageNode(
        messages = listOf(
            UIMessage(
                role = role,
                parts = listOf(UIMessagePart.Text(text)),
            )
        )
    )

    @Test
    fun `sharing an assistant answer selects its question and the answer`() {
        val user = node(MessageRole.USER)
        val assistant = node(MessageRole.ASSISTANT)
        val nodes = listOf(user, assistant)

        assertEquals(listOf(user.id, assistant.id), defaultShareNodeIds(nodes, 1))
    }

    @Test
    fun `sharing a question selects the question and its answer`() {
        val user = node(MessageRole.USER)
        val assistant = node(MessageRole.ASSISTANT)
        val nodes = listOf(user, assistant)

        assertEquals(listOf(user.id, assistant.id), defaultShareNodeIds(nodes, 0))
    }

    @Test
    fun `other turns are not selected`() {
        val user1 = node(MessageRole.USER)
        val assistant1 = node(MessageRole.ASSISTANT)
        val user2 = node(MessageRole.USER)
        val assistant2 = node(MessageRole.ASSISTANT)
        val nodes = listOf(user1, assistant1, user2, assistant2)

        assertEquals(listOf(user2.id, assistant2.id), defaultShareNodeIds(nodes, 2))
        assertEquals(listOf(user2.id, assistant2.id), defaultShareNodeIds(nodes, 3))
    }

    @Test
    fun `question without an answer selects only itself`() {
        val user = node(MessageRole.USER)
        val assistant = node(MessageRole.ASSISTANT)
        val nodes = listOf(assistant, user)

        assertEquals(listOf(user.id), defaultShareNodeIds(nodes, 1))
    }

    @Test
    fun `answer at the start of the conversation selects only itself`() {
        val assistant = node(MessageRole.ASSISTANT)
        val user = node(MessageRole.USER)
        val nodes = listOf(assistant, user)

        assertEquals(listOf(assistant.id), defaultShareNodeIds(nodes, 0))
    }

    @Test
    fun `two consecutive questions do not pair with each other`() {
        val user1 = node(MessageRole.USER)
        val user2 = node(MessageRole.USER)
        val assistant = node(MessageRole.ASSISTANT)
        val nodes = listOf(user1, user2, assistant)

        assertEquals(listOf(user1.id), defaultShareNodeIds(nodes, 0))
        assertEquals(listOf(user2.id, assistant.id), defaultShareNodeIds(nodes, 1))
    }

    @Test
    fun `system message selects only itself`() {
        val system = node(MessageRole.SYSTEM)
        val user = node(MessageRole.USER)
        val nodes = listOf(system, user)

        assertEquals(listOf(system.id), defaultShareNodeIds(nodes, 0))
    }

    @Test
    fun `out of bounds index selects nothing`() {
        val nodes = listOf(node(MessageRole.USER))

        assertEquals(emptyList<Any>(), defaultShareNodeIds(nodes, 1))
        assertEquals(emptyList<Any>(), defaultShareNodeIds(emptyList(), 0))
    }
}
