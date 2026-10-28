package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AirplaneTakeOff02
import me.rerere.hugeicons.stroke.CableCar
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DragDropVertical
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Pin02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Sword02
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.toMessageTimeString
import me.rerere.ui.components.RikkaConfirmDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onScrollToQuestion: (() -> Unit)? = null,
    onScrollToAnswer: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    // 是否是对话中最后一条消息，只有最后一条才显示重试按钮
    isLastMessage: Boolean = true,
    // [FORK] 固定到上下文
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    val isAssistant = message.role == MessageRole.ASSISTANT

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // [FORK] 跳转按钮：回答向上跳到提问，提问向下跳到回答
        if (isAssistant && onScrollToQuestion != null) {
            ChatMessageActionButton(
                icon = HugeIcons.AirplaneTakeOff02,
                contentDescription = "Scroll to question",
                onClick = onScrollToQuestion,
            )
        }
        if (!isAssistant && onScrollToAnswer != null) {
            ChatMessageActionButton(
                icon = HugeIcons.CableCar,
                contentDescription = "Scroll to answer",
                onClick = onScrollToAnswer,
            )
        }

        ChatMessageActionButton(
            icon = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            onClick = { onCopy?.invoke() ?: context.copyMessageToClipboard(message) },
        )

        // 只有最后一条消息才显示重试按钮
        if (isLastMessage) {
            ChatMessageActionButton(
                icon = HugeIcons.Refresh03,
                contentDescription = stringResource(R.string.regenerate),
                onClick = {
                    if (message.role == MessageRole.USER) {
                        showRegenerateConfirm = true
                    } else {
                        onRegenerate()
                    }
                },
            )
        }

        if (isAssistant) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            // 朗读中换成带底色的方角按钮
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconToggleButton(
                    checked = isSpeaking,
                    onCheckedChange = { speak ->
                        if (speak) {
                            val text = message.toText()
                            var textToSpeak = text
                            if (settings.displaySetting.ttsOnlyReadQuoted) {
                                textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                            }
                            if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                                textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                            }
                            tts.speak(textToSpeak)
                        } else {
                            tts.stop()
                        }
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
                    enabled = isAvailable,
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                        contentDescription = stringResource(R.string.tts),
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
            }

            // [FORK] 收藏：已收藏/未收藏图标与颜色区分，点击时弹跳动画
            if (onToggleFavorite != null) {
                var bouncing by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (bouncing) 1.45f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "favoriteScale"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 250),
                    label = "favoriteColor"
                )
                LaunchedEffect(bouncing) {
                    if (bouncing) {
                        delay(120)
                        bouncing = false
                    }
                }
                ChatMessageActionButton(
                    icon = if (isFavorite) HugeIcons.InLove else HugeIcons.Favourite,
                    contentDescription = "Favourite",
                    onClick = {
                        bouncing = true
                        onToggleFavorite()
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    contentColor = iconColor,
                )
            }

            // Battle Mode 标识图标：battle 节点（多条来自不同模型的回答）显示，点击打开排序面板
            val isBattleMode = node.isBattleNodeEffective
            if (isBattleMode) {
                var showBattleSortSheet by remember { mutableStateOf(false) }
                ChatMessageActionButton(
                    icon = HugeIcons.Sword02,
                    contentDescription = "Battle Mode",
                    onClick = { showBattleSortSheet = true },
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                if (showBattleSortSheet) {
                    BattleSortSheet(
                        node = node,
                        onDismissRequest = { showBattleSortSheet = false },
                        onUpdate = onUpdate
                    )
                }
            }
        }

        // [FORK] 固定到上下文（回答：放在收藏右边；提问：放在复制右边），选中时图标变色
        if (onTogglePin != null) {
            val pinColor by animateColorAsState(
                targetValue = if (isPinned) Color(0xFF1E88E5) else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 250),
                label = "pinColor"
            )
            ChatMessageActionButton(
                icon = HugeIcons.Pin02,
                contentDescription = "Pin to context",
                onClick = onTogglePin,
                contentColor = pinColor,
            )
        }

        ChatMessageActionButton(
            icon = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            onClick = onOpenActionSheet,
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        if (settings.displaySetting.showDateTimeInMessage) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    // Regenerate confirmation dialog
    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

private val ActionIconSize = 18.dp

// 消息下方的小号图标按钮，按下时圆形收成方角
@Composable
internal fun ChatMessageActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // [FORK] 部分按钮（收藏/固定/对战）需要在选中时变色
    contentColor: Color? = null,
) {
    // 按钮排得密，不让 48dp 的最小触控尺寸把间距撑开
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(IconButtonDefaults.extraSmallContainerSize()),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
            )
        }
    }
}

