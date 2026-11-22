package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * 缓存感知上下文限制策略
 */
enum class ContextLimitStrategy {
    /** 按估算 Token 总量进行双水位滞回截断 */
    TOKENS,
    /** 按消息条数进行双水位滞回截断 */
    MESSAGES,
}

/**
 * 缓存优先上下文裁剪参数
 *
 * @property strategy 裁剪计量模式（按 Token 或按消息条数）
 * @property maxTokens Token 模式上限，<= 0 表示不限制（但已建立的 [anchorMessageId] 仍保持不变以延续缓存）
 * @property maxMessages 条数模式上限，<= 0 表示不限制（同上）
 * @property keepRatio 越过上限后回落保留的比例（如 0.35 / 0.50 / 0.70）
 * @property minTokensFloor Token 模式下触发回落时的最低保留 Token 下限，保证高于主流模型隐式缓存门槛（如 4096）
 * @property anchorMessageId 当前生效的普通历史单调裁剪起点；null 表示从头发送
 * @property protectedMessageIds 固定到上下文的消息 ID，放置在滚动历史之前以隔离前缀缓存
 * @property fixedOverheadTokens 系统提示词与工具定义等固定头部开销（估算 token）
 * @property tokenCounter 单条消息 token 估算函数
 */
data class ContextLimitSpec(
    val strategy: ContextLimitStrategy = ContextLimitStrategy.MESSAGES,
    val maxTokens: Int = 0,
    val maxMessages: Int = 0,
    val keepRatio: Float = 0.5f,
    val minTokensFloor: Int = DEFAULT_MIN_CACHE_TOKENS_FLOOR,
    val anchorMessageId: Uuid? = null,
    val protectedMessageIds: Set<Uuid> = emptySet(),
    val fixedOverheadTokens: Int = 0,
    val tokenCounter: (UIMessage) -> Int = ::estimateMessageTokens,
)

/**
 * [limitContextForCache] 的输出详情，同时服务于请求组装与 UI 实时缓存预估。
 */
data class ContextLimitResult(
    val messages: List<UIMessage>,
    val nextAnchorMessageId: Uuid?,
    val activeNormalStartMessageId: Uuid?,
    val activeNormalStartIndex: Int,
    val normalMessageCount: Int,
    val protectedMessageCount: Int,
    val estimatedNormalTokens: Int,
    val estimatedProtectedTokens: Int,
    val estimatedMessageTokens: Int,
    val estimatedTotalTokens: Int,
    val targetTokensAfterTruncate: Int,
    val didAdvanceAnchor: Boolean,
    val hasTruncatedHistory: Boolean,
)

/**
 * 默认最低回落 Token 底线：
 * Gemini 2.5 隐式缓存门槛为 2,048 tokens，Gemini 3 / 4 为 4,096+ tokens；
 * 回落时保底约 8,192 tokens，既避免落在门槛下白交全价，又留出足够步幅。
 */
const val DEFAULT_MIN_CACHE_TOKENS_FLOOR = 8_192

/**
 * 轻量估算纯文本 token 数（ASCII 约 0.30 token/字符，CJK 等非 ASCII 约 1.15 token/字符）。
 */
fun estimateTextTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var ascii = 0
    var nonAscii = 0
    for (i in 0 until text.length) {
        if (text[i].code < 128) ascii++ else nonAscii++
    }
    val raw = ascii * 0.30 + nonAscii * 1.15
    return raw.roundToInt().coerceAtLeast(1)
}

/**
 * 轻量估算单条 [UIMessage] 占用的 token 数。
 */
@Suppress("DEPRECATION")
fun estimateMessageTokens(message: UIMessage): Int {
    var total = 4
    message.parts.forEach { part ->
        total += when (part) {
            is UIMessagePart.Text -> estimateTextTokens(part.text)
            is UIMessagePart.Reasoning -> estimateTextTokens(part.reasoning)
            is UIMessagePart.Image -> 512
            is UIMessagePart.Video -> 2_000
            is UIMessagePart.Audio -> 600
            is UIMessagePart.Document -> 1_500
            is UIMessagePart.Tool -> {
                val outTokens = part.output.sumOf { sub ->
                    when (sub) {
                        is UIMessagePart.Text -> estimateTextTokens(sub.text)
                        is UIMessagePart.Image -> 512
                        is UIMessagePart.Video -> 2_000
                        is UIMessagePart.Audio -> 600
                        is UIMessagePart.Document -> 1_500
                        else -> 16
                    }
                }
                estimateTextTokens(part.toolName) + estimateTextTokens(part.input) + outTokens + 20
            }

            is UIMessagePart.ServerTool -> {
                estimateTextTokens(part.toolName) +
                    estimateTextTokens(part.input?.toString().orEmpty()) +
                    estimateTextTokens(part.output?.toString().orEmpty()) + 20
            }

            is UIMessagePart.ToolCall -> estimateTextTokens(part.toolName) + estimateTextTokens(part.arguments) + 12
            is UIMessagePart.ToolResult -> estimateTextTokens(part.toolName) + estimateTextTokens(part.content.toString()) + 12
            is UIMessagePart.Search -> 8
            is UIMessagePart.Notice -> estimateTextTokens(part.text) + 8
        }
    }
    return total
}

