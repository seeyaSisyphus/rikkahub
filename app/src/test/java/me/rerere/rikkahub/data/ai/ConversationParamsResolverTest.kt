package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextKeepPolicy
import me.rerere.rikkahub.data.model.ContextLimitMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationParams
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.normalizeContextAnchorNodeId
import me.rerere.rikkahub.data.model.resolveContextAnchorMessageId
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationParamsResolverTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `legacy conversation params json deserializes with safe defaults`() {
        val decoded = json.decodeFromString<ConversationParams>("""{"contextMessageSize":40}""")
        assertEquals(40, decoded.contextMessageSize)
        assertNull(decoded.contextLimitMode)
        assertNull(decoded.contextTokenLimit)
        assertEquals(ContextKeepPolicy.BALANCED, decoded.contextKeepPolicy)
        assertNull(decoded.contextAnchorNodeId)

        val resolved = decoded.resolveWith(Assistant(contextMessageLimit = 0))
        assertEquals(ContextLimitMode.MESSAGES, resolved.contextLimitMode)
        assertEquals(40, resolved.contextMessageSize)
        assertTrue(resolved.hasActiveContextLimit)
    }

    @Test
    fun `token mode resolves token ceiling and ignores message limit`() {
        val params = ConversationParams(
            contextLimitMode = ContextLimitMode.TOKENS,
            contextTokenLimit = 64_000,
            contextKeepPolicy = ContextKeepPolicy.HIGH_CACHE,
        )
        val resolved = params.resolveWith(Assistant(contextMessageLimit = 25))
        assertEquals(ContextLimitMode.TOKENS, resolved.contextLimitMode)
        assertEquals(64_000, resolved.contextTokenLimit)
        assertEquals(0, resolved.contextMessageSize)
        assertEquals(ContextKeepPolicy.HIGH_CACHE, resolved.contextKeepPolicy)
        assertTrue(resolved.hasActiveContextLimit)
    }

    @Test
    fun `unlimited token mode reports no active context limit`() {
        val params = ConversationParams(
            contextLimitMode = ContextLimitMode.TOKENS,
            contextTokenLimit = 0,
        )
        val resolved = params.resolveWith(Assistant(contextMessageLimit = 30))
        assertEquals(ContextLimitMode.TOKENS, resolved.contextLimitMode)
        assertEquals(0, resolved.contextTokenLimit)
        assertFalse(resolved.hasActiveContextLimit)
    }

    @Test
    fun `anchor node resolution respects pinned branch and normalizes missing node`() {
        val branchA = UIMessage.assistant("branch A")
        val branchB = UIMessage.assistant("branch B")
        val pinnedNode = MessageNode(
            messages = listOf(branchA, branchB),
            selectIndex = 1,
            pinnedMessageId = branchA.id,
        )
        val userNode = UIMessage.user("hi").toMessageNode()
        val conv = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(userNode, pinnedNode),
            conversationParams = ConversationParams(contextAnchorNodeId = pinnedNode.id),
        )

        assertEquals(branchA.id, conv.resolveContextAnchorMessageId())
        assertEquals(pinnedNode.id, conv.normalizeContextAnchorNodeId().conversationParams.contextAnchorNodeId)

        val afterDelete = conv.copy(messageNodes = listOf(userNode)).normalizeContextAnchorNodeId()
        assertNull(afterDelete.conversationParams.contextAnchorNodeId)
    }
}
