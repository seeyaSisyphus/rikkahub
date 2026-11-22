package me.rerere.rikkahub.data.ai

// [FORK] 对话专属参数解析器
// 将 ConversationParams 与 Assistant 设置合并为最终生效值，
// 使 GenerationHandler 无需感知 ConversationParams 的存在，减少与 upstream 的冲突面。
import me.rerere.ai.ui.ContextLimitSpec
import me.rerere.ai.ui.ContextLimitStrategy
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.estimateMessageTokens
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextKeepPolicy
import me.rerere.rikkahub.data.model.ContextLimitMode
import me.rerere.rikkahub.data.model.ConversationParams
import me.rerere.rikkahub.data.model.SystemPromptMode
import kotlin.uuid.Uuid

/**
 * ConversationParams 与 Assistant 合并后的最终生效参数。
 * 由 [ConversationParams.resolveWith] 生成，传入请求组装与 UI 使用。
 */
data class ResolvedConversationParams(
    val temperature: Float?,
    val topP: Float?,
    val contextMessageSize: Int,
    val contextLimitMode: ContextLimitMode,
    val contextTokenLimit: Int,
    val contextKeepPolicy: ContextKeepPolicy,
    val systemPrompt: String,
) {
    val hasActiveContextLimit: Boolean
        get() = when (contextLimitMode) {
            ContextLimitMode.TOKENS -> contextTokenLimit > 0
            ContextLimitMode.MESSAGES -> contextMessageSize > 0
        }

    fun toContextLimitSpec(
        anchorMessageId: Uuid? = null,
        protectedMessageIds: Set<Uuid> = emptySet(),
        fixedOverheadTokens: Int = 0,
        tokenCounter: (UIMessage) -> Int = ::estimateMessageTokens,
    ): ContextLimitSpec = ContextLimitSpec(
        strategy = when (contextLimitMode) {
            ContextLimitMode.TOKENS -> ContextLimitStrategy.TOKENS
            ContextLimitMode.MESSAGES -> ContextLimitStrategy.MESSAGES
        },
        maxTokens = if (contextLimitMode == ContextLimitMode.TOKENS) contextTokenLimit else 0,
        maxMessages = if (contextLimitMode == ContextLimitMode.MESSAGES) contextMessageSize else 0,
        keepRatio = contextKeepPolicy.keepRatio,
        anchorMessageId = anchorMessageId,
        protectedMessageIds = protectedMessageIds,
        fixedOverheadTokens = fixedOverheadTokens,
        tokenCounter = tokenCounter,
    )
}

/**
 * 将对话专属参数与助手默认参数合并，对话参数优先级更高。
 * null 表示回退到助手设置。
 */
fun ConversationParams.resolveWith(assistant: Assistant): ResolvedConversationParams {
    val effectiveSystemPrompt: String = when {
        systemPrompt == null -> assistant.systemPrompt
        systemPromptMode == SystemPromptMode.OVERRIDE -> systemPrompt
        else -> buildString {
            if (assistant.systemPrompt.isNotBlank()) {
                append(assistant.systemPrompt)
                append("\n\n")
            }
            append(systemPrompt)
        }
    }
    val effectiveMode = when {
        contextLimitMode != null -> contextLimitMode
        contextMessageSize != null -> ContextLimitMode.MESSAGES
        else -> ContextLimitMode.MESSAGES
    }
    val effectiveMessageSize = if (effectiveMode == ContextLimitMode.MESSAGES) {
        contextMessageSize ?: assistant.contextMessageLimit
    } else {
        0
    }
    val effectiveTokenLimit = if (effectiveMode == ContextLimitMode.TOKENS) {
        contextTokenLimit ?: 0
    } else {
        0
    }
    return ResolvedConversationParams(
        temperature = temperature ?: assistant.temperature,
        topP = topP ?: assistant.topP,
        contextMessageSize = effectiveMessageSize,
        contextLimitMode = effectiveMode,
        contextTokenLimit = effectiveTokenLimit,
        contextKeepPolicy = contextKeepPolicy,
        systemPrompt = effectiveSystemPrompt,
    )
}
