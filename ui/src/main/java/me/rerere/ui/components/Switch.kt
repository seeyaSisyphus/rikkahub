package me.rerere.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SwitchSize {
    Small,
    Medium,
    Large
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: SwitchSize = SwitchSize.Medium,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primary,
    trackColorUnchecked: Color = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    },
    thumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    thumbColorUnchecked: Color = Color.White
) {
    val dimensions = when (size) {
        SwitchSize.Small -> SwitchDimensions(
            trackWidth = 36.dp,
            trackHeight = 20.dp,
            thumbSize = 16.dp,
            thumbPadding = 2.dp
        )

        SwitchSize.Medium -> SwitchDimensions(
            trackWidth = 46.dp,
            trackHeight = 26.dp,
            thumbSize = 22.dp,
            thumbPadding = 2.dp
        )

        SwitchSize.Large -> SwitchDimensions(
            trackWidth = 52.dp,
            trackHeight = 28.dp,
            thumbSize = 24.dp,
            thumbPadding = 2.dp
        )
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            dimensions.trackWidth - dimensions.thumbSize - dimensions.thumbPadding * 2
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = 160),
        label = "thumbOffset"
    )

    val currentTrackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            checked -> trackColor
            else -> trackColorUnchecked
        },
        animationSpec = tween(durationMillis = 160),
        label = "trackColor"
    )

    val currentThumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.65f)
            checked -> thumbColor
            else -> thumbColorUnchecked
        },
        animationSpec = tween(durationMillis = 160),
        label = "thumbColor"
    )

    Box(
        modifier = modifier
            .size(width = dimensions.trackWidth, height = dimensions.trackHeight)
            .clip(RoundedCornerShape(50))
            .background(currentTrackColor)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(dimensions.thumbPadding)
                .offset(x = thumbOffset)
                .size(dimensions.thumbSize)
                .shadow(
                    elevation = if (enabled) 2.dp else 0.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(currentThumbColor)
        )
    }
}

private data class SwitchDimensions(
    val trackWidth: Dp,
    val trackHeight: Dp,
    val thumbSize: Dp,
    val thumbPadding: Dp
)

@Composable
@Preview(showBackground = true)
private fun SwitchPreview() {
    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var checkedSmall by remember { mutableStateOf(true) }
        var checkedMedium by remember { mutableStateOf(true) }
        var checkedLarge by remember { mutableStateOf(true) }
        var unchecked by remember { mutableStateOf(false) }

        Text("Small", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedSmall,
            onCheckedChange = { checkedSmall = it },
            size = SwitchSize.Small
        )

        Text("Medium (Default)", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedMedium,
            onCheckedChange = { checkedMedium = it },
            size = SwitchSize.Medium
        )

        Text("Large", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checkedLarge,
            onCheckedChange = { checkedLarge = it },
            size = SwitchSize.Large
        )

        Text("Unchecked", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = unchecked,
            onCheckedChange = { unchecked = it },
            size = SwitchSize.Medium
        )

        Text("Disabled", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = true,
            onCheckedChange = {},
            size = SwitchSize.Medium,
            enabled = false
        )
    }
}
