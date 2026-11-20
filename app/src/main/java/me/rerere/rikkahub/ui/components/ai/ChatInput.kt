package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowExpand01
import me.rerere.hugeicons.stroke.ArrowShrink02
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ArrowUpDown
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.service.InboxState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.ui.AppDropdownMenu
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.rememberAutoFocusRequester
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.data.ai.mcp.McpManager
import org.koin.compose.koinInject
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/** 参考 CyberSlots 移动端输入框原地上浮/回落的阻尼曲线 */
private val ComposerExpandEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

internal fun isStopGenerationAction(loading: Boolean, inputEmpty: Boolean): Boolean = loading && inputEmpty

@Composable
fun ChatInput(
    state: ChatInputState,
    // 每次自增都会让输入框重新聚焦并弹出键盘（编辑消息 / 点建议 / 接收分享文本）
    autoFocusRequest: Int = 0,
    // 聚焦完成后由调用方把 autoFocusRequest 复位，避免重建组合时重放旧请求
    onAutoFocusHandled: () -> Unit = {},
    // 快捷消息等组件插入文本后，请求一次输入框聚焦
    onRequestComposerFocus: () -> Unit = {},
    loading: Boolean,
    settings: Settings,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onMoreClick: () -> Unit,
    onJumperClick: () -> Unit = {},
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    // [FORK] 编辑态下点击发送是否会触发重试；false 时发送按钮显示保存图标
    editWillRegenerate: Boolean = false,
    messageQueue: InboxState = InboxState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    runningBackgroundTasks: Int = 0,
    onShowBackgroundTasks: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
    onExpandedHeightDeltaChange: (Dp) -> Unit = {},
    onFloatingChange: (Boolean) -> Unit = {},
) {
    val toaster = LocalToaster.current
    val isDark = LocalDarkMode.current
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val containerShape = RoundedCornerShape(24.dp)
    val modelListState = rememberModelListState(
        modelId = chatModel?.id,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    var isExpanded by remember { mutableStateOf(false) }
    val expandFraction by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isExpanded) 300 else 260,
            easing = ComposerExpandEasing,
        ),
        label = "composer_expand_fraction",
    )
    val isFloating = isExpanded || expandFraction > 0.001f

    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    fun sendMessage() {
        isExpanded = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (isStopGenerationAction(loading, state.isEmpty())) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        isExpanded = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (isStopGenerationAction(loading, state.isEmpty())) onCancelClick() else onLongSendClick()
    }

    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    // ASR 启动时快照原有文字，回调中用 transcript 追加到其后
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    // 收集 ASR controller 的错误消息并弹 toast（去重 + 跳过初始值，避免重组时重复弹出）
    val asrFlow = asr.state
    LaunchedEffect(asrFlow) {
        asrFlow
            .map { it.errorMessage }
            .distinctUntilChanged()
            .drop(1)
            .collect { message ->
                if (!message.isNullOrBlank()) {
                    toaster.show(message = message, type = ToastType.Error)
                }
            }
    }

    // 原地放大/缩小高度插值计算（参考 CyberSlots 移动端输入框原地上浮状态机）
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val configuration = LocalConfiguration.current
    var collapsedCardHeightPx by remember { mutableIntStateOf(0) }
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    var textRowHeightPx by remember { mutableIntStateOf(0) }
    var queuePanelHeightPx by remember { mutableIntStateOf(0) }

    val statusBarsInsets = WindowInsets.statusBars
    val imeInsets = WindowInsets.ime
    val navigationBarsInsets = WindowInsets.navigationBars

    val currentCardHeightPx: Int
    val extraHeightDp: Dp
    if (isFloating) {
        val windowHeightPx = windowInfo.containerSize.height.takeIf { it > 0 }
            ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
        val statusBarsTopPx = statusBarsInsets.getTop(density)
        val topReservedPx = statusBarsTopPx + with(density) { (64.dp + 8.dp).roundToPx() }
        val bottomInsetPx = maxOf(
            imeInsets.getBottom(density),
            navigationBarsInsets.getBottom(density),
        )
        val bottomReservedPx = bottomInsetPx +
            with(density) { 10.dp.roundToPx() } +
            (if (queuePanelHeightPx > 0) queuePanelHeightPx + with(density) { 8.dp.roundToPx() } else 0)
        val fallbackCollapsedPx = with(density) { 104.dp.roundToPx() }
        val baseCollapsedPx = if (collapsedCardHeightPx > 0) collapsedCardHeightPx else fallbackCollapsedPx
        val maxExpandedCardHeightPx = (windowHeightPx - topReservedPx - bottomReservedPx)
            .coerceAtLeast(baseCollapsedPx)
        currentCardHeightPx = (baseCollapsedPx + (maxExpandedCardHeightPx - baseCollapsedPx) * expandFraction)
            .roundToInt()
        extraHeightDp = with(density) { (currentCardHeightPx - baseCollapsedPx).coerceAtLeast(0).toDp() }
    } else {
        currentCardHeightPx = 0
        extraHeightDp = 0.dp
    }

    SideEffect {
        onFloatingChange(isFloating)
        onExpandedHeightDeltaChange(extraHeightDp)
    }

    // 底部渐变遮罩：从输入区顶部一直过渡到屏幕底部（键盘弹出时到键盘上沿），避免消息透到系统导航栏
    val scrimColor = MaterialTheme.colorScheme.background
    Surface(
        color = Color.Transparent,
        modifier = Modifier.drawWithCache {
            val brush = Brush.verticalGradient(
                colors = listOf(scrimColor.copy(alpha = 0f), scrimColor),
                startY = 0f,
                endY = (size.height - imeInsets.getBottom(this)).coerceAtLeast(1f),
            )
            onDrawBehind { drawRect(brush) }
        },
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.onSizeChanged { queuePanelHeightPx = it.height }
            ) {
                MessageQueuePanel(
                    state = messageQueue,
                    onRemove = onRemoveQueuedMessage,
                    onBeginEdit = onBeginEditQueuedMessage,
                    onFinishEdit = onFinishEditQueuedMessage,
                    onResume = onResumeMessageQueue,
                )
            }
            val borderColor = if (isDark) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.90f)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFloating) {
                            Modifier.height(with(density) { currentCardHeightPx.toDp() })
                        } else {
                            Modifier
                        }
                    )
                    .onSizeChanged { size ->
                        if (!isFloating && size.height > 0) {
                            collapsedCardHeightPx = size.height
                            if (textRowHeightPx > 0) {
                                chromeHeightPx = (size.height - textRowHeightPx).coerceAtLeast(0)
                            }
                        }
                    }
                    .then(
                        if (settings.displaySetting.enableBlurEffect) {
                            Modifier
                                .clip(containerShape)
                                .chatInputHaze(
                                    hazeState = hazeState,
                                    displaySetting = settings.displaySetting,
                                    containerColor = containerColor,
                                    shape = containerShape,
                                )
                        } else {
                            Modifier.shadow(
                                elevation = if (isDark) 2.dp else 5.dp,
                                shape = containerShape,
                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.32f else 0.18f),
                                spotColor = Color.Black.copy(alpha = if (isDark) 0.32f else 0.26f),
                            )
                        }
                    ),
                shape = containerShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, borderColor),
                color = if (settings.displaySetting.enableBlurEffect) Color.Transparent else containerColor,
            ) {
                Column(
                    modifier = Modifier
                        .then(if (isFloating) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (voiceState.phase != VoicePhase.Off) {
                        VoiceModeRow(
                            state = voiceState,
                            onStop = onStopVoiceMode,
                            onRetry = { onStartVoiceMode?.invoke() },
                        )
                        androidx.compose.material3.HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(state = state)
                    }

                    TextInputRow(
                        state = state,
                        autoFocusRequest = autoFocusRequest,
                        onAutoFocusHandled = onAutoFocusHandled,
                        onRequestComposerFocus = onRequestComposerFocus,
                        assistant = assistant,
                        completionProviders = completionProviders,
                        isExpanded = isExpanded,
                        isFloating = isFloating,
                        onToggleExpand = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isExpanded = !isExpanded
                        },
                        onCollapsedTextRowMeasured = { rowH, fromFloating ->
                            textRowHeightPx = rowH
                            if (fromFloating && chromeHeightPx > 0) {
                                collapsedCardHeightPx = chromeHeightPx + rowH
                            } else if (!fromFloating && collapsedCardHeightPx > 0) {
                                chromeHeightPx = (collapsedCardHeightPx - rowH).coerceAtLeast(0)
                            }
                        },
                        onSendMessage = { sendMessage() },
                        modifier = if (isFloating) Modifier.weight(1f) else Modifier,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 左侧：模型图标 + 思考深度图标
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ModelSelectorButton(
                                state = modelListState,
                                onlyIcon = true,
                            )

                            if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                                val chatProvider = remember(chatModel, settings.providers) {
                                    chatModel.findProvider(settings.providers)
                                }
                                ReasoningButton(
                                    reasoningLevel = assistant.reasoningLevel,
                                    onUpdateReasoningLevel = {
                                        onUpdateAssistant(assistant.copy(reasoningLevel = it))
                                    },
                                    onlyIcon = true,
                                    model = chatModel,
                                    providerSetting = chatProvider,
                                )
                            }
                        }

                        // 右侧：附件/更多、快速跳转、后台任务、语音、发送
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ActionIconButton(
                                onClick = onMoreClick
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.more_options),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // 快速跳转回答位置
                            ActionIconButton(onClick = onJumperClick) {
                                Icon(
                                    imageVector = HugeIcons.ArrowUpDown,
                                    contentDescription = "快速跳转",
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (runningBackgroundTasks > 0) {
                                BackgroundTasksButton(
                                    running = runningBackgroundTasks,
                                    onClick = onShowBackgroundTasks,
                                )
                            }

                            if (!voiceState.isActive && (asrState.isAvailable || asrState.isRecording)) {
                                AsrButton(
                                    state = asrState,
                                    onClick = {
                                        when (asrState.status) {
                                            ASRStatus.Listening -> asr.stop()
                                            // 连接过程中点击 = 取消连接
                                            ASRStatus.Connecting -> asr.stop()
                                            // Error 状态：先 stop() 重置到 Idle，避免再次 start() 导致重复报错
                                            ASRStatus.Error -> asr.stop()
                                            ASRStatus.Idle -> {
                                                if (!asrPermission.allRequiredPermissionsGranted) {
                                                    asrPermission.requestPermissions()
                                                } else {
                                                    asrBaseText = state.textContent.text.toString()
                                                    asr.start { transcript ->
                                                        // Chirp3 provider 内部已通过 completedTexts + partialText 保证
                                                        // transcript 是本次会话从开始到现在的全量文字（单调递增），
                                                        // 直接用启动时快照 + transcript 更新输入框即可
                                                        val spacer = if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                                        state.setMessageText(asrBaseText + spacer + transcript)
                                                    }
                                                }
                                            }

                                            ASRStatus.Stopping -> {}
                                        }
                                    }
                                )
                            }

                            if (loading) {
                                KeepScreenOn()
                            }

                            AnimatedVisibility(
                                visible = !asrState.isRecording,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                SendButton(
                                    loading = loading,
                                    empty = state.isEmpty(),
                                    editSaving = state.isEditing() && !editWillRegenerate,
                                    onClick = { sendMessage() },
                                    onLongClick = { sendMessageWithoutAnswer() },
                                )
                            }
                        }
                    }
                }
            }

        }
    }

    ModelListSheet(
        state = modelListState,
        onSelect = onUpdateChatModel,
    )
}

