package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationMessagesTest {
    @Test
    fun `deleting the only message of a node removes the node`() {
        val user = UIMessage.user("hi")
        val reply = UIMessage.assistant("hello")
        val conversation = conversationOf(user.toMessageNode(), reply.toMessageNode())

        val updated = conversation.deleteMessage(reply.id)!!

        assertEquals(listOf(user.id), updated.currentMessages.map { it.id })
    }

    @Test
    fun `deleting the selected last branch selects the previous one`() {
        val first = UIMessage.assistant("first")
        val second = UIMessage.assistant("second")
        val conversation = conversationOf(MessageNode(messages = listOf(first, second), selectIndex = 1))

        val updated = conversation.deleteMessage(second.id)!!

        assertEquals(listOf(first.id), updated.currentMessages.map { it.id })
    }

    @Test
    fun `deleting a missing message returns null`() {
        assertNull(conversationOf(UIMessage.user("hi").toMessageNode()).deleteMessage(Uuid.random()))
    }

    @Test
    fun `deleteMessagesAfter keeps target node and drops following nodes`() {
        val q1 = UIMessage.user("q1").toMessageNode()
        val a1 = UIMessage.assistant("a1").toMessageNode()
        val q2 = UIMessage.user("q2").toMessageNode()
        val a2 = UIMessage.assistant("a2").toMessageNode()
        val conversation = conversationOf(q1, a1, q2, a2).copy(
            conversationParams = ConversationParams(contextAnchorNodeId = a2.id),
        )

        val updated = conversation.deleteMessagesAfter(a1.currentMessage.id)!!

        assertEquals(listOf(q1.id, a1.id), updated.messageNodes.map { it.id })
        assertNull(updated.conversationParams.contextAnchorNodeId)
    }

    @Test
    fun `deleteMessagesAfter returns null on last node or missing message`() {
        val q1 = UIMessage.user("q1").toMessageNode()
        val a1 = UIMessage.assistant("a1").toMessageNode()
        val conversation = conversationOf(q1, a1)

        assertNull(conversation.deleteMessagesAfter(a1.currentMessage.id))
        assertNull(conversation.deleteMessagesAfter(Uuid.random()))
    }

    @Test
    fun `deleteMessagesBefore keeps target node and drops preceding nodes`() {
        val q1 = UIMessage.user("q1").toMessageNode()
        val a1 = UIMessage.assistant("a1").toMessageNode()
        val q2 = UIMessage.user("q2").toMessageNode()
        val a2 = UIMessage.assistant("a2").toMessageNode()
        val conversation = conversationOf(q1, a1, q2, a2).copy(
            conversationParams = ConversationParams(contextAnchorNodeId = q1.id),
        )

        val updated = conversation.deleteMessagesBefore(q2.currentMessage.id)!!

        assertEquals(listOf(q2.id, a2.id), updated.messageNodes.map { it.id })
        assertNull(updated.conversationParams.contextAnchorNodeId)
    }

    @Test
    fun `deleteMessagesBefore returns null on first node or missing message`() {
        val q1 = UIMessage.user("q1").toMessageNode()
        val a1 = UIMessage.assistant("a1").toMessageNode()
        val conversation = conversationOf(q1, a1)

        assertNull(conversation.deleteMessagesBefore(q1.currentMessage.id))
        assertNull(conversation.deleteMessagesBefore(Uuid.random()))
    }

    @Test
    fun `editing a message rewrites it in place and keeps its model`() {
        val modelId = Uuid.random()
        val reply = UIMessage.assistant("old").copy(modelId = modelId)
        val conversation = conversationOf(reply.toMessageNode())

        val node = conversation.editMessage(reply.id, listOf(UIMessagePart.Text("new"))).messageNodes.single()

        // [FORK] 编辑原地替换内容，不新增分支：模型、消息 id 等元数据保持不变
        assertEquals(1, node.messages.size)
        assertEquals(0, node.selectIndex)
        assertEquals(reply.id, node.currentMessage.id)
        assertEquals("new", node.currentMessage.toText())
        assertEquals(MessageRole.ASSISTANT, node.currentMessage.role)
        assertEquals(modelId, node.currentMessage.modelId)
    }

    @Test
    fun `editing a checkpoint rewrites it in place`() {
        val checkpoint = UIMessage.user("summary").copy(isContextCheckpoint = true)
        val conversation = conversationOf(checkpoint.toMessageNode())

        val node = conversation.editMessage(checkpoint.id, listOf(UIMessagePart.Text("edited"))).messageNodes.single()

        assertEquals(listOf(checkpoint.id), node.messages.map { it.id })
        assertTrue(node.currentMessage.isContextCheckpoint)
        assertEquals("edited", node.currentMessage.toText())
    }

    @Test
    fun `translation is set on the matching message without switching branches`() {
        val first = UIMessage.assistant("first")
        val second = UIMessage.assistant("second")
        val conversation = conversationOf(MessageNode(messages = listOf(first, second), selectIndex = 1))

        val node = conversation.withTranslation(first.id, "翻译").messageNodes.single()

        assertEquals(1, node.selectIndex)
        assertEquals("翻译", node.messages[0].translation)
        assertNull(node.messages[1].translation)
    }

    @Test
    fun `approval is recorded on the pending tool`() {
        val reply = toolReply(ToolApprovalState.Pending)
        val toolCallId = reply.getTools().single().toolCallId
        val conversation = conversationOf(reply.toMessageNode())

        val updated = conversation.withToolApproval(toolCallId, ToolApprovalState.Approved)!!

        assertEquals(ToolApprovalState.Approved, updated.currentMessages.single().getTools().single().approvalState)
    }

    @Test
    fun `approval for a tool that is no longer pending is ignored`() {
        val reply = toolReply(ToolApprovalState.Approved)
        val toolCallId = reply.getTools().single().toolCallId
        val conversation = conversationOf(reply.toMessageNode())

        assertNull(conversation.withToolApproval(toolCallId, ToolApprovalState.Denied("no")))
    }

    @Test
    fun `approval for a tool on an unselected branch is ignored`() {
        val pending = toolReply(ToolApprovalState.Pending)
        val toolCallId = pending.getTools().single().toolCallId
        val conversation = conversationOf(
            MessageNode(messages = listOf(pending, UIMessage.assistant("other")), selectIndex = 1)
        )

        assertNull(conversation.withToolApproval(toolCallId, ToolApprovalState.Approved))
    }

    @Test
    fun `interrupted tools on the last message get a cancelled result`() {
        val conversation = conversationOf(
            UIMessage.user("run it").toMessageNode(),
            toolReply(ToolApprovalState.Approved).toMessageNode(),
        )

        val tool = conversation.cancelInterruptedTools()!!.currentMessages.last().getTools().single()

        assertTrue(tool.isExecuted)
        assertTrue((tool.output.single() as UIMessagePart.Text).text.contains("cancelled"))
    }

    @Test
    fun `nothing to cancel returns null`() {
        val conversation = conversationOf(UIMessage.user("hi").toMessageNode(), UIMessage.assistant("done").toMessageNode())

        assertNull(conversation.cancelInterruptedTools())
        assertNull(Conversation.ofId(Uuid.random()).cancelInterruptedTools())
    }

    private fun conversationOf(vararg nodes: MessageNode) =
        Conversation.ofId(Uuid.random()).copy(messageNodes = nodes.toList())

    private fun toolReply(approvalState: ToolApprovalState) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Tool(
                toolCallId = Uuid.random().toString(),
                toolName = "workspace_shell",
                input = "{}",
                approvalState = approvalState,
            )
        ),
    )
}
