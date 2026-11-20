package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

@Composable
fun SettingPreferencesPage() {
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_preferences))
                },
                navigationIcon = {
                    BackButton()
                },
                colors = CustomColors.topBarColors
            )
        },
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                CardGroup {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                        leadingContent = { CardGroupIcon(HugeIcons.Sun01, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_theme)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_theme_desc)) },
                        trailingContent = { PreferenceNavigationArrow() },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                        leadingContent = { CardGroupIcon(HugeIcons.Notification01, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_desc)) },
                        trailingContent = { PreferenceNavigationArrow() },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                        leadingContent = { CardGroupIcon(HugeIcons.Settings03, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_general)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_general_desc)) },
                        trailingContent = { PreferenceNavigationArrow() },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                        leadingContent = { CardGroupIcon(HugeIcons.PaintBoard, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_desc)) },
                        trailingContent = { PreferenceNavigationArrow() },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
                        leadingContent = { CardGroupIcon(HugeIcons.Internet, containerColor = containerColor, contentColor = contentColor) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_network)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_network_desc)) },
                        trailingContent = { PreferenceNavigationArrow() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceNavigationArrow() {
    Icon(
        imageVector = HugeIcons.ArrowRight01,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}
