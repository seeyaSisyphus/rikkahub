package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R

/**
 * 列表 item 的次要操作（复制、导出、重命名、删除等）。
 *
 * 约定：点击 item 本身执行主操作（进入详情/编辑），其余操作统一放进 [ItemActionMenu]；
 * 破坏性操作（删除）放在最后并标记 [destructive]，由调用方负责二次确认或提供撤销。
 */
@Immutable
data class ItemAction(
    val text: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun ItemActionMenu(
    actions: List<ItemAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = HugeIcons.MoreVertical,
                contentDescription = stringResource(R.string.more_options),
            )
        }
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEachIndexed { index, action ->
                if (action.destructive && index > 0 && !actions[index - 1].destructive) {
                    AppMenuDivider()
                }
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = action.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = action.enabled,
                    colors = appMenuItemColors(destructive = action.destructive),
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
