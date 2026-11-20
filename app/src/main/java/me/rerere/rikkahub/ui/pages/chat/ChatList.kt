package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.withConversation
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.ui.ErrorCardsDisplay
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.ui.theme.ChatFontProvider
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.ListSelectableItem

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

/** [FORK] 概览页跳转请求：nonce 保证同一个 index 重复点击时也能重新触发滚动 */
data class JumpRequest(val index: Int, val nonce: Int)

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    previewListState: LazyListState,
    selectionState: ChatSelectionState,
    loading: Boolean,
    compressing: Boolean = false,
    processingStatus: String? = null,
    previewMode: Boolean,
    onPreviewModeChange: (Boolean) -> Unit,
    settings: Settings,
    hazeState: HazeState,
    // [FORK] 输入区按钮手动展开的消息跳转悬浮窗
    showJumper: Boolean = false,
    onDismissJumper: () -> Unit = {},
    // [FORK] 概览页跳转请求，非 null 时先禁用自动贴底再滚动
    jumpRequest: JumpRequest? = null,
    errors: List<ChatError> = emptyList(),
    onDismissError: (Uuid) -> Unit = {},
    onClearAllErrors: () -> Unit = {},
    onRegenerate: (UIMessage) -> Unit = {},
    onEdit: (UIMessage) -> Unit = {},
    onForkMessage: (UIMessage) -> Unit = {},
    onDelete: (UIMessage) -> Unit = {},
    onDeleteBeforeMessage: (UIMessage) -> Unit = {},
    onDeleteAfterMessage: (UIMessage) -> Unit = {},
    onUpdateMessage: (MessageNode) -> Unit = {},
    onClickSuggestion: (String) -> Unit = {},
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onJumpToMessage: (Int) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
    // [FORK] 固定到上下文：仅当有上下文限制时才显示此选项
    onTogglePin: ((MessageNode) -> Unit)? = null,
) {
    val previewProgress = rememberPreviewSwipeProgress(previewMode)
    val scope = rememberCoroutineScope()
    val currentPreviewMode by rememberUpdatedState(previewMode)
    val currentOnPreviewModeChange by rememberUpdatedState(onPreviewModeChange)
    val currentConversation by rememberUpdatedState(conversation)
    val layoutDirection = LocalLayoutDirection.current
    val layoutDirectionSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    // 概览页只在露出来的时候组合，平时不占资源
    val showPreview by remember { derivedStateOf { previewProgress.value > 0f } }
    val showNormal by remember { derivedStateOf { previewProgress.value < 1f } }
    // [FORK] 概览筛选在切走再回来时保持（按会话分开记忆）
    var previewFilter by rememberSaveable(conversation.id) { mutableStateOf(PreviewFilter.ALL) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .previewSwipeGesture(
                progress = previewProgress,
                scope = scope,
                layoutDirection = layoutDirection,
                canEnterPreview = { currentConversation.messageNodes.isNotEmpty() },
                isPreviewMode = { currentPreviewMode },
                onPreviewModeChange = { currentOnPreviewModeChange(it) },
            ),
    ) {
        // 两页像翻页一样整体平移：聊天页向左滑出，概览页从右侧跟进
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = previewProgress.value
                    translationX = -p * size.width * layoutDirectionSign
                    alpha = if (showNormal) 1f - p * 0.4f else 0f
                },
        ) {
            ChatListNormal(
                innerPadding = innerPadding,
                conversation = conversation,
                state = state,
                loading = loading,
                compressing = compressing,
                processingStatus = processingStatus,
                settings = settings,
                hazeState = hazeState,
                showJumper = showJumper,
                onDismissJumper = onDismissJumper,
                jumpRequest = jumpRequest,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onForkMessage = onForkMessage,
                onDelete = onDelete,
                onDeleteBeforeMessage = onDeleteBeforeMessage,
                onDeleteAfterMessage = onDeleteAfterMessage,
                onUpdateMessage = onUpdateMessage,
                onClickSuggestion = onClickSuggestion,
                onTranslate = onTranslate,
                onClearTranslation = onClearTranslation,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onToggleFavorite = onToggleFavorite,
                onConversationSystemPromptChange = onConversationSystemPromptChange,
                selectionState = selectionState,
                onTogglePin = onTogglePin,
            )
        }

        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = previewProgress.value
                        translationX = (1f - p) * size.width * layoutDirectionSign
                        alpha = 0.4f + p * 0.6f
                    },
            ) {
                ChatListPreview(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    chatListState = state,
                    listState = previewListState,
                    settings = settings,
                    hazeState = hazeState,
                    onJumpToMessage = onJumpToMessage,
                    previewFilter = previewFilter,
                    onPreviewFilterChange = { previewFilter = it },
                )
            }
        }
    }
}

