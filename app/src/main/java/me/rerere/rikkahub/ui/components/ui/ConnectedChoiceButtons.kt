package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private val SegmentTrackPadding = 4.dp
private val SegmentGap = 2.dp

@Composable
internal fun segmentedTrackColor(): Color = if (LocalDarkMode.current) {
    MaterialTheme.colorScheme.surfaceContainerLowest
} else {
    MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
internal fun segmentedPillColor(): Color = if (LocalDarkMode.current) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else {
    MaterialTheme.colorScheme.surfaceBright
}

// 滑动胶囊单选选择器（参考 cyberslots seg-track / seg-pill 设计）
@Composable
fun <T> ConnectedChoiceButtons(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // 保留参数签名兼容已有调用方；轨道背景使用主题自适应内凹色
    @Suppress("UNUSED_PARAMETER")
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    minHeight: Dp = 44.dp,
    content: @Composable (option: T, selected: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val trackColor = segmentedTrackColor()
    val pillColor = segmentedPillColor()
    val itemHeight = (minHeight - SegmentTrackPadding * 2).coerceAtLeast(34.dp)
    val selectedIndex = options.indexOfFirst { it == selected }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        shape = CircleShape,
        color = trackColor,
        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.55f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SegmentTrackPadding),
        ) {
            val count = options.size.coerceAtLeast(1)
            val totalGap = SegmentGap * (count - 1)
            val segmentWidth = ((maxWidth - totalGap) / count).coerceAtLeast(0.dp)
            val targetOffset = if (selectedIndex >= 0) (segmentWidth + SegmentGap) * selectedIndex else 0.dp
            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                if (selectedIndex >= 0) {
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffset.roundToPx(), 0) }
                            .width(segmentWidth)
                            .fillMaxHeight(),
                        shape = CircleShape,
                        color = pillColor,
                        shadowElevation = 2.dp,
                        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f)),
                        content = {},
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SegmentGap),
                ) {
                    options.fastForEachIndexed { index, option ->
                        val isSelected = index == selectedIndex
                        val contentColor = when {
                            !enabled -> colors.onSurface.copy(alpha = 0.38f)
                            isSelected -> colors.onSurface
                            else -> colors.onSurfaceVariant.copy(alpha = 0.85f)
                        }
                        val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = itemHeight)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = enabled,
                                    onClick = { if (!isSelected) onSelect(option) },
                                )
                                .semantics { this.role = Role.RadioButton }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                ProvideTextStyle(
                                    MaterialTheme.typography.labelLarge.copy(fontWeight = fontWeight),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        content(option, isSelected)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 独立胶囊卡片式多选组（与单选滑动轨道区分，每项各自独立开关、带边框与主题容器色反馈）
@Composable
fun <T> ConnectedMultiChoiceButtons(
    options: List<T>,
    selected: Collection<T>,
    onToggle: (option: T, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    minHeight: Dp = 42.dp,
    content: @Composable (option: T, selected: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.fastForEachIndexed { _, option ->
            val isSelected = option in selected
            val chipContainerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    colors.primaryContainer.copy(alpha = 0.72f)
                } else {
                    colors.surfaceContainerLow
                },
                animationSpec = motionSpec,
            )
            val chipContentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    colors.onPrimaryContainer
                } else {
                    colors.onSurfaceVariant
                },
                animationSpec = motionSpec,
            )
            val chipBorderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    colors.primary.copy(alpha = 0.5f)
                } else {
                    colors.outlineVariant.copy(alpha = 0.7f)
                },
                animationSpec = motionSpec,
            )
            val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium

            Surface(
                onClick = { onToggle(option, !isSelected) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight)
                    .semantics { this.role = Role.Checkbox },
                shape = RoundedCornerShape(14.dp),
                color = chipContainerColor,
                border = BorderStroke(1.dp, chipBorderColor),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides chipContentColor) {
                        ProvideTextStyle(
                            MaterialTheme.typography.labelLarge.copy(fontWeight = fontWeight),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                content(option, isSelected)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 按钮组里的一行文字，选中时前面带图标
@Composable
fun ConnectedChoiceLabel(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
) {
    if (icon != null) {
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            }
        }
    }
    Text(
        text = text,
        // 几个按钮并排时较长的译文放不下，自动缩小字号
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 13.sp),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
