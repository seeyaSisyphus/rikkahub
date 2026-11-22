package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.ContextLimitResult
import me.rerere.ai.ui.ContextLimitStrategy
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.estimateMessageTokens
import me.rerere.ai.ui.estimateTextTokens
import me.rerere.ai.ui.limitContextForCache
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextKeepPolicy
import me.rerere.rikkahub.data.model.ContextLimitMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationParams
import me.rerere.rikkahub.data.model.resolveContextAnchorMessageId
import me.rerere.rikkahub.service.collectProtectedMessageIds
import me.rerere.rikkahub.utils.formatNumber
import java.io.File
import java.net.URI
import kotlin.math.abs

/**
 * 对话专属上下文限制的预设档位与 Token 估算辅助工具
 */
object ContextLimitPresets {
    /** Token 上限离散档位（0 表示不限，放在最右端防止误滑到极小值） */
    val TOKEN_STOPS: List<Int> = listOf(
        16_000,
        24_000,
        32_000,
        48_000,
        64_000,
        80_000,
        96_000,
        128_000,
        0,
    )

    /** 消息条数离散档位（全部 >= 20，0 表示不限） */
    val MESSAGE_STOPS: List<Int> = listOf(
        20,
        30,
        40,
        60,
        80,
        100,
        140,
        200,
        300,
        512,
        0,
    )

    const val DEFAULT_TOKEN_LIMIT = 64_000
    const val DEFAULT_MESSAGE_LIMIT = 60

    fun closestTokenStopIndex(tokens: Int): Int {
        if (tokens <= 0) return TOKEN_STOPS.lastIndex
        val finite = TOKEN_STOPS.dropLast(1)
        return finite.indices.minByOrNull { abs(finite[it] - tokens) } ?: 4
    }

    fun closestMessageStopIndex(messages: Int): Int {
        if (messages <= 0) return MESSAGE_STOPS.lastIndex
        val finite = MESSAGE_STOPS.dropLast(1)
        return finite.indices.minByOrNull { abs(finite[it] - messages) } ?: 3
    }

    fun formatLimitValue(mode: ContextLimitMode, value: Int): String = when {
        value <= 0 -> "不限"
        mode == ContextLimitMode.TOKENS -> formatCompactTokens(value)
        else -> "$value 条"
    }

    fun formatCompactTokens(tokens: Int): String = tokens.formatNumber()

    fun policyLabel(policy: ContextKeepPolicy): String = when (policy) {
        ContextKeepPolicy.ULTRA_CACHE -> "极限缓存"
        ContextKeepPolicy.HIGH_CACHE -> "高缓存"
        ContextKeepPolicy.BALANCED -> "均衡"
        ContextKeepPolicy.HIGH_MEMORY -> "偏记忆"
    }
}

/**
 * 在 [estimateMessageTokens] 基础上补充本地文档体积估算，避免大 PDF/文本附件在 transform 前被严重低估。
 */
fun estimateAppMessageTokens(message: UIMessage): Int {
    val hasLocalDocs = message.parts.any {
        it is UIMessagePart.Document && it.url.startsWith("file://")
    }
    if (!hasLocalDocs) return estimateMessageTokens(message)

    var total = 4
    message.parts.forEach { part ->
        total += when (part) {
            is UIMessagePart.Document -> estimateDocumentTokens(part)
            else -> estimatePartTokensFallback(part)
        }
    }
    return total
}

@Suppress("DEPRECATION")
private fun estimatePartTokensFallback(part: UIMessagePart): Int = when (part) {
    is UIMessagePart.Text -> estimateTextTokens(part.text)
    is UIMessagePart.Reasoning -> estimateTextTokens(part.reasoning)
    is UIMessagePart.Image -> 512
    is UIMessagePart.Video -> 2_000
    is UIMessagePart.Audio -> 600
    is UIMessagePart.Document -> 1_500
    is UIMessagePart.Tool,
    is UIMessagePart.ServerTool,
    is UIMessagePart.ToolCall,
    is UIMessagePart.ToolResult,
    is UIMessagePart.Search,
    is UIMessagePart.Notice -> estimateMessageTokens(
        UIMessage(role = me.rerere.ai.core.MessageRole.ASSISTANT, parts = listOf(part))
    ) - 4
}

private fun estimateDocumentTokens(doc: UIMessagePart.Document): Int {
    if (!doc.url.startsWith("file://")) return 1_500
    val file = runCatching { File(URI(doc.url)) }.getOrNull() ?: return 1_500
    if (!file.exists() || !file.isFile) return 1_500
    val len = file.length()
    if (len <= 0L) return 300
    val isBinaryDoc = doc.mime == "application/pdf" ||
        doc.mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        doc.mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
        doc.mime == "application/epub+zip"
    val factor = if (isBinaryDoc) 0.20 else 0.45
    return (len * factor).toInt().coerceIn(500, 48_000)
}

fun estimateToolsTokens(tools: List<Tool>): Int {
    if (tools.isEmpty()) return 0
    return tools.sumOf { tool ->
        val schemaChars = when (val schema = tool.parameters()) {
            is InputSchema.Obj -> schema.properties.toString().length +
                (schema.required?.sumOf { it.length + 4 } ?: 0)

            null -> 0
        }
        estimateTextTokens(tool.name) +
            estimateTextTokens(tool.description) +
            (schemaChars * 0.30).toInt() +
            24
    }
}

fun estimateFixedOverheadTokens(systemText: String, tools: List<Tool>): Int =
    estimateTextTokens(systemText) + estimateToolsTokens(tools)

/**
 * UI 实时预览用：根据当前对话、助手配置和滑块预览参数计算截断/缓存状态，不触发实际磁盘扫描以外的重量级解析。
 */
fun Conversation.previewContextLimit(
    assistant: Assistant,
    previewParams: ConversationParams,
    settings: Settings,
    tokenCounter: (UIMessage) -> Int = ::estimateAppMessageTokens,
): ContextLimitResult {
    val resolved = previewParams.resolveWith(assistant)
    val effectiveSystem =
        if (previewParams.systemPrompt == null &&
            assistant.allowConversationSystemPrompt && !customSystemPrompt.isNullOrBlank()
        ) {
            customSystemPrompt
        } else {
            resolved.systemPrompt
        }
    val systemTokens = estimateTextTokens(effectiveSystem) +
        (if (assistant.enableMemory) 500 else 0) +
        (assistant.localTools.size * 180) +
        (assistant.mcpServers.size * 600) +
        (if (assistant.workspaceId != null) 1_200 else 0) +
        (assistant.enabledSkills.size * 250) +
        (if (assistant.enableWebSearch) 300 else 0)

    val anchorMessageId = resolveContextAnchorMessageId()
    val protectedIds = collectProtectedMessageIds()
    val spec = resolved.toContextLimitSpec(
        anchorMessageId = anchorMessageId,
        protectedMessageIds = protectedIds,
        fixedOverheadTokens = systemTokens,
        tokenCounter = tokenCounter,
    ).copy(
        // 确保 Preview 与 ResolvedConversationParams 口径完全一致
        strategy = when (resolved.contextLimitMode) {
            ContextLimitMode.TOKENS -> ContextLimitStrategy.TOKENS
            ContextLimitMode.MESSAGES -> ContextLimitStrategy.MESSAGES
        },
    )
    return contextMessages.limitContextForCache(spec)
}
