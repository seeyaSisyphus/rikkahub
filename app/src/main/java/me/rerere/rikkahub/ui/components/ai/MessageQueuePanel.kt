package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.uuid.Uuid
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Attachment01
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DragDropVertical
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.InboxItem
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.service.QueuedNotice
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val QueueMotionEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * 输入框上方的等待发送队列行条（参考 CyberSlots 移动端 QueueRailPill 样式与交互）。
 * 支持折叠/展开、手柄拖拽排序、点击编辑回填到主输入框以及移除。
 */
@Composable
internal fun MessageQueuePanel(
    state: InboxState,
    onRemove: (Uuid) -> Unit,
    onMove: (Uuid, Uuid) -> Unit,
    onEdit: (Uuid) -> Unit,
    onResume: () -> Unit,
) {
    val isDark = LocalDarkMode.current
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(true) }
    var prevCount by remember { mutableIntStateOf(state.items.size) }
    val bumpAlpha = remember { Animatable(0f) }

    LaunchedEffect(state.items.size) {
        val current = state.items.size
        if (current > prevCount) {
            if (prevCount == 0) expanded = true
            bumpAlpha.snapTo(1f)
            bumpAlpha.animateTo(0f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
        prevCount = current
    }

    AnimatedVisibility(
        visible = state.items.isNotEmpty(),
        enter = fadeIn(tween(200)) + expandVertically(tween(220, easing = QueueMotionEasing)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(200, easing = QueueMotionEasing)),
    ) {
        val panelColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.50f else 0.68f)
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 90f else 0f,
            animationSpec = tween(durationMillis = 200, easing = QueueMotionEasing),
            label = "queue_chevron_rotation",
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = panelColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_message_queue"),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val firstSummary = state.items.firstOrNull()?.let { summarizeInboxItem(it) }.orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * bumpAlpha.value))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = !expanded
                        }
                        .defaultMinSize(minHeight = 34.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Clock01,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (state.paused) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = stringResource(
                            if (state.paused) R.string.chat_page_queue_paused_count
                            else R.string.chat_page_queue_pending_count,
                            state.items.size,
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (state.paused) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                    if (!expanded && firstSummary.isNotEmpty()) {
                        Text(
                            text = firstSummary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (state.paused) {
                        Surface(
                            onClick = onResume,
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            tonalElevation = 0.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.chat_page_queue_resume),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = QueueMotionEasing)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200, easing = QueueMotionEasing)),
                ) {
                    QueueItemList(
                        items = state.items,
                        messageCount = state.messages.size,
                        onMove = onMove,
                        onEdit = onEdit,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemList(
    items: List<InboxItem>,
    messageCount: Int,
    onMove: (Uuid, Uuid) -> Unit,
    onEdit: (Uuid) -> Unit,
    onRemove: (Uuid) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val localItems = remember { mutableStateListOf<InboxItem>().apply { addAll(items) } }
    val currentOnMove by rememberUpdatedState(onMove)
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = localItems.indexOfFirst { it.id.toString() == from.key }
        val toIndex = localItems.indexOfFirst { it.id.toString() == to.key }
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return@rememberReorderableLazyListState
        val fromItem = localItems[fromIndex]
        val toItem = localItems[toIndex]
        if (fromItem is QueuedMessage && toItem is QueuedMessage) {
            localItems.add(toIndex, localItems.removeAt(fromIndex))
            currentOnMove(fromItem.id, toItem.id)
        }
    }
    LaunchedEffect(items, reorderState.isAnyItemDragging) {
        val idsChanged = localItems.map { it.id }.toSet() != items.map { it.id }.toSet()
        if ((!reorderState.isAnyItemDragging || idsChanged) && localItems.toList() != items) {
            localItems.clear()
            localItems.addAll(items)
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp),
    ) {
        items(localItems, key = { it.id.toString() }) { item ->
            if (item is QueuedNotice) {
                QueuedNoticeRow(
                    notice = item,
                    onRemove = { onRemove(item.id) },
                    modifier = Modifier.animateItem(),
                )
            } else {
                val message = item as QueuedMessage
                ReorderableItem(reorderState, key = message.id.toString()) { isDragging ->
                    val canDrag = messageCount > 1
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 0.98f else 1f,
                        label = "queue_drag_scale",
                    )
                    QueuedMessageRow(
                        message = message,
                        isDragging = isDragging,
                        dragModifier = Modifier.draggableHandle(
                            enabled = canDrag,
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        ),
                        canDrag = canDrag,
                        onEdit = { onEdit(message.id) },
                        onRemove = { onRemove(message.id) },
                        modifier = Modifier
                            .animateItem()
                            .graphicsLayer {
                                scaleX = dragScale
                                scaleY = dragScale
                                alpha = if (isDragging) 0.80f else 1f
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedNoticeRow(
    notice: QueuedNotice,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 28.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Notification01,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = notice.notice.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        QueueActionIconButton(
            icon = HugeIcons.Delete01,
            contentDescription = stringResource(R.string.chat_page_queue_remove),
            onClick = onRemove,
        )
    }
}

@Composable
private fun QueuedMessageRow(
    message: QueuedMessage,
    isDragging: Boolean,
    dragModifier: Modifier,
    canDrag: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textPreview = remember(message.parts) { extractMessagePreviewText(message.parts) }
    val imageCount = remember(message.parts) { message.parts.count { it is UIMessagePart.Image } }
    val documentCount = remember(message.parts) { message.parts.count { it is UIMessagePart.Document } }
    val audioCount = remember(message.parts) { message.parts.count { it is UIMessagePart.Audio } }
    val videoCount = remember(message.parts) { message.parts.count { it is UIMessagePart.Video } }
    val fallbackLabel = when {
        imageCount > 0 -> stringResource(R.string.chat_page_queue_image)
        documentCount > 0 -> stringResource(R.string.chat_page_queue_file)
        audioCount > 0 -> stringResource(R.string.chat_page_queue_audio)
        videoCount > 0 -> stringResource(R.string.chat_page_queue_video)
        else -> ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .defaultMinSize(minHeight = 28.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = dragModifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.DragDropVertical,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (canDrag) 0.68f else 0.32f,
                ),
            )
        }
        Text(
            text = textPreview.ifEmpty { fallbackLabel },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = if (textPreview.isNotEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (imageCount > 0) {
            QueueAttachmentBadge(icon = HugeIcons.Attachment01, count = imageCount)
        }
        if (documentCount > 0) {
            QueueAttachmentBadge(icon = HugeIcons.File02, count = documentCount)
        }
        if (audioCount > 0) {
            QueueAttachmentBadge(icon = HugeIcons.MusicNote03, count = audioCount)
        }
        if (videoCount > 0) {
            QueueAttachmentBadge(icon = HugeIcons.Video01, count = videoCount)
        }
        QueueActionIconButton(
            icon = HugeIcons.PencilEdit01,
            contentDescription = stringResource(R.string.edit),
            onClick = onEdit,
        )
        QueueActionIconButton(
            icon = HugeIcons.Delete01,
            contentDescription = stringResource(R.string.chat_page_queue_remove),
            onClick = onRemove,
        )
    }
}

@Composable
private fun QueueAttachmentBadge(
    icon: ImageVector,
    count: Int,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun summarizeInboxItem(item: InboxItem): String = when (item) {
    is QueuedNotice -> item.notice.title
    is QueuedMessage -> {
        val text = extractMessagePreviewText(item.parts)
        if (text.isNotEmpty()) {
            text
        } else {
            when {
                item.parts.any { it is UIMessagePart.Image } -> stringResource(R.string.chat_page_queue_image)
                item.parts.any { it is UIMessagePart.Document } -> stringResource(R.string.chat_page_queue_file)
                item.parts.any { it is UIMessagePart.Audio } -> stringResource(R.string.chat_page_queue_audio)
                item.parts.any { it is UIMessagePart.Video } -> stringResource(R.string.chat_page_queue_video)
                else -> ""
            }
        }
    }
}

private fun extractMessagePreviewText(parts: List<UIMessagePart>): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString(" ") { it.text }
        .replace(Regex("\\s+"), " ")
        .trim()
