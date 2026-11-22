package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Text as TextIcon
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.wordCount
import me.rerere.rikkahub.utils.toFixed
import java.text.NumberFormat
import java.time.Duration
import kotlin.math.roundToInt

/**
 * 显示消息的技术统计信息（如 token 使用量），点击查看详情
 *
 * 多轮工具调用时 usage 是各步累计值，行内显示累计输入、输出、字数和耗时，最后一次请求的上下文占用放在详情里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current
    val usage = message.usage
    if (!settings.displaySetting.showTokenUsage || usage == null) return

    val stats = remember(message) { message.nerdStats(usage) }
    // 消息里的模型快照不带上下文窗口，只能从设置里按 ID 找；模型被删了就不显示窗口大小
    val contextLength = remember(settings.providers, message.modelId) {
        settings.findModelById(message.modelId)?.contextLength?.takeIf { it > 0 }
    }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            UsagePopoverCard(
                stats = stats,
                contextLength = contextLength,
            )
        },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
            CompositionLocalProvider(LocalContentColor provides color) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                if (tooltipState.isVisible) {
                                    tooltipState.dismiss()
                                } else {
                                    tooltipState.show()
                                }
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    StatsItem(
                        icon = { StatsIcon(HugeIcons.Upload02, stringResource(R.string.chat_message_usage_input)) },
                        content = {
                            val promptTokens = stats.total.promptTokens
                            val cacheRatePct = if (promptTokens > 0) {
                                ((stats.total.cachedTokens.toDouble() / promptTokens) * 100)
                                    .roundToInt()
                                    .coerceIn(0, 100)
                            } else {
                                null
                            }
                            Text(text = "${promptTokens.formatNumber()} tokens")
                            if (cacheRatePct != null) {
                                Text(text = " (${stringResource(R.string.chat_message_usage_cached)} $cacheRatePct%)")
                            }
                        }
                    )
                    StatsItem(
                        icon = { StatsIcon(HugeIcons.Download04, stringResource(R.string.chat_message_usage_output)) },
                        content = { Text(text = "${stats.total.completionTokens.formatNumber()} tokens") }
                    )
                    if (stats.charCount > 0) {
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.TextIcon, "Characters") },
                            content = { Text(text = "${stats.charCount}字") }
                        )
                    }
                    if (stats.durationMillis != null) {
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.Clock02, stringResource(R.string.chat_message_usage_duration)) },
                            content = { Text(text = "${(stats.durationMillis / 1000f).toFixed(1)}s") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 极简编辑风用量浮窗容器：圆角卡片、发丝边框、零 tonalElevation、预留外边距避免阴影被 Popup 裁剪。
 */
@Composable
private fun UsagePopoverCard(
    stats: NerdStats,
    contextLength: Int?,
) {
    val isDark = LocalDarkMode.current
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isDark) colors.surfaceContainerHigh else colors.surfaceBright
    val borderColor = colors.outlineVariant.copy(alpha = if (isDark) 0.45f else 0.65f)

    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = if (isDark) 6.dp else 10.dp,
            border = BorderStroke(0.5.dp, borderColor),
            modifier = Modifier.width(284.dp),
        ) {
            UsageDetails(
                stats = stats,
                contextLength = contextLength,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun UsageDetails(
    stats: NerdStats,
    contextLength: Int?,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkMode.current
    val colors = MaterialTheme.colorScheme
    val extendColors = MaterialTheme.extendColors
    val total = stats.total
    val lastUsage = stats.lastUsage
    val contextTokens = lastUsage.promptTokens + lastUsage.completionTokens
    val hasContextSection = contextTokens > 0 || contextLength != null
    val lastCachePct = if (lastUsage.promptTokens > 0 && lastUsage.cachedTokens > 0) {
        ((lastUsage.cachedTokens.toDouble() / lastUsage.promptTokens) * 100)
            .roundToInt()
            .coerceIn(0, 100)
    } else {
        null
    }

    val metricRows = buildList {
        if (stats.steps > 1) {
            add(stringResource(R.string.chat_message_usage_step_count) to stats.steps.toString())
            add(stringResource(R.string.chat_message_usage_total_input) to total.promptTokens.exact())
            if (total.cachedTokens > 0) {
                val totalCachePct = if (total.promptTokens > 0) {
                    ((total.cachedTokens.toDouble() / total.promptTokens) * 100)
                        .roundToInt()
                        .coerceIn(0, 100)
                } else {
                    0
                }
                add(
                    stringResource(R.string.chat_message_usage_total_cached) to
                        "${total.cachedTokens.exact()} ($totalCachePct%)",
                )
            }
            add(stringResource(R.string.chat_message_usage_total_output) to total.completionTokens.exact())
        }
        stats.tps?.let { tps ->
            add(stringResource(R.string.chat_message_usage_speed) to "${tps.toFixed(1)} tok/s")
        }
        stats.durationMillis?.let { durationMs ->
            add(stringResource(R.string.chat_message_usage_duration) to "${(durationMs / 1000f).toFixed(1)}s")
        }
        // [FORK] 回答字数
        if (stats.charCount > 0) {
            add("字数" to "${stats.charCount.exact()} 字")
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.chat_message_usage_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            when {
                stats.steps > 1 -> UsageBadge(
                    text = stringResource(R.string.chat_message_usage_steps, stats.steps),
                    containerColor = colors.surfaceContainerHighest.copy(alpha = 0.7f),
                    contentColor = colors.onSurfaceVariant,
                )
                lastCachePct != null && lastCachePct > 0 -> UsageBadge(
                    text = "${stringResource(R.string.chat_message_usage_cached)} $lastCachePct%",
                    containerColor = if (isDark) extendColors.green2.copy(alpha = 0.5f) else extendColors.green1,
                    contentColor = extendColors.green8,
                )
            }
        }

        if (hasContextSection) {
            ContextUsageBar(
                lastUsage = lastUsage,
                contextLength = contextLength,
            )
        }

        if (hasContextSection && metricRows.isNotEmpty()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.outlineVariant.copy(alpha = if (isDark) 0.45f else 0.65f),
            )
        }

        if (metricRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                metricRows.forEach { (label, value) ->
                    UsageDetailRow(label = label, value = value)
                }
            }
        }
    }
}

