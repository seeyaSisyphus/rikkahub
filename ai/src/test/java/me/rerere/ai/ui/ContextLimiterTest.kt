package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ContextLimiterTest {
    private fun msg(
        role: MessageRole,
        text: String,
        id: Uuid = Uuid.random(),
    ) = UIMessage(
        id = id,
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private fun turns(count: Int, charsPerMessage: Int = 400): List<UIMessage> = buildList {
        repeat(count) { i ->
            val payload = "a".repeat(charsPerMessage)
            add(msg(MessageRole.USER, "U$i $payload"))
            add(msg(MessageRole.ASSISTANT, "A$i $payload"))
        }
    }

    @Test
    fun `token mode keeps all messages while total stays within ceiling`() {
        val history = turns(6, charsPerMessage = 200) // ~60 tokens * 12 = ~720 tokens
        val result = history.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.TOKENS,
                maxTokens = 16_000,
                keepRatio = 0.5f,
            )
        )

        assertEquals(history, result.messages)
        assertNull(result.nextAnchorMessageId)
        assertFalse(result.didAdvanceAnchor)
        assertFalse(result.hasTruncatedHistory)
    }

    @Test
    fun `token mode truncates to low watermark above cache floor and stays stable within cycle`() {
        // 每条约 1,000 ASCII 字符 ≈ 304 tokens；20 轮 40 条 ≈ 12,160 tokens
        val history = turns(25, charsPerMessage = 1_000)
        // 加上 400 tokens 系统开销，总计 ~15,600 tokens；设上限 12,000, keepRatio=0.35 -> 回落底线保底 8,192（因 12k*0.65=7.8k，safeFloor=7.8k）
        val spec = ContextLimitSpec(
            strategy = ContextLimitStrategy.TOKENS,
            maxTokens = 12_000,
            keepRatio = 0.35f,
            minTokensFloor = 8_192,
            fixedOverheadTokens = 400,
        )

        val firstCut = history.limitContextForCache(spec)
        assertTrue(firstCut.hasTruncatedHistory)
        assertTrue(firstCut.didAdvanceAnchor)
        assertEquals(MessageRole.USER, firstCut.messages.first().role)
        assertTrue(firstCut.estimatedTotalTokens <= 12_000)
        assertTrue(firstCut.estimatedTotalTokens >= 4_096)

        // 在同一个周期内再追加 3 条新消息，只要没超 12,000，起点锚点必须纹丝不动
        val anchor = firstCut.nextAnchorMessageId!!
        val nextTurn = history + listOf(
            msg(MessageRole.USER, "follow up question"),
            msg(MessageRole.ASSISTANT, "short answer"),
        )
        val secondRun = nextTurn.limitContextForCache(spec.copy(anchorMessageId = anchor))
        assertEquals(anchor, secondRun.nextAnchorMessageId)
        assertEquals(firstCut.messages.first().id, secondRun.messages.first().id)
        assertFalse(secondRun.didAdvanceAnchor)
    }

    @Test
    fun `monotonic anchor does not rewind when raising limit or setting unlimited`() {
        val history = turns(20, charsPerMessage = 1_000)
        val cut = history.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.TOKENS,
                maxTokens = 8_000,
                keepRatio = 0.5f,
            )
        )
        val anchor = cut.nextAnchorMessageId!!
        assertTrue(cut.hasTruncatedHistory)

        // 把上限调大到 64K：锚点必须原地保持，绝不能把旧历史插回前缀
        val raised = history.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.TOKENS,
                maxTokens = 64_000,
                keepRatio = 0.5f,
                anchorMessageId = anchor,
            )
        )
        assertEquals(anchor, raised.nextAnchorMessageId)
        assertEquals(cut.messages, raised.messages)
        assertFalse(raised.didAdvanceAnchor)

        // 改为不限（0）：只要不显式重置锚点，仍保持当前起点以延续服务端缓存
        val unlimited = history.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.TOKENS,
                maxTokens = 0,
                anchorMessageId = anchor,
            )
        )
        assertEquals(anchor, unlimited.nextAnchorMessageId)
        assertEquals(cut.messages, unlimited.messages)
    }

    @Test
    fun `pinned messages are placed in stable prefix before rolling history`() {
        val history = turns(12, charsPerMessage = 500)
        val pinnedTurnUser = history[2]
        val pinnedTurnAssistant = history[3]
        val protectedIds = setOf(pinnedTurnUser.id, pinnedTurnAssistant.id)

        val first = history.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.MESSAGES,
                maxMessages = 10,
                keepRatio = 0.5f,
                protectedMessageIds = protectedIds,
            )
        )
        // 前两条必须是固定的 turn，之后才是滚动历史
        assertEquals(pinnedTurnUser.id, first.messages[0].id)
        assertEquals(pinnedTurnAssistant.id, first.messages[1].id)
        assertTrue(first.messages.size <= 10)
        assertEquals(MessageRole.USER, first.messages[2].role)

        // 继续追加消息触发下一次截断时，前 2 条固定前缀的位置和顺序完全不变
        val extended = history + turns(8, charsPerMessage = 500)
        val second = extended.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.MESSAGES,
                maxMessages = 10,
                keepRatio = 0.5f,
                anchorMessageId = first.nextAnchorMessageId,
                protectedMessageIds = protectedIds,
            )
        )
        assertEquals(pinnedTurnUser.id, second.messages[0].id)
        assertEquals(pinnedTurnAssistant.id, second.messages[1].id)
        assertTrue(second.didAdvanceAnchor)
    }

    @Test
    fun `missing anchor falls back to beginning safely`() {
        val history = turns(4, charsPerMessage = 100)
        val result = history.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.MESSAGES,
                maxMessages = 20,
                anchorMessageId = Uuid.random(),
            )
        )
        assertEquals(history, result.messages)
        assertNull(result.nextAnchorMessageId)
    }

    @Test
    fun `checkpoint isolates older history and ignores pre-checkpoint anchor`() {
        val oldTurns = turns(4, charsPerMessage = 100)
        val checkpoint = msg(MessageRole.USER, "summary").copy(isContextCheckpoint = true)
        val newTurns = turns(2, charsPerMessage = 100)
        val all = oldTurns + checkpoint + newTurns

        val result = all.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.MESSAGES,
                maxMessages = 20,
                anchorMessageId = oldTurns[2].id,
            )
        )
        assertEquals(listOf(checkpoint) + newTurns, result.messages)
        assertNull(result.nextAnchorMessageId)
    }

    @Test
    fun `aligns away from orphan tool call boundary`() {
        val u0 = msg(MessageRole.USER, "old query " + "x".repeat(400))
        val a0 = msg(MessageRole.ASSISTANT, "old reply " + "x".repeat(400))
        val u1 = msg(MessageRole.USER, "run tool")
        val a1Tool = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = "t1",
                    toolName = "search",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("result")),
                )
            ),
        )
        val u2 = msg(MessageRole.USER, "next")
        val a2 = msg(MessageRole.ASSISTANT, "done")
        val messages = listOf(u0, a0, u1, a1Tool, u2, a2)

        val result = messages.limitContextForCache(
            ContextLimitSpec(
                strategy = ContextLimitStrategy.MESSAGES,
                maxMessages = 5,
                keepRatio = 0.5f,
            )
        )

        // 不应从 a1Tool 孤立开始，应保持 u1 与 a1Tool 完整配对（或跳过整轮）
        assertEquals(MessageRole.USER, result.messages.first().role)
        assertTrue(result.messages.first().id == u1.id || result.messages.first().id == u2.id)
    }
}
