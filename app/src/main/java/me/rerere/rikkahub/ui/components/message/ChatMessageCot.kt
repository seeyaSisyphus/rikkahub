package me.rerere.rikkahub.ui.components.message

import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.ai.ui.UIMessagePart

/**
 * 思考步骤类型，用于分组 Reasoning、客户端 Tool 和 ServerTool
 */
sealed interface ThinkingStep {
    data class ReasoningStep(
        val reasoning: UIMessagePart.Reasoning,
    ) : ThinkingStep

    data class ToolStep(
        val tool: UIMessagePart.Tool,
    ) : ThinkingStep

    data class ServerToolStep(
        val tool: UIMessagePart.ServerTool,
    ) : ThinkingStep
}

/**
 * 消息部分块类型，用于保持渲染顺序
 */
sealed interface MessagePartBlock {
    data class ThinkingBlock(val steps: List<ThinkingStep>) : MessagePartBlock
    data class ContentBlock(val part: UIMessagePart, val index: Int) : MessagePartBlock
}

/**
 * 将 parts 分组成 ThinkingBlock 和 ContentBlock
 * 连续的 Reasoning、客户端 Tool 和 ServerTool 会被分组到一个 ThinkingBlock 中
 *
 * @param attachmentsFirst 是否把附件（图片/视频/音频/文档）排在文字前面。
 * 用户消息开启后，图片等附件会显示在文字上方；助手消息保持原始顺序，
 * 以便正文与生成内容按模型返回的顺序展示。
 */
fun List<UIMessagePart>.groupMessageParts(
    attachmentsFirst: Boolean = false,
): List<MessagePartBlock> {
    // 强制：将所有 Reasoning/Tool parts 提取到最前面，绝不让思考过程出现在正文中
    val reasoningParts = mutableListOf<UIMessagePart.Reasoning>()
    val toolSteps = mutableListOf<ThinkingStep.ToolStep>()
    val serverToolSteps = mutableListOf<ThinkingStep.ServerToolStep>()
    val contentBlocks = mutableListOf<MessagePartBlock.ContentBlock>()

    this.fastForEachIndexed { index, part ->
        when (part) {
            is UIMessagePart.Reasoning -> {
                reasoningParts.add(part)
            }

            is UIMessagePart.Tool -> {
                toolSteps.add(ThinkingStep.ToolStep(part))
            }

            is UIMessagePart.ServerTool -> {
                serverToolSteps.add(ThinkingStep.ServerToolStep(part))
            }

            else -> {
                contentBlocks.add(MessagePartBlock.ContentBlock(part, index))
            }
        }
    }

    // 将所有碎片化的 Reasoning 合并为唯一一段，避免顶部出现多个思考气泡
    val mergedThinkingSteps = mutableListOf<ThinkingStep>()
    if (reasoningParts.isNotEmpty()) {
        val merged = UIMessagePart.Reasoning(
            reasoning = reasoningParts.joinToString("") { it.reasoning },
            createdAt = reasoningParts.first().createdAt,
            finishedAt = reasoningParts.lastOrNull { it.finishedAt != null }?.finishedAt
                ?: reasoningParts.last().finishedAt,
        )
        mergedThinkingSteps.add(ThinkingStep.ReasoningStep(merged))
    }
    mergedThinkingSteps.addAll(toolSteps)
    mergedThinkingSteps.addAll(serverToolSteps)

    // 用户消息按附件在上、文字在下展示，与输入框里的顺序保持一致；
    // 助手消息保留原始顺序，正文与生成内容按模型返回的顺序展示。
    // sortedBy 是稳定排序，同类内容之间的相对顺序保持不变。
    val orderedContentBlocks: List<MessagePartBlock> = if (attachmentsFirst) {
        contentBlocks.sortedBy { if (it.part is UIMessagePart.Text) 1 else 0 }
    } else {
        contentBlocks
    }

    // 思考过程始终在最前面
    return if (mergedThinkingSteps.isNotEmpty()) {
        listOf(MessagePartBlock.ThinkingBlock(mergedThinkingSteps)) + orderedContentBlocks
    } else {
        orderedContentBlocks
    }
}