@Composable
private fun UsageBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private data class UsageSegment(val label: String, val tokens: Int, val color: Color)

/**
 * 最后一次请求的上下文占用：缓存命中、未命中的输入、输出依次排开，剩下的是窗口里的空余
 */
@Composable
private fun ContextUsageBar(
    lastUsage: TokenUsage,
    contextLength: Int?,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkMode.current
    val colorScheme = MaterialTheme.colorScheme
    val extendColors = MaterialTheme.extendColors
    val segments = listOf(
        UsageSegment(stringResource(R.string.chat_message_usage_cached), lastUsage.cachedTokens, extendColors.green6),
        UsageSegment(
            stringResource(R.string.chat_message_usage_input),
            (lastUsage.promptTokens - lastUsage.cachedTokens).coerceAtLeast(0),
            extendColors.blue6,
        ),
        UsageSegment(stringResource(R.string.chat_message_usage_output), lastUsage.completionTokens, extendColors.orange5),
    ).filter { it.tokens > 0 }
    val contextTokens = lastUsage.promptTokens + lastUsage.completionTokens
    // 窗口未知或已经超出时，占用本身铺满整条
    val capacity = maxOf(contextLength ?: 0, contextTokens, 1)
    val hasFreeSpace = capacity > contextTokens
    val trackColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.12f else 0.08f)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.chat_message_usage_context),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (contextLength != null) {
                val fraction = contextTokens.toFloat() / contextLength
                Text(
                    text = "${contextTokens.formatNumber()} / ${contextLength.formatNumber()} · ${(fraction * 100).toFixed(0)}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = if (fraction >= 0.9f) colorScheme.error else colorScheme.onSurface,
                )
            } else {
                Text(
                    text = contextTokens.exact(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            val outerRadius = CornerRadius(size.height / 2)
            val trackPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = outerRadius,
                    )
                )
            }
            drawPath(trackPath, color = trackColor)

            if (segments.isEmpty()) return@Canvas

            val gap = 1.5.dp.toPx()
            val minWidth = 4.dp.toPx()
            val totalGaps = if (segments.size > 1) gap * (segments.size - 1) else 0f
            val available = (size.width - totalGaps).coerceAtLeast(0f)
            val segmentRadius = CornerRadius(2.dp.toPx())

            clipPath(trackPath) {
                var x = 0f
                segments.forEachIndexed { index, segment ->
                    if (x >= size.width) return@forEachIndexed
                    val remaining = (size.width - x).coerceAtLeast(0f)
                    val rawWidth = segment.tokens.toFloat() / capacity * available
                    val width = if (index == segments.lastIndex && !hasFreeSpace) {
                        remaining
                    } else {
                        rawWidth.coerceAtLeast(minWidth).coerceAtMost(remaining)
                    }
                    drawRoundRect(
                        color = segment.color,
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                        cornerRadius = segmentRadius,
                    )
                    x += width + gap
                }
            }
        }
        if (segments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                segments.forEach { segment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(segment.color, CircleShape)
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                    append(segment.label)
                                    append(" ")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                                ) {
                                    append(segment.tokens.exact())
                                }
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDetailRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun StatsIcon(imageVector: ImageVector, contentDescription: String) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(12.dp)
    )
}

@Composable
fun StatsItem(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}

private data class NerdStats(
    val total: TokenUsage,
    val lastUsage: TokenUsage,
    val steps: Int,
    val durationMillis: Long?,
    val tps: Float?,
    // [FORK] 回答字数
    val charCount: Int,
)

private fun UIMessage.nerdStats(usage: TokenUsage): NerdStats {
    val last = lastRequestUsage() ?: usage
    // 旧消息没有记录生成耗时，退回用创建到完成的总时长估算
    val durationMillis = finishedAt?.let { finishedAt ->
        generationTimeMs ?: Duration.between(
            createdAt.toJavaLocalDateTime(),
            finishedAt.toJavaLocalDateTime()
        ).toMillis()
    }?.takeIf { it > 0 }
    return NerdStats(
        total = usage,
        lastUsage = last,
        steps = stepCount ?: 1,
        durationMillis = durationMillis,
        tps = durationMillis?.let { usage.completionTokens.toFloat() / it * 1000 },
        charCount = toText().wordCount(),
    )
}

private fun Int.exact(): String = NumberFormat.getIntegerInstance().format(this)