/**
 * 估算双水位滞回下的稳态隐式缓存命中率（理想情况，假定服务端缓存稳定命中）。
 *
 * 每个周期：回落后首轮整段重建（约 [low] 未命中），之后每轮只有新增 [delta] 未命中，
 * 共 N = (high - low) / delta 轮，平均上下文 (high + low) / 2：
 * hit = 1 - 2 * high / (N * (high + low))。
 * 单位可以是 token 也可以是条数，只要 [high] / [low] / [delta] 一致。
 */
fun estimateSteadyStateHitRate(high: Double, low: Double, delta: Double): Double {
    if (high <= 0.0 || delta <= 0.0 || low <= 0.0 || high <= low) return 0.0
    val turns = ((high - low) / delta).coerceAtLeast(1.0)
    val hit = 1.0 - 2.0 * high / (turns * (high + low))
    return hit.coerceIn(0.0, 0.98)
}

/**
 * 计算 Token 模式下触发截断后的回落目标总水位 L：
 * 既按 [keepRatio] 回落以拉长缓存周期，又守住隐式缓存最低门槛下限。
 */
fun computeTokenLowWatermark(
    maxTokens: Int,
    keepRatio: Float,
    minTokensFloor: Int = DEFAULT_MIN_CACHE_TOKENS_FLOOR,
): Int {
    if (maxTokens <= 0) return 0
    val clampedRatio = keepRatio.coerceIn(0.15f, 0.85f)
    val rawTarget = (maxTokens * clampedRatio).roundToInt()
    val safeUpper = (maxTokens * 0.85f).roundToInt().coerceAtLeast(1)
    val safeFloor = minOf(minTokensFloor, (maxTokens * 0.65f).roundToInt())
        .coerceAtLeast(minOf(4_500, maxTokens))
        .coerceAtMost(safeUpper)
    return rawTarget.coerceIn(safeFloor, safeUpper)
}

/**
 * 以缓存优先的双水位滞回 + 单调锚点策略截取发送给模型的上下文：
 *
 * 1. **前缀隔离**：固定消息（[ContextLimitSpec.protectedMessageIds]）置于检查点之后、普通滚动历史之前，
 *    避免普通历史跳变时连带打断固定区前缀，也避免固定消息变更扰乱普通历史起点。
 * 2. **单调锚点**：当已有 [ContextLimitSpec.anchorMessageId] 且未超出上限时，**绝不后退**（即使把滑块调大或改为“不限”），
 *    从而延续已经建立的服务端 KV 前缀缓存。
 * 3. **双水位回落**：仅当当前活跃内容越过上限时，才把起点推进到回落水位且对齐到完整的 User/Tool 边界。
 */