private class MessageSheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onDeleteBefore: () -> Unit,
    onDeleteAfter: () -> Unit,
    onCopy: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showDeleteBeforeConfirm by remember { mutableStateOf(false) }
    var showDeleteAfterConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        // WebView Preview (only show if message has text content)
        val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
            .any { it.text.isNotBlank() }
        val actions = buildList {
            add(MessageSheetAction(HugeIcons.Copy01, stringResource(R.string.copy), onClick = onCopy))
            add(MessageSheetAction(HugeIcons.TextSelection, stringResource(R.string.select_and_copy), onClick = onSelectAndCopy))
            if (hasTextContent) {
                add(MessageSheetAction(HugeIcons.WebDesign01, stringResource(R.string.render_with_webview), onClick = onWebViewPreview))
            }
            add(MessageSheetAction(HugeIcons.Edit01, stringResource(R.string.edit), onClick = onEdit))
            add(MessageSheetAction(HugeIcons.Share04, stringResource(R.string.share), onClick = onShare))
            add(MessageSheetAction(HugeIcons.GitFork, stringResource(R.string.create_fork), onClick = onFork))
            if (onTranslate != null) {
                add(
                    MessageSheetAction(
                        icon = HugeIcons.Translate,
                        label = stringResource(R.string.translate),
                        onClick = { showTranslateDialog = true },
                    )
                )
            }
            if (onToggleFavorite != null) {
                add(
                    MessageSheetAction(
                        icon = HugeIcons.FavouriteCircle,
                        label = stringResource(
                            if (isFavorite) R.string.chat_message_remove_favorite
                            else R.string.chat_message_add_favorite
                        ),
                        onClick = onToggleFavorite,
                    )
                )
            }
            add(MessageSheetAction(HugeIcons.Delete01, stringResource(R.string.delete), destructive = true, onClick = onDelete))
            add(
                MessageSheetAction(
                    HugeIcons.Delete01,
                    stringResource(R.string.delete_messages_before),
                    destructive = true,
                    onClick = { showDeleteBeforeConfirm = true },
                )
            )
            add(
                MessageSheetAction(
                    HugeIcons.Delete01,
                    stringResource(R.string.delete_messages_after),
                    destructive = true,
                    onClick = { showDeleteAfterConfirm = true },
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                actions.fastForEachIndexed { index, action ->
                    SegmentedListItem(
                        onClick = {
                            onDismissRequest()
                            action.onClick()
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = actions.size),
                        colors = if (action.destructive) {
                            ListItemDefaults.segmentedColors(
                                contentColor = MaterialTheme.colorScheme.error,
                                leadingContentColor = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            ListItemDefaults.segmentedColors()
                        },
                        leadingContent = {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(action.label)
                    }
                }
            }

            // Message Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                    if (model != null) {
                        Text(model.displayName)
                    }
                }
            }
        }
    }

    // Delete before confirmation dialog
    RikkaConfirmDialog(
        show = showDeleteBeforeConfirm,
        title = stringResource(R.string.delete_messages_before),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteBeforeConfirm = false
            onDeleteBefore()
        },
        onDismiss = { showDeleteBeforeConfirm = false },
        text = { Text(stringResource(R.string.delete_messages_before_confirm)) }
    )

    // Delete after confirmation dialog
    RikkaConfirmDialog(
        show = showDeleteAfterConfirm,
        title = stringResource(R.string.delete_messages_after),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteAfterConfirm = false
            onDeleteAfter()
        },
        onDismiss = { showDeleteAfterConfirm = false },
        text = { Text(stringResource(R.string.delete_messages_after_confirm)) }
    )

    // Translation dialog
    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }
}

/**
 * Battle Mode 排序面板：展示所有模型的回答，支持长按拖拽调整顺序
 */
@Composable
fun BattleSortSheet(
    node: MessageNode,
    onDismissRequest: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
) {
    val settings = LocalSettings.current
    // 记录当前正在显示的消息 id，排序后 selectIndex 跟随它
    val currentMsgId = node.messages.getOrNull(node.selectIndex)?.id

    var messages by remember(node.messages) { mutableStateOf(node.messages) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = messages.indexOfFirst { it.id.toString() == from.key }
        val toIdx = messages.indexOfFirst { it.id.toString() == to.key }
        if (fromIdx != -1 && toIdx != -1) {
            messages = messages.toMutableList().apply {
                add(toIdx, removeAt(fromIdx))
            }
            val newSelectIndex = messages.indexOfFirst { it.id == currentMsgId }.coerceAtLeast(0)
            onUpdate(node.copy(messages = messages, selectIndex = newSelectIndex))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Sword02,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Battle 排序",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = "长按拖拽可调整回答顺序，排在前面的将优先显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(messages, key = { it.id.toString() }) { msg ->
                    ReorderableItem(
                        state = reorderState,
                        key = msg.id.toString(),
                    ) { isDragging ->
                        val model: Model? = msg.modelId?.let {
                            settings.findModelById(it)
                        }
                        val isSelected = msg.id == currentMsgId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = if (isDragging) 1.03f else 1f
                                    scaleY = if (isDragging) 1.03f else 1f
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // 模型图标
                                AutoAIIcon(
                                    name = model?.displayName ?: "",
                                    modifier = Modifier.size(28.dp),
                                )
                                // 模型名称
                                Text(
                                    text = model?.displayName ?: "Unknown",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 拖拽把手（长按触发）
                                Icon(
                                    imageVector = HugeIcons.DragDropVertical,
                                    contentDescription = "拖拽排序",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .longPressDraggableHandle(),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
