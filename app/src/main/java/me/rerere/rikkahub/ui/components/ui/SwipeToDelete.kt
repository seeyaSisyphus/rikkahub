package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** 第一段停留态露出的操作区宽度 */
private val RevealWidth = 82.dp

/** 进入第一段停留态的最小左滑阈值 */
private val RevealThreshold = 34.dp

/** 第二段贯穿直接触发的深滑阈值 */
private val DeepCommitThreshold = 156.dp

/** 深滑越界后的最大橡皮筋缓冲距离 */
private val MaxOverDeep = 24.dp

/**
 * 两段式左滑删除/移除组件（参考 CyberSlots 移动端首页分段左滑归档交互）：
 *
 * 1. **第一段（短滑停留态）**：左滑超过 [RevealThreshold]（未达 [DeepCommitThreshold]）松手后，
 *    卡片吸附停留在 `-[RevealWidth]`，右侧露出圆形操作按钮；需显式点击右侧按钮才执行删除，
 *    点击卡片主体或向右轻推自动收回原位，防止滚动浏览时误触删除。
 * 2. **第二段（贯穿深滑态）**：继续深拉越过 [DeepCommitThreshold] 时触发震动反馈与视觉跃迁
 *    （圆钮放大 + 实心高亮 + 外圈光晕，文字切换为 [releaseLabel]），此时松手直接执行删除；
 *    若松手前拉回阈值以内则回退为第一段停留态。
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    actionLabel: String = stringResource(R.string.delete),
    releaseLabel: String = "松手$actionLabel",
    expanded: Boolean? = null,
    onExpandChange: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnExpandChange by rememberUpdatedState(onExpandChange)

    val revealWidthPx = with(density) { RevealWidth.toPx() }
    val revealThresholdPx = with(density) { RevealThreshold.toPx() }
    val deepThresholdPx = with(density) { DeepCommitThreshold.toPx() }
    val maxOverDeepPx = with(density) { MaxOverDeep.toPx() }
    val haloExpandPx = with(density) { 4.dp.toPx() }

    val offsetAnim = remember { Animatable(0f) }
    var cardWidthPx by remember { mutableIntStateOf(0) }
    var isRevealed by remember { mutableStateOf(false) }
    var deepArmed by remember { mutableStateOf(false) }
    var committedFromDeep by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }

    var dragBaseX by remember { mutableFloatStateOf(0f) }
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var stage1Ticked by remember { mutableStateOf(false) }

    fun setRevealedState(open: Boolean) {
        if (isRevealed != open) {
            isRevealed = open
        }
        currentOnExpandChange?.invoke(open)
    }

    fun closeReveal() {
        if (committing) return
        deepArmed = false
        setRevealedState(false)
        scope.launch {
            offsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
        }
    }

    fun openReveal() {
        if (committing) return
        deepArmed = false
        setRevealedState(true)
        scope.launch {
            offsetAnim.animateTo(
                targetValue = -revealWidthPx,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
        }
    }

    fun commitDelete(fromDeepSwipe: Boolean) {
        if (committing) return
        committing = true
        committedFromDeep = fromDeepSwipe
        if (!fromDeepSwipe) {
            deepArmed = false
        }
        currentOnExpandChange?.invoke(false)
        scope.launch {
            val exitTarget = -((if (cardWidthPx > 0) cardWidthPx.toFloat() else deepThresholdPx * 2f) + revealWidthPx * 0.5f)
            offsetAnim.animateTo(
                targetValue = exitTarget,
                animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
            )
            currentOnDelete()
            // 若外部未立即移除该项或稍后撤销恢复，复位状态
            committing = false
            committedFromDeep = false
            deepArmed = false
            isRevealed = false
            offsetAnim.snapTo(0f)
        }
    }

    // 外部联动收起（例如其他卡片展开或列表发生滚动）
    LaunchedEffect(expanded) {
        if (expanded == false && !committing && (isRevealed || offsetAnim.value < -1f)) {
            deepArmed = false
            isRevealed = false
            offsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
        } else if (expanded == true && !committing && !isRevealed) {
            isRevealed = true
            offsetAnim.animateTo(
                targetValue = -revealWidthPx,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
        }
    }

    val currentOffsetX = offsetAnim.value
    val progress = (-currentOffsetX / revealWidthPx).coerceIn(0f, 1f)
    val showDeepState = deepArmed || committedFromDeep

    val discScale by animateFloatAsState(
        targetValue = if (showDeepState) 1.15f else (0.62f + 0.38f * progress),
        animationSpec = tween(durationMillis = 160),
        label = "swipe_disc_scale",
    )
    val discBgColor by animateColorAsState(
        targetValue = when {
            showDeepState -> colors.error
            isRevealed || progress >= 0.85f -> colors.errorContainer
            else -> colors.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 160),
        label = "swipe_disc_bg",
    )
    val discContentColor by animateColorAsState(
        targetValue = when {
            showDeepState -> colors.onError
            isRevealed || progress >= 0.85f -> colors.onErrorContainer
            else -> colors.error
        },
        animationSpec = tween(durationMillis = 160),
        label = "swipe_disc_content",
    )
    val discBorderColor by animateColorAsState(
        targetValue = when {
            showDeepState -> colors.error
            else -> colors.error.copy(alpha = 0.28f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "swipe_disc_border",
    )

    val trackAlpha = if (committing) {
        val totalExit = (if (cardWidthPx > 0) cardWidthPx.toFloat() else deepThresholdPx * 2f).coerceAtLeast(1f)
        (1f - (-currentOffsetX / totalExit)).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .onSizeChanged { cardWidthPx = it.width },
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 背景动作层：随滑动进度渐显并生长的圆钮 + 分段提示标签
        if (currentOffsetX < -0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = (progress * 0.98f).coerceIn(0f, 1f) }
                    .background(
                        brush = Brush.horizontalGradient(
                            0.25f to Color.Transparent,
                            1.0f to colors.errorContainer.copy(alpha = if (showDeepState) 0.42f else 0.24f),
                        ),
                        shape = shape,
                    )
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            enabled = isRevealed && !committing,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            commitDelete(fromDeepSwipe = false)
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val haloColor = colors.error.copy(alpha = 0.22f)
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = discScale
                                scaleY = discScale
                                alpha = (0.35f + 0.65f * progress).coerceIn(0f, 1f)
                            }
                            .drawBehind {
                                if (showDeepState) {
                                    drawCircle(
                                        color = haloColor,
                                        radius = size.minDimension / 2f + haloExpandPx,
                                    )
                                }
                            },
                        shape = CircleShape,
                        color = discBgColor,
                        contentColor = discContentColor,
                        border = BorderStroke(1.2.dp, discBorderColor),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = HugeIcons.Delete01,
                                contentDescription = actionLabel,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Text(
                        text = if (showDeepState) releaseLabel else actionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = if (showDeepState) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (showDeepState) colors.error else colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = (progress * progress).coerceIn(0f, 1f)
                        },
                    )
                }
            }
        }

        // 表层卡片：承载分段水平拖拽手势，且在第一段展开态下拦截点按以自动收起防误触
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(currentOffsetX.roundToInt(), 0) }
                .graphicsLayer { alpha = trackAlpha }
                .pointerInput(isRevealed, committing) {
                    if (!isRevealed || committing) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startPos = down.position
                        var movedPastSlop = false
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change != null) {
                                val dx = abs(change.position.x - startPos.x)
                                val dy = abs(change.position.y - startPos.y)
                                if (dx > slop || dy > slop) {
                                    movedPastSlop = true
                                }
                            }
                            if (event.changes.all { !it.pressed }) {
                                if (!movedPastSlop) {
                                    // 展开态下轻点卡片主体：吞掉点击事件并收回卡片，避免误进入详情
                                    event.changes.forEach { it.consume() }
                                    closeReveal()
                                }
                                break
                            }
                        }
                    }
                }
                .pointerInput(revealWidthPx, revealThresholdPx, deepThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (committing) return@detectHorizontalDragGestures
                            dragBaseX = offsetAnim.value
                            rawDragX = dragBaseX
                            stage1Ticked = (-dragBaseX) >= revealThresholdPx
                            if (dragBaseX < -2f) {
                                currentOnExpandChange?.invoke(true)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (committing) return@detectHorizontalDragGestures
                            change.consume()
                            rawDragX += dragAmount

                            // 分段阻尼映射：
                            // 0 ~ -RevealWidth: 1:1 跟随
                            // -RevealWidth ~ -DeepCommitThreshold: 0.72 阻尼过渡段，营造明显的第一段停顿感
                            // 超过 -DeepCommitThreshold: 橡皮筋强阻尼
                            val mappedX = when {
                                rawDragX >= 0f -> 0f
                                rawDragX >= -revealWidthPx -> rawDragX
                                else -> {
                                    val extra = -rawDragX - revealWidthPx
                                    val stage2Span = (deepThresholdPx - revealWidthPx).coerceAtLeast(1f)
                                    val dampedExtra = extra * 0.72f
                                    if (dampedExtra <= stage2Span) {
                                        -(revealWidthPx + dampedExtra)
                                    } else {
                                        val overDeep = dampedExtra - stage2Span
                                        -(deepThresholdPx + maxOverDeepPx * (1f - 1f / (1f + overDeep / (maxOverDeepPx * 2f))))
                                    }
                                }
                            }

                            scope.launch {
                                offsetAnim.snapTo(mappedX)
                            }

                            val dist = -mappedX
                            if (dist > 4f) {
                                currentOnExpandChange?.invoke(true)
                            }

                            // 第一段阈值轻触反馈
                            val reachedStage1 = dist >= revealThresholdPx
                            if (reachedStage1 != stage1Ticked) {
                                stage1Ticked = reachedStage1
                                if (reachedStage1 && dragBaseX > -10f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }

                            // 第二段深滑阈值段落震动与视觉跃迁
                            val reachedDeep = dist >= deepThresholdPx
                            if (reachedDeep != deepArmed) {
                                deepArmed = reachedDeep
                                if (reachedDeep) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = {
                            if (committing) return@detectHorizontalDragGestures
                            val dist = -offsetAnim.value
                            when {
                                // 第二段深滑：松手直接触发移除
                                dist >= deepThresholdPx -> {
                                    commitDelete(fromDeepSwipe = true)
                                }
                                // 原本处于第一段展开态：向右回推超过 16dp 则收起，否则保持吸附在第一段
                                dragBaseX < -10f -> {
                                    if (dist < revealWidthPx - with(density) { 16.dp.toPx() }) {
                                        closeReveal()
                                    } else {
                                        openReveal()
                                    }
                                }
                                // 原本处于闭合态：达到第一段阈值则吸附展开露出按钮，否则弹回原位
                                dist >= revealThresholdPx -> {
                                    openReveal()
                                }
                                else -> {
                                    closeReveal()
                                }
                            }
                        },
                        onDragCancel = {
                            if (!committing) {
                                closeReveal()
                            }
                        },
                    )
                },
            content = content,
        )
    }
}
