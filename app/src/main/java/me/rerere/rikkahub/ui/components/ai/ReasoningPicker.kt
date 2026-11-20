package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppModalBottomSheet
import me.rerere.ui.components.ToggleSurface
import me.rerere.ui.icons.ReasoningHigh
import me.rerere.ui.icons.ReasoningLow
import me.rerere.ui.icons.ReasoningMax
import me.rerere.ui.icons.ReasoningMedium
import me.rerere.ui.icons.ReasoningXHigh
import kotlin.math.roundToInt

private val CapsuleTrackHeight = 32.dp
private val CapsuleThumbSize = 24.dp
private val CapsuleDotSize = 4.dp

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    compact: Boolean = false,
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
    model: Model? = null,
    providerSetting: ProviderSetting? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val supportedLevels = remember(model, providerSetting) {
        ModelRegistry.resolveReasoningLevels(model, providerSetting)
    }
    val displayLevel = remember(reasoningLevel, supportedLevels) {
        ModelRegistry.closestReasoningLevel(reasoningLevel, supportedLevels)
    }
    val maxLevel = remember(supportedLevels) {
        supportedLevels.lastOrNull { it.isEnabled }
    }

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = displayLevel,
            supportedLevels = supportedLevels,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel,
        )
    }

    if (compact) {
        Surface(
            onClick = { showPicker = true },
            modifier = modifier,
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            Text(
                text = displayLevel.rawLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (displayLevel.isEnabled) FontWeight.Medium else FontWeight.Normal,
                color = if (displayLevel.isEnabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                },
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        return
    }

    ToggleSurface(
        checked = displayLevel.isEnabled,
        onClick = { showPicker = true },
        modifier = if (onlyIcon) modifier.size(36.dp) else modifier,
    ) {
        if (onlyIcon) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = displayLevel.icon(maxLevel),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(displayLevel.icon(maxLevel), null)
                }
                Text(stringResource(R.string.setting_provider_page_reasoning))
            }
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    supportedLevels: List<ReasoningLevel> = ReasoningLevel.entries,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val levels = remember(supportedLevels) {
        supportedLevels.ifEmpty { ReasoningLevel.entries }
    }
    val maxLevel = remember(levels) {
        levels.lastOrNull { it.isEnabled }
    }
    val shapes = remember(levels, maxLevel) { levels.map { it.shape(maxLevel) } }
    val resolvedLevel = remember(reasoningLevel, levels) {
        ModelRegistry.closestReasoningLevel(reasoningLevel, levels)
    }
    val currentIndex = levels.indexOf(resolvedLevel).coerceAtLeast(0)

    var selectedIndex by remember { mutableIntStateOf(currentIndex) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(currentIndex) {
        if (dragProgress == null) {
            selectedIndex = currentIndex
        }
    }

    val activeProgress = dragProgress ?: selectedIndex.toFloat()
    val activeIndex = activeProgress.roundToInt().coerceIn(0, levels.lastIndex)
    val previewLevel = levels[activeIndex]

    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }
    LaunchedEffect(activeIndex) {
        if (activeIndex != lastHapticIndex) {
            lastHapticIndex = activeIndex
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PickerValueHeader(
                title = stringResource(R.string.reasoning_picker_title),
                value = previewLevel,
                label = { it.rawLabel },
            ) {
                // 等级越高形状越「激烈」，当前模型最高档位固定使用带闪电的灯泡图标
                PickerHero(
                    shapes = shapes,
                    index = activeIndex,
                    icon = previewLevel.icon(maxLevel),
                    containerColor = when {
                        !previewLevel.isEnabled -> colors.surfaceContainerHighest
                        previewLevel == maxLevel || previewLevel >= ReasoningLevel.HIGH -> colors.primary
                        else -> colors.primaryContainer
                    },
                    contentColor = when {
                        !previewLevel.isEnabled -> colors.onSurfaceVariant
                        previewLevel == maxLevel || previewLevel >= ReasoningLevel.HIGH -> colors.onPrimary
                        else -> colors.onPrimaryContainer
                    },
                )
            }

            EffortCapsuleSlider(
                levels = levels,
                selectedIndex = selectedIndex,
                dragProgress = dragProgress,
                previewLevel = previewLevel,
                onDragStartOrMove = { progress -> dragProgress = progress },
                onDragEnd = { snappedIndex ->
                    dragProgress = null
                    selectedIndex = snappedIndex
                    onUpdateReasoningLevel(levels[snappedIndex])
                },
                onDragCancel = { dragProgress = null },
                onSelectIndex = { index ->
                    dragProgress = null
                    selectedIndex = index
                    onUpdateReasoningLevel(levels[index])
                },
            )
        }
    }
}

