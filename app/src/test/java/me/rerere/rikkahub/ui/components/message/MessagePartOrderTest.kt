package me.rerere.rikkahub.ui.components.message

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户消息里的附件（图片/视频/音频/文档）必须显示在文字上方。
 *
 * 历史上“新发送”分支把文字放在 parts 最前面，导致图片渲染到文字下面；
 * 这里锁定 [groupMessageParts] 的排序行为，避免再次回归。
 */
class MessagePartOrderTest {

    private fun contentParts(parts: List<UIMessagePart>, attachmentsFirst: Boolean): List<UIMessagePart> =
        parts.groupMessageParts(attachmentsFirst = attachmentsFirst)
            .filterIsInstance<MessagePartBlock.ContentBlock>()
            .map { it.part }

    @Test
    fun `user message shows attachments above text regardless of stored order`() {
        val image = UIMessagePart.Image("file:///tmp/a.png")
        val text = UIMessagePart.Text("hello")

        // 文字在前（当前存储顺序）
        assertEquals(
            listOf(image, text),
            contentParts(listOf(text, image), attachmentsFirst = true),
        )

        // 附件在前（旧版本编辑分支写下的顺序）
        assertEquals(
            listOf(image, text),
            contentParts(listOf(image, text), attachmentsFirst = true),
        )
    }

    @Test
    fun `multiple attachments keep their relative order and stay above text`() {
        val image1 = UIMessagePart.Image("file:///tmp/1.png")
        val image2 = UIMessagePart.Image("file:///tmp/2.png")
        val text = UIMessagePart.Text("hi")

        assertEquals(
            listOf(image1, image2, text),
            contentParts(listOf(text, image1, image2), attachmentsFirst = true),
        )
    }

    @Test
    fun `assistant message keeps original part order`() {
        val text = UIMessagePart.Text("answer")
        val image = UIMessagePart.Image("file:///tmp/a.png")

        assertEquals(
            listOf(text, image),
            contentParts(listOf(text, image), attachmentsFirst = false),
        )
        assertEquals(
            listOf(image, text),
            contentParts(listOf(image, text), attachmentsFirst = false),
        )
    }

    @Test
    fun `reasoning stays at the top and attachments come before text`() {
        val reasoning = UIMessagePart.Reasoning(reasoning = "thinking")
        val text = UIMessagePart.Text("hello")
        val image = UIMessagePart.Image("file:///tmp/a.png")

        val blocks = listOf(text, image, reasoning).groupMessageParts(attachmentsFirst = true)

        assertTrue(blocks.first() is MessagePartBlock.ThinkingBlock)
        assertEquals(
            listOf(image, text),
            blocks.filterIsInstance<MessagePartBlock.ContentBlock>().map { it.part },
        )
    }
}