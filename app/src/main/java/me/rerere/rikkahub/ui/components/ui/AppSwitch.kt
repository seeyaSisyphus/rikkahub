package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private val TrackWidth = 46.dp
private val TrackHeight = 26.dp
private val ThumbSize = 22.dp
private val ThumbPressedExtraWidth = 4.dp
private val TrackPadding = 2.dp

/**
 * 极简 iOS 风格胶囊开关，替代厚重的 Material 默认 Switch。
 *
 * 支持 `onCheckedChange = null`（由外层整行点击驱动状态切换时使用）。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val isDark = LocalDarkMode.current

    val uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDark) 0.24f else 0.18f
    )
    val targetTrackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        uncheckedTrackColor
    }
    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "AppSwitchTrackColor",
    )

    val targetThumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    val thumbColor by animateColorAsState(
        targetValue = targetThumbColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "AppSwitchThumbColor",
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isPressed && enabled) ThumbSize + ThumbPressedExtraWidth else ThumbSize,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "AppSwitchThumbWidth",
    )
    val maxOffset = TrackWidth - TrackPadding * 2 - thumbWidth
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "AppSwitchThumbOffset",
    )

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = resolvedInteractionSource,
            indication = null,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (!enabled) Modifier.alpha(0.45f) else Modifier)
            .width(TrackWidth)
            .height(TrackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .then(toggleModifier)
            .padding(TrackPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(thumbWidth)
                .height(ThumbSize),
            shape = CircleShape,
            color = thumbColor,
            shadowElevation = if (enabled) 2.dp else 0.dp,
        ) {}
    }
}
