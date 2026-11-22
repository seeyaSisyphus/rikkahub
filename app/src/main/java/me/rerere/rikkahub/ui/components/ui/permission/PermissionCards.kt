package me.rerere.rikkahub.ui.components.ui.permission

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * 本地网络权限状态, Android 17 以下不需要这个权限, 返回 null
 */
@Composable
fun rememberLocalNetworkPermissionState(): PermissionState? {
    return if (Build.VERSION.SDK_INT >= 37) rememberPermissionState(PermissionLocalNetwork) else null
}

/**
 * 通知权限状态, Android 13 以下不需要运行时申请, 返回 null
 */
@Composable
fun rememberNotificationPermissionState(): PermissionState? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(PermissionNotification)
    } else {
        null
    }
}

/**
 * 提示用户授予本地网络权限的卡片, 点击后发起申请
 *
 * 是否显示由调用方根据 [permissionState] 决定, 在 LazyColumn 里按条件添加 item, 避免留下空白间距
 */
@Composable
fun LocalNetworkPermissionCard(
    permissionState: PermissionState,
    modifier: Modifier = Modifier,
    manageDialog: Boolean = true,
    onClick: () -> Unit = { permissionState.requestPermissions() },
) {
    PermissionRequestCard(
        permissionState = permissionState,
        icon = HugeIcons.Alert01,
        title = stringResource(R.string.permission_local_network),
        description = stringResource(R.string.permission_local_network_desc),
        modifier = modifier,
        manageDialog = manageDialog,
        onClick = onClick,
    )
}

/**
 * 提示用户授予通知权限的卡片, 没有通知权限时前台服务（生成、Web 服务器等）的通知不会显示
 *
 * 是否显示由调用方根据 [permissionState] 决定, 在 LazyColumn 里按条件添加 item, 避免留下空白间距
 */
@Composable
fun NotificationPermissionCard(
    permissionState: PermissionState,
    modifier: Modifier = Modifier,
    manageDialog: Boolean = true,
    onClick: () -> Unit = { permissionState.requestPermissions() },
) {
    PermissionRequestCard(
        permissionState = permissionState,
        icon = HugeIcons.Notification01,
        title = stringResource(R.string.permission_notification),
        description = stringResource(R.string.permission_notification_card_desc),
        modifier = modifier,
        manageDialog = manageDialog,
        onClick = onClick,
    )
}

@Composable
private fun PermissionRequestCard(
    permissionState: PermissionState,
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    manageDialog: Boolean = true,
    onClick: () -> Unit = { permissionState.requestPermissions() },
) {
    if (manageDialog) {
        PermissionManager(permissionState = permissionState)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CustomColors.listItemColors.containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
