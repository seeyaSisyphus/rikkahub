package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.CheckmarkCircle01
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Sword02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.ConversationParams
import me.rerere.rikkahub.ui.components.ai.BattleModelPicker
import me.rerere.rikkahub.ui.components.ui.AppDropdownMenu
import me.rerere.rikkahub.ui.components.ui.AppDropdownMenuItem
import me.rerere.rikkahub.ui.components.ui.AppSwitch
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.appMenuItemColors
import me.rerere.ui.icons.ReasoningHigh
import me.rerere.ui.icons.ReasoningLow
import me.rerere.ui.icons.ReasoningMax
import me.rerere.ui.icons.ReasoningMedium
import me.rerere.ui.icons.ReasoningXHigh

/**
 * [FORK] Battle Mode UI section (MD3 Expressive CardGroup style).
 *
 * Rendered inside ConversationParamsSheet.
 * Provides a toggle switch plus a multi-model picker:
 *   - Selected models shown as custom chips in a wrapping FlowRow layout.
 *     Long-press a chip to drag-reorder; tap chip body to set thinking depth via DropdownMenu;
 *     tap the trailing X icon to remove.
 *   - "Select model" button appends new models to the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleModeSection(
    params: ConversationParams,
    settings: Settings,
    onUpdate: (ConversationParams) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CardGroup {
        formItem(
            leadingContent = {
                CardGroupIcon(
                    icon = HugeIcons.Sword02,
                    containerColor = if (params.battleModeEnabled) colors.primaryContainer else colors.secondaryContainer,
                    contentColor = if (params.battleModeEnabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                )
            },
            headlineContent = {
                Text("Battle Mode")
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (params.battleModeEnabled && params.battleModelIds.isNotEmpty()) {
                        CardGroupValue(text = "${params.battleModelIds.size}")
                    }
                    AppSwitch(
                        checked = params.battleModeEnabled,
                        onCheckedChange = { enabled ->
                            onUpdate(params.copy(battleModeEnabled = enabled))
                        },
                    )
                }
            },
            contentSpacing = 0.dp,
        ) {
            AnimatedVisibility(
                visible = params.battleModeEnabled,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                val haptic = LocalHapticFeedback.current
                val assistant = settings.getCurrentAssistant()

                // 每个 chip 的窗口坐标（用于拖拽命中检测）
                val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
                // 正在拖拽的 index，-1 表示未拖拽
                var draggingIndex by remember { mutableIntStateOf(-1) }
                // 当前悬停的目标 index
                var hoverIndex by remember { mutableIntStateOf(-1) }
                // 拖拽起始的窗口坐标
                var startWindowPos by remember { mutableStateOf(Offset.Zero) }
                // 从拖拽开始累计的位移
                var cumulativeDrag by remember { mutableStateOf(Offset.Zero) }
                // 当前展开 DropdownMenu 的 chip index，-1 表示无
                var expandedDropdownIndex by remember { mutableIntStateOf(-1) }

                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onUpdate(params.copy(battleIndependentContext = !params.battleIndependentContext))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "独立上下文",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        AppSwitch(
                            checked = params.battleIndependentContext,
                            onCheckedChange = { enabled ->
                                onUpdate(params.copy(battleIndependentContext = enabled))
                            },
                        )
                    }

                    if (params.battleModelIds.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            params.battleModelIds.forEachIndexed { index, modelId ->
                                val model = settings.providers.findModelById(modelId)
                                val isDragging = draggingIndex == index
                                val isHover = hoverIndex == index && !isDragging
                                val currentReasoningLevel = params.battleModelReasoningLevels[modelId]
                                    ?: assistant.reasoningLevel

                                Box(
                                    modifier = Modifier
                                        .alpha(if (isDragging) 0.4f else 1f)
                                        .scale(if (isHover) 1.06f else 1f)
                                        .onGloballyPositioned { coords ->
                                            itemBounds[index] = coords.boundsInWindow()
                                        }
                                        .pointerInput(modelId) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { localOffset ->
                                                    expandedDropdownIndex = -1
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggingIndex = index
                                                    hoverIndex = index
                                                    cumulativeDrag = Offset.Zero
                                                    val bounds = itemBounds[index]
                                                    startWindowPos = if (bounds != null) {
                                                        Offset(
                                                            bounds.left + localOffset.x,
                                                            bounds.top + localOffset.y,
                                                        )
                                                    } else Offset.Zero
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    cumulativeDrag += dragAmount
                                                    val currentPos = startWindowPos + cumulativeDrag
                                                    val nearest = itemBounds.entries.minByOrNull { (_, bounds) ->
                                                        (bounds.center - currentPos).getDistance()
                                                    }
                                                    if (nearest != null) {
                                                        hoverIndex = nearest.key
                                                    }
                                                },
                                                onDragEnd = {
                                                    val from = draggingIndex
                                                    val to = hoverIndex
                                                    draggingIndex = -1
                                                    hoverIndex = -1
                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                    if (from != -1 && to != -1 && from != to) {
                                                        val newList = params.battleModelIds.toMutableList()
                                                        newList.add(to, newList.removeAt(from))
                                                        onUpdate(params.copy(battleModelIds = newList))
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingIndex = -1
                                                    hoverIndex = -1
                                                },
                                            )
                                        }
                                ) {
                                    val modelProvider = remember(model, settings.providers) {
                                        model?.findProvider(settings.providers)
                                    }
                                    val supportedLevels = remember(model, modelProvider) {
                                        ModelRegistry.resolveReasoningLevels(model, modelProvider)
                                    }
                                    val effectiveLevel = ModelRegistry.closestReasoningLevel(
                                        currentReasoningLevel,
                                        supportedLevels,
                                    )
                                    val maxLevel = remember(supportedLevels) {
                                        supportedLevels.lastOrNull { it.isEnabled }
                                    }
                                    BattleModelChip(
                                        modelName = model?.displayName ?: modelId.toString().take(8),
                                        reasoningLevel = effectiveLevel,
                                        maxLevel = maxLevel,
                                        isHover = isHover,
                                        onChipClick = {
                                            if (draggingIndex == -1) {
                                                expandedDropdownIndex = if (expandedDropdownIndex == index) -1 else index
                                            }
                                        },
                                        onRemoveClick = {
                                            if (draggingIndex == -1) {
                                                onUpdate(
                                                    params.copy(
                                                        battleModelIds = params.battleModelIds.filter { it != modelId },
                                                        battleModelReasoningLevels = params.battleModelReasoningLevels - modelId,
                                                    )
                                                )
                                                if (expandedDropdownIndex == index) {
                                                    expandedDropdownIndex = -1
                                                }
                                            }
                                        }
                                    )

                                    AppDropdownMenu(
                                        expanded = expandedDropdownIndex == index,
                                        onDismissRequest = { expandedDropdownIndex = -1 },
                                    ) {
                                        supportedLevels.forEach { level ->
                                            val isSelected = level == effectiveLevel
                                            AppDropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = level.rawLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                },
                                                leadingIcon = {
                                                    BattleReasoningIcon(
                                                        level = level,
                                                        maxLevel = maxLevel,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                                trailingIcon = {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = HugeIcons.CheckmarkCircle01,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                },
                                                colors = appMenuItemColors(selected = isSelected),
                                                onClick = {
                                                    onUpdate(
                                                        params.copy(
                                                            battleModelReasoningLevels = params.battleModelReasoningLevels + (modelId to level)
                                                        )
                                                    )
                                                    expandedDropdownIndex = -1
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    BattleModelPicker(
                        providers = settings.providers,
                        selectedModelIds = params.battleModelIds,
                        onToggle = { model ->
                            val newList = if (params.battleModelIds.contains(model.id)) {
                                params.battleModelIds.filter { it != model.id }
                            } else {
                                params.battleModelIds + model.id
                            }
                            onUpdate(params.copy(battleModelIds = newList))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 自定义 Battle Mode 模型 Chip。
 *
 * 将主体点击（触发思考深度 DropdownMenu）与 X 图标点击（移除模型）完全分离，
 * 同时在左侧直观展示当前模型的思考深度图标。
 */