// 顶部渐隐：消息滚到标题栏下面时逐渐透明，从状态栏一直淡出到标题栏下沿之外，保证标题可读。
// 让内容自己变透明而不是盖一层背景色，背景图和动态渐变背景才不会被盖住。
internal fun Modifier.topFadingEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fadeHeight = height.toPx()
        val brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.Black.copy(alpha = 0.15f),
            1f to Color.Black,
            startY = 0f,
            endY = fadeHeight,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush, size = Size(size.width, fadeHeight), blendMode = BlendMode.DstIn)
        }
    }

@Composable
private fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    compressing: Boolean,
    processingStatus: String? = null,
    settings: Settings,
    hazeState: HazeState,
    showJumper: Boolean = false,
    onDismissJumper: () -> Unit = {},
    jumpRequest: JumpRequest? = null,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onDeleteBeforeMessage: (UIMessage) -> Unit = {},
    onDeleteAfterMessage: (UIMessage) -> Unit = {},
    onUpdateMessage: (MessageNode) -> Unit,
    onClickSuggestion: (String) -> Unit,
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)?,
    onClearTranslation: (UIMessage) -> Unit,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
    selectionState: ChatSelectionState,
    onTogglePin: ((MessageNode) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val loadingState by rememberUpdatedState(loading)
    var isRecentScroll by remember { mutableStateOf(false) }
    var isUserDragging by remember { mutableStateOf(false) }
    // 如果入场时就带有跳转请求，直接以 false 初始化，避免贴底 LaunchedEffect 在跳转请求处理之前抢先触发
    var shouldAutoFollow by remember { mutableStateOf(jumpRequest == null) }
    val density = LocalDensity.current
    val activity = LocalContext.current as? me.rerere.rikkahub.RouteActivity

    // 处理概览页跳转请求：先禁用自动贴底，再瞬间跳到目标位置（不带动画）
    LaunchedEffect(jumpRequest) {
        jumpRequest?.let { req ->
            shouldAutoFollow = false
            state.scrollToItem(req.index)
        }
    }

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Boolean = { isVolumeUp ->
            if (settings.displaySetting.enableVolumeKeyScroll) {
                // 列表视口包含标题栏和输入区遮住的部分，翻页时只按可见区域计算
                val coveredPx = with(density) {
                    (innerPadding.calculateTopPadding() + 32.dp + innerPadding.calculateBottomPadding()).toPx()
                }
                val scrollAmount = (state.layoutInfo.viewportSize.height - coveredPx) *
                    settings.displaySetting.volumeKeyScrollRatio
                scope.launch { state.scrollBy(if (isVolumeUp) -scrollAmount else scrollAmount) }
                true
            } else false
        }
        activity?.volumeKeyListeners?.add(listener)
        onDispose {
            activity?.volumeKeyListeners?.remove(listener)
        }
    }

    // 聊天选择
    val selectedItems = selectionState.selectedItems
    var showExportSheet by remember { mutableStateOf(false) }

    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    val assistant = remember(
        settings.assistants,
        conversation.assistantId,
        conversation.config,
        conversation.modeInjectionIds,
        conversation.lorebookIds,
    ) {
        // 工作区等配置在会话开始后以会话上固定的为准
        settings.getAssistantById(conversation.assistantId)?.withConversation(conversation)
    }
    val modelById = remember(settings.providers) {
        settings.providers
            .flatMap { it.models }
            .associateBy { it.id }
    }
    // [FORK] 提问消息的下标，快速跳转的上下按钮在提问之间跳转
    val userMessageIndices = remember(conversation.messageNodes) {
        conversation.messageNodes.mapIndexedNotNull { index, node ->
            index.takeIf { node.role == MessageRole.USER }
        }
    }

    // [FORK] 分别记录 USER 和 ASSISTANT 角色的最后一条 node 的 index，用于控制重试按钮显示
    val lastUserMessageIndex = conversation.messageNodes.indexOfLast {
        it.role == MessageRole.USER
    }
    val lastAssistantMessageIndex = conversation.messageNodes.indexOfLast {
        it.role == MessageRole.ASSISTANT
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LaunchedEffect(state) {
            state.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> isUserDragging = true
                    is DragInteraction.Stop, is DragInteraction.Cancel -> isUserDragging = false
                }
            }
        }

        // 自动滚动到底部
        if (settings.displaySetting.enableAutoScroll) {
            LaunchedEffect(state, isUserDragging) {
                snapshotFlow {
                    Pair(isUserDragging, state.canScrollForward)
                }.collect { (dragging, canScrollForward) ->
                    if (dragging && canScrollForward) {
                        shouldAutoFollow = false
                    } else if (!canScrollForward) {
                        shouldAutoFollow = true
                    }
                }
            }

            LaunchedEffect(state, isUserDragging, shouldAutoFollow, loadingState, conversation) {
                snapshotFlow {
                    Triple(
                        state.layoutInfo.totalItemsCount,
                        conversation.messageNodes.lastOrNull()?.currentMessage?.toText()?.length ?: 0,
                        state.isScrollInProgress,
                    )
                }.collect { (totalItems, _, inProgress) ->
                    if (!isUserDragging && !inProgress && loadingState && shouldAutoFollow && totalItems > 0) {
                        state.requestScrollToItem(totalItems - 1)
                    }
                }
            }
        }

        // 判断最近是否滚动
        LaunchedEffect(state.isScrollInProgress) {
            if (state.isScrollInProgress) {
                isRecentScroll = true
                delay(1500)
                isRecentScroll = false
            } else {
                delay(1500)
                isRecentScroll = false
            }
        }

        ChatFontProvider(displaySetting = settings.displaySetting) {
            LazyColumn(
                state = state,
                // 顶部留白放进 contentPadding，让消息可以滚动到透明标题栏下面
                contentPadding = PaddingValues(16.dp) + PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 32.dp + innerPadding.calculateBottomPadding(),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .topFadingEdge(innerPadding.calculateTopPadding() + 24.dp)
                    .hazeSource(state = hazeState),
            ) {
                itemsIndexed(
                    items = conversation.messageNodes,
                    key = { index, item -> item.id },
                ) { index, node ->
                    // 跳转到配对消息时先暂停自动贴底，防止跳过去又被拉回底部
                    val questionScrollAction: (() -> Unit)? = remember(node.id, index) {
                        if (node.currentMessage.role == MessageRole.ASSISTANT && index > 0) {
                            {
                                shouldAutoFollow = false
                                scope.launch { state.animateScrollToItem(index - 1) }
                            }
                        } else {
                            null
                        }
                    }
                    val answerScrollAction: (() -> Unit)? =
                        remember(node.id, index, conversation.messageNodes.size) {
                        if (node.currentMessage.role == MessageRole.USER && index < conversation.messageNodes.lastIndex) {
                            {
                                shouldAutoFollow = false
                                scope.launch { state.animateScrollToItem(index + 1) }
                            }
                        } else {
                            null
                        }
                    }
                    Column {
                        ListSelectableItem(
                            key = node.id,
                            onSelectChange = {
                                if (!selectedItems.contains(node.id)) {
                                    selectedItems.add(node.id)
                                } else {
                                    selectedItems.remove(node.id)
                                }
                            },
                            selectedKeys = selectedItems,
                            enabled = selectionState.selecting,
                        ) {
                            ChatMessage(
                                node = node,
                                model = node.currentMessage.modelId?.let(modelById::get)
                                    ?: node.currentMessage.snapshotModel(),
                                assistant = assistant,
                                loading = loading && !compressing && index == conversation.messageNodes.lastIndex,
                                onRegenerate = {
                                    onRegenerate(node.currentMessage)
                                },
                                onEdit = {
                                    onEdit(node.currentMessage)
                                },
                                onFork = {
                                    onForkMessage(node.currentMessage)
                                },
                                onDelete = {
                                    onDelete(node.currentMessage)
                                },
                                onDeleteBefore = {
                                    onDeleteBeforeMessage(node.currentMessage)
                                },
                                onDeleteAfter = {
                                    onDeleteAfterMessage(node.currentMessage)
                                },
                                onShare = {
                                    // [FORK] 默认只选中该轮问答，整段对话可在多选工具栏里「全选」
                                    selectionState.start(
                                        defaultShareNodeIds(conversation.messageNodes, index)
                                    )
                                },
                                onUpdate = {
                                    onUpdateMessage(it)
                                },
                                isFavorite = node.favoriteMessageId == node.currentMessage.id,
                                onToggleFavorite = {
                                    onToggleFavorite?.invoke(node)
                                },
                                onTranslate = onTranslate,
                                onClearTranslation = onClearTranslation,
                                onToolApproval = onToolApproval,
                                onToolAnswer = onToolAnswer,
                                lastMessage = when (node.role) {
                                    MessageRole.USER -> index == lastUserMessageIndex
                                    // 只有当该回答确实是最后一条提问的回答（其后没有新的提问）时才允许重试，
                                    // 避免用户发起新提问但模型未响应/被中断时，上一条问答的回答仍显示重试按钮
                                    else -> index == lastAssistantMessageIndex &&
                                        lastAssistantMessageIndex > lastUserMessageIndex
                                },
                                onScrollToQuestion = questionScrollAction,
                                onScrollToAnswer = answerScrollAction,
                                isPinned = node.isPinned,
                                onTogglePin = onTogglePin?.let { togglePin -> { togglePin(node) } },
                            )
                        }
                    }
                }

                if (!loading && assistant?.allowConversationSystemPrompt == true && onConversationSystemPromptChange != null) {
                    item(key = "ConversationSystemPrompt") {
                        ConversationSystemPromptButton(
                            value = conversation.customSystemPrompt,
                            onSystemPromptChange = onConversationSystemPromptChange,
                        )
                    }
                }

                // 压缩时也用这一行，processingStatus 显示的是压缩中
                if (loading) {
                    item(LoadingIndicatorKey) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ContainedLoadingIndicator(
                                modifier = Modifier.size(28.dp)
                            )
                            AnimatedVisibility(
                                visible = processingStatus != null,
                            ) {
                                Text(
                                    text = processingStatus ?: "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 为了能正确滚动到这
                item(ScrollBottomKey) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 错误消息卡片
            ErrorCardsDisplay(
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
            )

            // 完成选择
            ChatSelectionToolbar(
                state = selectionState,
                allIds = conversation.messageNodes.map { it.id },
                onConfirm = {
                    selectionState.selecting = false
                    if (conversation.messageNodes.any { it.id in selectedItems }) {
                        showExportSheet = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(48).dp),
            )

            // 导出对话框
            ChatExportSheet(
                visible = showExportSheet,
                onDismissRequest = {
                    showExportSheet = false
                    selectedItems.clear()
                },
                conversation = conversation,
                selectedMessages = conversation.messageNodes.filter { it.id in selectedItems }
                    .map { it.currentMessage }
            )

            val captureProgress = LocalScrollCaptureInProgress.current

            // 消息快速跳转
            MessageJumper(
                show = (showJumper || (isRecentScroll && !state.isScrollInProgress)) &&
                    settings.displaySetting.showMessageJumper &&
                    !captureProgress,
                onLeft = settings.displaySetting.messageJumperOnLeft,
                scope = scope,
                state = state,
                userMessageIndices = userMessageIndices,
                onDismissJumper = onDismissJumper,
                onDisableAutoFollow = { shouldAutoFollow = false },
            )

            // Suggestion
            if (conversation.chatSuggestions.isNotEmpty() && !captureProgress) {
                ChatSuggestionsRow(
                    conversation = conversation,
                    onClickSuggestion = onClickSuggestion,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