@Composable
private fun EffortCapsuleSlider(
    levels: List<ReasoningLevel>,
    selectedIndex: Int,
    dragProgress: Float?,
    previewLevel: ReasoningLevel,
    onDragStartOrMove: (Float) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: () -> Unit,
    onSelectIndex: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val levelCount = levels.size
    val maxStep = (levelCount - 1).coerceAtLeast(1)

    val animatedIndexProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "effort_capsule_progress",
    )
    val displayProgress = (dragProgress ?: animatedIndexProgress).coerceIn(0f, (levelCount - 1).coerceAtLeast(0).toFloat())
    val activeIndex = displayProgress.roundToInt().coerceIn(0, levels.lastIndex)

    val latestOnDragStartOrMove by rememberUpdatedState(onDragStartOrMove)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)

    val fillColor = if (previewLevel.isEnabled) {
        colors.primary
    } else {
        colors.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val thumbColor = if (previewLevel.isEnabled) {
        colors.onPrimary
    } else {
        colors.surfaceBright
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = previewLevel.rawLabel
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val trackHeightPx = with(density) { CapsuleTrackHeight.toPx() }
            val thumbSizePx = with(density) { CapsuleThumbSize.toPx() }
            val dotSizePx = with(density) { CapsuleDotSize.toPx() }

            val minCenterPx = if (levelCount <= 1) maxWidthPx / 2f else trackHeightPx / 2f
            val maxCenterPx = if (levelCount <= 1) maxWidthPx / 2f else (maxWidthPx - trackHeightPx / 2f).coerceAtLeast(minCenterPx)
            val spanPx = (maxCenterPx - minCenterPx).coerceAtLeast(1f)

            val fraction = if (levelCount > 1) (displayProgress / maxStep).coerceIn(0f, 1f) else 1f
            val thumbCenterPx = if (levelCount <= 1) maxWidthPx / 2f else minCenterPx + fraction * spanPx
            val filledWidthDp = with(density) {
                if (levelCount <= 1) maxWidth else (thumbCenterPx + trackHeightPx / 2f).coerceAtMost(maxWidthPx).toDp()
            }
            val thumbOffsetX = with(density) { (thumbCenterPx - thumbSizePx / 2f).roundToInt() }

            var gestureProgress = displayProgress

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(levelCount, maxWidthPx) {
                        if (levelCount <= 1) return@pointerInput
                        detectTapGestures { offset ->
                            val raw = (((offset.x - minCenterPx) / spanPx).coerceIn(0f, 1f)) * maxStep
                            latestOnSelectIndex(raw.roundToInt().coerceIn(0, levelCount - 1))
                        }
                    }
                    .pointerInput(levelCount, maxWidthPx) {
                        if (levelCount <= 1) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                gestureProgress = (((offset.x - minCenterPx) / spanPx).coerceIn(0f, 1f)) * maxStep
                                latestOnDragStartOrMove(gestureProgress)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                gestureProgress = (gestureProgress + (dragAmount / spanPx) * maxStep)
                                    .coerceIn(0f, maxStep.toFloat())
                                latestOnDragStartOrMove(gestureProgress)
                            },
                            onDragEnd = {
                                latestOnDragEnd(gestureProgress.roundToInt().coerceIn(0, levelCount - 1))
                            },
                            onDragCancel = {
                                latestOnDragCancel()
                            },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // 胶囊底轨
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CapsuleTrackHeight)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // 胶囊已填充部分，包裹到滑块右侧
                    Box(
                        modifier = Modifier
                            .width(filledWidthDp)
                            .height(CapsuleTrackHeight)
                            .clip(CircleShape)
                            .background(fillColor),
                    )

                    // 档位刻度点
                    for (i in 0 until levelCount) {
                        val dotCenterPx = if (levelCount <= 1) {
                            maxWidthPx / 2f
                        } else {
                            minCenterPx + (i.toFloat() / maxStep) * spanPx
                        }
                        val dotOffsetX = with(density) { (dotCenterPx - dotSizePx / 2f).roundToInt() }
                        val filled = i <= displayProgress + 0.05f
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(dotOffsetX, 0) }
                                .size(CapsuleDotSize)
                                .background(
                                    color = if (filled) {
                                        colors.onPrimary.copy(alpha = 0.68f)
                                    } else {
                                        colors.onSurfaceVariant.copy(alpha = 0.35f)
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }

                    // 滑块圆钮
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(thumbOffsetX, 0) }
                            .size(CapsuleThumbSize),
                        shape = CircleShape,
                        color = thumbColor,
                        shadowElevation = 2.dp,
                    ) {}
                }
            }
        }

        // 与刻度点垂直对齐的档位标签
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                levels.forEachIndexed { index, level ->
                    val isActive = index == activeIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectIndex(index) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = level.rawLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                isActive && previewLevel.isEnabled -> colors.primary
                                isActive -> colors.onSurface
                                else -> colors.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val trackHeightPx = with(density) { CapsuleTrackHeight.toPx() }
            val maxW = constraints.maxWidth.toFloat()
            val minCenter = if (levelCount <= 1) maxW / 2f else trackHeightPx / 2f
            val maxCenter = if (levelCount <= 1) maxW / 2f else (maxW - trackHeightPx / 2f).coerceAtLeast(minCenter)
            val span = (maxCenter - minCenter).coerceAtLeast(1f)

            val childConstraints = constraints.copy(minWidth = 0)
            val placeables = measurables.map { it.measure(childConstraints) }
            val height = placeables.maxOfOrNull { it.height } ?: 0

            layout(constraints.maxWidth, height) {
                placeables.forEachIndexed { index, placeable ->
                    val centerPx = if (levelCount <= 1) {
                        maxW / 2f
                    } else {
                        minCenter + (index.toFloat() / maxStep) * span
                    }
                    val x = (centerPx.roundToInt() - placeable.width / 2)
                        .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                    val y = (height - placeable.height) / 2
                    placeable.placeRelative(x, y)
                }
            }
        }
    }
}

