package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import me.rerere.rikkahub.ui.theme.LocalDarkMode

private val AppMenuShape = RoundedCornerShape(20.dp)

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerColor = if (LocalDarkMode.current) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = AppMenuShape,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        content = content,
    )
}

@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = appMenuItemColors(),
    contentPadding: PaddingValues? = null,
) {
    val resolvedPadding = contentPadding ?: PaddingValues(
        start = 14.dp,
        end = if (trailingIcon != null) 14.dp else 18.dp,
        top = 10.dp,
        bottom = 10.dp,
    )
    val textColor = if (enabled) colors.textColor else colors.disabledTextColor
    val leadingColor = if (enabled) colors.leadingIconColor else colors.disabledLeadingIconColor
    val trailingColor = if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(resolvedPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides leadingColor) {
                    leadingIcon()
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    content = text,
                )
            }
        }

        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides trailingColor) {
                    trailingIcon()
                }
            }
        }
    }
}

@Composable
fun AppMenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
fun appMenuItemColors(
    destructive: Boolean = false,
    selected: Boolean = false,
): MenuItemColors {
    val colors = MaterialTheme.colorScheme
    return when {
        destructive -> MenuDefaults.itemColors(
            textColor = colors.error,
            leadingIconColor = colors.error,
            trailingIconColor = colors.error,
        )
        selected -> MenuDefaults.itemColors(
            textColor = colors.primary,
            leadingIconColor = colors.primary,
            trailingIconColor = colors.primary,
        )
        else -> MenuDefaults.itemColors(
            textColor = colors.onSurface,
            leadingIconColor = colors.onSurfaceVariant,
            trailingIconColor = colors.onSurfaceVariant,
        )
    }
}

val AppMenuItemContentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