fun List<UIMessage>.limitContextForCache(spec: ContextLimitSpec): ContextLimitResult {
    val checkpointIndex = indexOfLast { it.isContextCheckpoint }
    val checkpointMsg = if (checkpointIndex >= 0) this[checkpointIndex] else null
    val tail = if (checkpointIndex >= 0) subList(checkpointIndex + 1, size) else this

    val protectedMsgs = if (spec.protectedMessageIds.isEmpty()) {
        emptyList()
    } else {
        tail.filter { it.id in spec.protectedMessageIds }
    }
    val normalMsgs = if (spec.protectedMessageIds.isEmpty()) {
        tail
    } else {
        tail.filter { it.id !in spec.protectedMessageIds }
    }

    val tokenCounter = spec.tokenCounter
    val checkpointTokens = checkpointMsg?.let(tokenCounter) ?: 0
    val protectedTokens = protectedMsgs.sumOf(tokenCounter)
    val overheadTokens = spec.fixedOverheadTokens + checkpointTokens + protectedTokens

    val targetTotalTokens = if (spec.strategy == ContextLimitStrategy.TOKENS && spec.maxTokens > 0) {
        computeTokenLowWatermark(spec.maxTokens, spec.keepRatio, spec.minTokensFloor)
    } else {
        0
    }

    if (normalMsgs.isEmpty()) {
        val out = buildList {
            checkpointMsg?.let(::add)
            addAll(protectedMsgs)
        }
        return ContextLimitResult(
            messages = out,
            nextAnchorMessageId = null,
            activeNormalStartMessageId = null,
            activeNormalStartIndex = 0,
            normalMessageCount = 0,
            protectedMessageCount = protectedMsgs.size,
            estimatedNormalTokens = 0,
            estimatedProtectedTokens = protectedTokens,
            estimatedMessageTokens = checkpointTokens + protectedTokens,
            estimatedTotalTokens = overheadTokens,
            targetTokensAfterTruncate = targetTotalTokens,
            didAdvanceAnchor = spec.anchorMessageId != null,
            hasTruncatedHistory = false,
        )
    }

    val normalTokens = IntArray(normalMsgs.size) { index -> tokenCounter(normalMsgs[index]) }

    // 1. 解析并校验已有单调锚点（只可能向右推进，缺失时回退到 0）
    val resolvedCurrentStart = spec.anchorMessageId?.let { anchorId ->
        val rawIndex = normalMsgs.indexOfFirst { it.id == anchorId }
        if (rawIndex >= 0) {
            alignToolDependencyBoundary(normalMsgs, rawIndex, minIndex = 0)
        } else {
            0
        }
    } ?: 0

    // 2. 判断当前窗口是否越过触发上限，只有越过时才推进锚点
    val finalStartIndex = when (spec.strategy) {
        ContextLimitStrategy.TOKENS -> {
            if (spec.maxTokens <= 0) {
                resolvedCurrentStart
            } else {
                var activeNormalTokens = 0
                for (i in resolvedCurrentStart until normalMsgs.size) {
                    activeNormalTokens += normalTokens[i]
                }
                val activeTotalTokens = overheadTokens + activeNormalTokens
                if (activeTotalTokens <= spec.maxTokens) {
                    resolvedCurrentStart
                } else {
                    val targetNormalTokens = (targetTotalTokens - overheadTokens).coerceAtLeast(1)
                    val remainingMaxBudget = (spec.maxTokens - overheadTokens).coerceAtLeast(1)
                    var acc = 0
                    var candidate = normalMsgs.lastIndex
                    for (i in normalMsgs.lastIndex downTo resolvedCurrentStart) {
                        val next = acc + normalTokens[i]
                        if (i < normalMsgs.lastIndex && next > targetNormalTokens) {
                            candidate = i + 1
                            break
                        }
                        acc = next
                        candidate = i
                    }
                    if (candidate <= resolvedCurrentStart && resolvedCurrentStart < normalMsgs.lastIndex) {
                        candidate = resolvedCurrentStart + 1
                    }
                    alignTurnStartForCache(
                        messages = normalMsgs,
                        candidateIndex = candidate.coerceIn(resolvedCurrentStart, normalMsgs.lastIndex),
                        minIndex = resolvedCurrentStart,
                        maxTokensBudget = remainingMaxBudget,
                        normalTokens = normalTokens,
                    )
                }
            }
        }

        ContextLimitStrategy.MESSAGES -> {
            if (spec.maxMessages <= 0) {
                resolvedCurrentStart
            } else {
                val activeCount = protectedMsgs.size + (normalMsgs.size - resolvedCurrentStart)
                if (activeCount <= spec.maxMessages) {
                    resolvedCurrentStart
                } else {
                    val remainingBudget = (spec.maxMessages - protectedMsgs.size).coerceAtLeast(1)
                    val clampedRatio = spec.keepRatio.coerceIn(0.15f, 0.85f)
                    val rawTargetCount = (remainingBudget * clampedRatio).roundToInt().coerceIn(1, remainingBudget)
                    val targetCount = if (remainingBudget >= 2 && rawTargetCount == remainingBudget) {
                        remainingBudget - 1
                    } else {
                        rawTargetCount
                    }
                    val candidate = (normalMsgs.size - targetCount).coerceIn(resolvedCurrentStart, normalMsgs.lastIndex)
                    alignTurnStartForCache(
                        messages = normalMsgs,
                        candidateIndex = candidate,
                        minIndex = resolvedCurrentStart,
                        maxMessageBudget = remainingBudget,
                    )
                }
            }
        }
    }.coerceIn(0, normalMsgs.lastIndex)

    var keptNormalTokens = 0
    for (i in finalStartIndex until normalMsgs.size) {
        keptNormalTokens += normalTokens[i]
    }
    val nextAnchor = if (finalStartIndex > 0) normalMsgs[finalStartIndex].id else null
    val outputMessages = buildList {
        checkpointMsg?.let(::add)
        addAll(protectedMsgs)
        addAll(normalMsgs.subList(finalStartIndex, normalMsgs.size))
    }

    return ContextLimitResult(
        messages = outputMessages,
        nextAnchorMessageId = nextAnchor,
        activeNormalStartMessageId = normalMsgs[finalStartIndex].id,
        activeNormalStartIndex = finalStartIndex,
        normalMessageCount = normalMsgs.size - finalStartIndex,
        protectedMessageCount = protectedMsgs.size,
        estimatedNormalTokens = keptNormalTokens,
        estimatedProtectedTokens = protectedTokens,
        estimatedMessageTokens = checkpointTokens + protectedTokens + keptNormalTokens,
        estimatedTotalTokens = overheadTokens + keptNormalTokens,
        targetTokensAfterTruncate = targetTotalTokens,
        didAdvanceAnchor = nextAnchor != spec.anchorMessageId &&
            (spec.anchorMessageId == null || finalStartIndex > resolvedCurrentStart),
        hasTruncatedHistory = finalStartIndex > 0,
    )
}

