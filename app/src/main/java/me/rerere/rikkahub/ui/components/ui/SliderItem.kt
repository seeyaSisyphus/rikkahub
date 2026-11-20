package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MinusSign
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 统一样式的细轨道胶囊滑块，替换默认粗重的 M3 Expressive Slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = LocalDarkMode.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)

    val sliderState = remember(valueRange, steps) {
        SliderState(
            value = clampedValue,
            steps = steps,
            trackRange = valueRange,
        )
    }

    LaunchedEffect(clampedValue, isDragged, sliderState) {
        if (!isDragged && abs(sliderState.value - clampedValue) > 1e-4f) {
            sliderState.value = clampedValue
        }
    }

    val thumbVisualSize by animateDpAsState(
        targetValue = if (enabled && (isPressed || isDragged)) 22.dp else 18.dp,
        animationSpec = tween(durationMillis = 150),
        label = "slider_thumb_size",
    )

    val thumbColor = when {
        !enabled -> colors.surfaceContainerHigh
        isDark -> colors.surfaceContainerHighest
        else -> colors.surfaceBright
    }
    val ringColor = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.28f)
    val trackBgColor = if (enabled) {
        colors.surfaceContainerHighest
    } else {
        colors.surfaceContainerHigh.copy(alpha = 0.6f)
    }
    val trackFillColor = if (enabled) {
        colors.primary
    } else {
        colors.onSurface.copy(alpha = 0.32f)
    }

    Slider(
        state = sliderState,
        onValueChange = { next ->
            sliderState.value = next
            onValueChange(next)
        },
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = thumbColor,
                    border = BorderStroke(2.5.dp, ringColor),
                    shadowElevation = if (enabled) 3.dp else 0.dp,
                    modifier = Modifier.size(thumbVisualSize),
                ) {}
            }
        },
        track = { state ->
            val span = state.trackRange.endInclusive - state.trackRange.start
            val fraction = if (span > 0f) {
                ((state.value - state.trackRange.start) / span).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(trackBgColor),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(trackFillColor, CircleShape),
                    )
                }
            }
        },
    )
}

/**
 * 整数滑块三件套: Minus IconButton + Slider + Plus IconButton.
 *
 * 解决直接使用 M3 Slider 时整数范围过大难以精确调节 (尤其是小数值) 的问题。
 *
 * - +/- 按钮: 每按一下按 [step] 增减, 在 [valueRange] 边界处禁用.
 * - 滑块拖动: 拖动过程仅在本地缓冲, 松手时通过 [onValueChange] 一次性提交,
 *   避免每次像素级变化都触发上层数据模型与持久化.
 * - [onValueChanging]: 拖动过程中持续回调（不提交/不持久化），供调用方实时更新显示.
 * - 外部 [value] 变化 (例如重置) 通过 [LaunchedEffect] 回流到本地 buffer.
 */
@Composable
fun IntSliderItem(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    enabled: Boolean = true,
    onValueChanging: (Int) -> Unit = {},
) {
    require(valueRange.first <= valueRange.last) {
        "valueRange.first must be <= valueRange.last"
    }
    require(step > 0) { "step must be positive" }

    val colors = MaterialTheme.colorScheme
    val min = valueRange.first
    val max = valueRange.last
    val coerced = value.coerceIn(min, max)

    // 本地缓冲: 拖动时仅更新此 state, 不触发外部 onValueChange
    var sliderValue by remember(coerced) { mutableFloatStateOf(coerced.toFloat()) }

    // 外部 value 改变 (例如重置或被其他来源修改) 时同步到本地 buffer
    LaunchedEffect(coerced) {
        sliderValue = coerced.toFloat()
    }

    val canDecrement = enabled && coerced > min
    val canIncrement = enabled && coerced < max

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = {
                val next = (coerced - step).coerceAtLeast(min)
                if (next != coerced) onValueChange(next)
            },
            enabled = canDecrement,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = HugeIcons.MinusSign,
                contentDescription = "Decrease",
                modifier = Modifier.size(16.dp),
                tint = if (canDecrement) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.28f),
            )
        }
        AppSlider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onValueChanging(it.roundToInt().coerceIn(min, max))
            },
            onValueChangeFinished = {
                val snapped = sliderValue.roundToInt().coerceIn(min, max)
                sliderValue = snapped.toFloat()
                if (snapped != coerced) onValueChange(snapped)
            },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                val next = (coerced + step).coerceAtMost(max)
                if (next != coerced) onValueChange(next)
            },
            enabled = canIncrement,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = HugeIcons.PlusSign,
                contentDescription = "Increase",
                modifier = Modifier.size(16.dp),
                tint = if (canIncrement) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.28f),
            )
        }
    }
}