@Composable
private fun SendButton(
    loading: Boolean,
    empty: Boolean,
    editSaving: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showStop = isStopGenerationAction(loading, empty)
    val containerColor = when {
        showStop -> MaterialTheme.colorScheme.onSurface
        empty -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        showStop -> MaterialTheme.colorScheme.surface
        empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .testTag("chat_send_button")
            .clip(CircleShape)
            .combinedClickable(
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = containerColor,
            content = {},
        )
        if (showStop) {
            KeepScreenOn()
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(contentColor, RoundedCornerShape(2.5.dp)),
            )
        } else if (editSaving) {
            // [FORK] 编辑且点击仅保存（不触发重试）时显示保存图标，区分“发送并重试”语义
            Icon(
                imageVector = HugeIcons.FloppyDisk,
                contentDescription = stringResource(R.string.chat_page_save),
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
        } else {
            Icon(
                imageVector = HugeIcons.ArrowUp02,
                contentDescription = stringResource(R.string.send),
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun BackgroundTasksButton(
    running: Int,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = Modifier.padding(horizontal = 5.dp),
        badge = {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(running.toString()) }
        },
    ) {
        ActionIconButton(onClick = onClick) {
            Icon(
                imageVector = HugeIcons.ComputerTerminal01,
                contentDescription = stringResource(R.string.background_tasks_running, running),
            )
        }
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    autoFocusRequest: Int = 0,
    onAutoFocusHandled: () -> Unit = {},
    onRequestComposerFocus: () -> Unit = {},
    assistant: Assistant,
    completionProviders: List<ChatCompletionProvider>,
    isExpanded: Boolean,
    isFloating: Boolean,
    onToggleExpand: () -> Unit,
    onCollapsedTextRowMeasured: (Int, Boolean) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    var isEditingReasoning by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing))
                    Spacer(Modifier.weight(1f))
                    if (state.editingReasoning != null) {
                        TextButton(onClick = { isEditingReasoning = true }) {
                            Text(stringResource(R.string.chat_input_edit_reasoning))
                        }
                    }
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.cancel_edit),
                        modifier = Modifier.clickable { state.clearInput() }
                    )
                }
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        // 外部（编辑消息 / 建议 / 分享）要求聚焦时，主动拉起输入法
        val focusRequester = rememberAutoFocusRequester(
            requestKey = autoFocusRequest.takeIf { it > 0 },
            onRequestHandled = onAutoFocusHandled,
        )
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFloating) Modifier.weight(1f) else Modifier)
                .onSizeChanged { size ->
                    if (!isFloating && size.height > 0) {
                        onCollapsedTextRowMeasured(size.height, false)
                    }
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = if (isFloating) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (quickMessages.isNotEmpty()) {
                QuickMessageButton(
                    quickMessages = quickMessages,
                    state = state,
                    onInsertText = onRequestComposerFocus,
                )
            }
            val colors = MaterialTheme.colorScheme
            BasicTextField(
                state = state.textContent,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isFloating) Modifier.fillMaxHeight() else Modifier)
                    .padding(vertical = 4.dp)
                    .testTag("chat_input")
                    .contentReceiver(receiveContentListener)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                    },
                lineLimits = TextFieldLineLimits.MultiLine(
                    maxHeightInLines = if (isFloating) Int.MAX_VALUE else 6
                ),
                onTextLayout = { getResult ->
                    if (isFloating) {
                        val result = getResult()
                        if (result != null && result.lineCount > 0) {
                            val clampedLine = minOf(result.lineCount - 1, 5)
                            val textH = result.getLineBottom(clampedLine).roundToInt()
                            val minRowPx = 32.dp.roundToPx()
                            val padPx = 12.dp.roundToPx()
                            onCollapsedTextRowMeasured((textH + padPx).coerceAtLeast(minRowPx), true)
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurface,
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (settings.displaySetting.sendOnEnter && !isExpanded) ImeAction.Send else ImeAction.Default
                ),
                onKeyboardAction = {
                    if (settings.displaySetting.sendOnEnter && !isExpanded && !state.isEmpty()) {
                        onSendMessage()
                    }
                },
            )
            if (isFocused || isFloating) {
                IconButton(
                    onClick = {
                        onToggleExpand()
                        if (!isFocused) {
                            focusRequester.requestFocus()
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    AnimatedContent(
                        targetState = isExpanded,
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.75f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.75f))
                        },
                        label = "composer_expand_icon",
                    ) { expanded ->
                        Icon(
                            imageVector = if (expanded) HugeIcons.ArrowShrink02 else HugeIcons.ArrowExpand01,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val reasoningState = state.editingReasoning
        if (isEditingReasoning && reasoningState != null) {
            FullScreenEditor(textState = reasoningState, placeholder = stringResource(R.string.chat_input_reasoning_placeholder)) {
                isEditingReasoning = false
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
    onInsertText: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(HugeIcons.Zap, null)
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                // 短按追加到输入框末尾，长按替换输入框内容
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                state.appendText(quickMessage.content)
                                expanded = false
                                onInsertText()
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.setMessageText(quickMessage.content)
                                expanded = false
                                onInsertText()
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    textState: TextFieldState,
    placeholder: String,
    onDone: () -> Unit
) {
    val focusRequester = rememberAutoFocusRequester()
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    TextField(
                        state = textState,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(placeholder)
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}