/**
 * 先回退避开 tool call/result 分裂边界，再优先对齐到 User 提问起点，
 * 避免保留下来的滚动历史从孤立的 Assistant 回复开始。
 */
private fun alignTurnStartForCache(
    messages: List<UIMessage>,
    candidateIndex: Int,
    minIndex: Int,
    maxTokensBudget: Int? = null,
    normalTokens: IntArray? = null,
    maxMessageBudget: Int? = null,
): Int {
    val toolAligned = alignToolDependencyBoundary(messages, candidateIndex, minIndex)
    if (messages[toolAligned].role == MessageRole.USER) return toolAligned

    fun fitsBudget(startIdx: Int): Boolean {
        if (maxMessageBudget != null && messages.size - startIdx > maxMessageBudget) {
            return false
        }
        if (maxTokensBudget != null && normalTokens != null) {
            var sum = 0
            for (i in startIdx until messages.size) {
                sum += normalTokens[i]
                if (sum > maxTokensBudget) return false
            }
        }
        return true
    }

    // 尝试向左回退到同一轮的 USER 提问，前提是不超过触发上限且不跨过 minIndex
    var leftUser = -1
    for (i in toolAligned - 1 downTo minIndex) {
        if (messages[i].role == MessageRole.USER) {
            leftUser = i
            break
        }
    }
    if (leftUser >= minIndex && fitsBudget(leftUser)) {
        return alignToolDependencyBoundary(messages, leftUser, minIndex)
    }

    // 若回退整轮会超过上限，尝试向前对齐到下一个 USER 提问起点
    for (i in toolAligned + 1 until messages.size) {
        if (messages[i].role == MessageRole.USER) {
            return i
        }
    }

    return toolAligned
}

private fun alignToolDependencyBoundary(
    messages: List<UIMessage>,
    startIndex: Int,
    minIndex: Int,
): Int {
    var adjusted = startIndex.coerceIn(minIndex, messages.lastIndex)
    val visited = mutableSetOf<Int>()
    while (adjusted > minIndex) {
        if (!visited.add(adjusted)) break
        val current = messages[adjusted]
        var moved = false

        if (current.getTools().any { it.isExecuted }) {
            for (i in adjusted - 1 downTo minIndex) {
                if (messages[i].getTools().any { !it.isExecuted }) {
                    adjusted = i
                    moved = true
                    break
                }
            }
        }

        if (!moved && current.getTools().any { !it.isExecuted }) {
            for (i in adjusted - 1 downTo minIndex) {
                if (messages[i].role == MessageRole.USER) {
                    adjusted = i
                    moved = true
                    break
                }
            }
        }

        if (!moved) break
    }
    return adjusted
}