@Composable
private fun BattleModelChip(
    modelName: String,
    reasoningLevel: ReasoningLevel,
    maxLevel: ReasoningLevel? = null,
    isHover: Boolean,
    onChipClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = if (isHover) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 主体区：思考深度图标 + 模型名，点击 → 打开思考深度 DropdownMenu
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onChipClick,
                    )
                    .padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BattleReasoningIcon(
                    level = reasoningLevel,
                    maxLevel = maxLevel,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 14.dp,
                )
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            // X 图标区：点击 → 移除该模型
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemoveClick,
                    )
                    .padding(start = 2.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = "remove",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Battle Mode 中的思考深度图标
 */
@Composable
private fun BattleReasoningIcon(
    level: ReasoningLevel,
    tint: Color,
    maxLevel: ReasoningLevel? = null,
    size: Dp = 18.dp,
) {
    val icon = when {
        level == ReasoningLevel.MAX || (level.isEnabled && level == maxLevel) -> ReasoningMax
        level == ReasoningLevel.OFF -> HugeIcons.Idea
        level == ReasoningLevel.AUTO -> HugeIcons.Idea01
        level == ReasoningLevel.LOW -> ReasoningLow
        level == ReasoningLevel.MEDIUM -> ReasoningMedium
        level == ReasoningLevel.HIGH -> ReasoningHigh
        level == ReasoningLevel.XHIGH -> ReasoningXHigh
        else -> ReasoningMax
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint,
    )
}