private fun ReasoningLevel.icon(maxLevel: ReasoningLevel? = null): ImageVector = when {
    this == ReasoningLevel.MAX || (this.isEnabled && this == maxLevel) -> ReasoningMax
    this == ReasoningLevel.OFF -> HugeIcons.Idea
    this == ReasoningLevel.AUTO -> HugeIcons.Idea01
    this == ReasoningLevel.LOW -> ReasoningLow
    this == ReasoningLevel.MEDIUM -> ReasoningMedium
    this == ReasoningLevel.HIGH -> ReasoningHigh
    this == ReasoningLevel.XHIGH -> ReasoningXHigh
    else -> ReasoningMax
}

private fun ReasoningLevel.shape(maxLevel: ReasoningLevel? = null): RoundedPolygon = when {
    this == ReasoningLevel.MAX || (this.isEnabled && this == maxLevel) -> MaterialShapes.SoftBurst
    this == ReasoningLevel.OFF -> MaterialShapes.Circle
    this == ReasoningLevel.AUTO -> MaterialShapes.Cookie4Sided
    this == ReasoningLevel.LOW -> MaterialShapes.Cookie6Sided
    this == ReasoningLevel.MEDIUM -> MaterialShapes.Cookie7Sided
    this == ReasoningLevel.HIGH -> MaterialShapes.Cookie9Sided
    this == ReasoningLevel.XHIGH -> MaterialShapes.Cookie12Sided
    else -> MaterialShapes.SoftBurst
}

@Preview(showBackground = true)
@Composable
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.HIGH) }
        ReasoningPicker(
            reasoningLevel = level,
            supportedLevels = listOf(
                ReasoningLevel.LOW,
                ReasoningLevel.HIGH,
                ReasoningLevel.XHIGH,
                ReasoningLevel.MAX,
            ),
            onUpdateReasoningLevel = { level = it },
        )
    }
}
