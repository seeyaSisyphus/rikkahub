package me.rerere.rikkahub.ui.pages.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import me.rerere.rikkahub.ui.components.ui.AppOutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ContextLimitResult
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Crane
import me.rerere.hugeicons.stroke.DashboardSpeed02
import me.rerere.hugeicons.stroke.Hourglass
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.PercentCircle
import me.rerere.hugeicons.stroke.Pin02
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.common.android.Logging
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ContextLimitPresets
import me.rerere.rikkahub.data.ai.previewContextLimit
import me.rerere.rikkahub.data.ai.resolveWith
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextKeepPolicy
import me.rerere.rikkahub.data.model.ContextLimitMode
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.ConversationParams
import me.rerere.rikkahub.data.model.pinnedGroupCount
import me.rerere.rikkahub.data.model.SystemPromptMode
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.BackgroundShellStatus
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.SessionActivity
import me.rerere.rikkahub.service.collectProtectedMessageIds
import me.rerere.rikkahub.utils.formatNumber
import kotlin.math.roundToInt
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.BackgroundTasksSheet
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.useCropLauncher
import me.rerere.rikkahub.ui.components.imagepicker.ImagePickerSheet
import me.rerere.rikkahub.ui.components.imagepicker.ImageSortOrder
import me.rerere.rikkahub.ui.components.ui.AppSlider
import me.rerere.rikkahub.ui.components.ui.AppSwitch
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.ui.components.ui.IntSliderItem
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import me.rerere.rikkahub.ui.components.ui.AppModalBottomSheet
import me.rerere.rikkahub.ui.components.ui.AppAlertDialog
import me.rerere.rikkahub.ui.components.ui.rememberAutoFocusRequester

private const val TAG = "ChatPage"
private const val CHAT_LOADING_TIMEOUT_MS = 3000L

// 聊天内容渲染门控：会话数据加载完成且初始滚动位置已记录后，才渲染聊天内容；
// forcedOpen 为超时兜底，加载异常时按旧行为放行，避免永久卡在 loading
internal fun shouldShowChatContent(
    conversationLoaded: Boolean,
    chatListReady: Boolean,
    forcedOpen: Boolean = false,
): Boolean = (conversationLoaded && chatListReady) || forcedOpen

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null, showLoading: Boolean = false) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val conversationLoaded by vm.conversationLoaded.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val hasActiveSlots by vm.hasActiveSlots.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    // 新会话发出第一条消息前，跟随抽屉当前选中的文件夹（#2133）
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Hide keyboard when drawer is open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState
    // 编辑消息 / 点建议问题 / 接收分享文本后，主动聚焦输入框并弹出键盘
    var composerFocusRequest by remember { mutableStateOf(0) }

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
                composerFocusRequest++
            }
        }
    }

    val chatListState = rememberLazyListState()
    // 内容就绪门闩（合并了原“初始滚动”与“就绪门闩”两处逻辑，避免两个 flag/Effect 漂移）：
    // 以 conversationLoaded 为权威的“数据已加载”信号，加载完成后先趁列表不可见把初始滚动位置
    // 写入 LazyListState（未组合时滚动请求会挂起，首帧测量直接落到目标位），再置 chatListReady
    // 放行渲染，从而既滚到底/目标节点，又避免列表先在顶部闪现一帧再跳底。
    LaunchedEffect(nodeId, conversationLoaded, conversation.messageNodes.size) {
        if (vm.chatListReady || !conversationLoaded) return@LaunchedEffect
        if (conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    // 跳到该回答的提问节点（前一个节点），让用户先看到问题再看回答。
                    // 必须用 requestScrollToItem：当前列表还被 loading 门控隐藏，
                    // scrollToItem 会挂起等待首次布局，而首次布局又等 chatListReady，形成死锁。
                    chatListState.requestScrollToItem((index - 1).coerceAtLeast(0))
                }
            } else {
                // requestScrollToItem 在列表未布局时也能生效，修复初始进入自动滚动可能失效的问题
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
        }
        vm.chatListReady = true
    }

    // 加载超时兜底：数据加载异常缓慢（如冷启动数据库阻塞）时按旧行为放行，
    // 数据到达后 UI 仍会自动填充，同时打日志便于定位根因
    LaunchedEffect(Unit) {
        delay(CHAT_LOADING_TIMEOUT_MS)
        if (!vm.chatListReady) {
            Logging.logError(
                tag = TAG,
                title = "会话加载超时",
                message = "超过 " + CHAT_LOADING_TIMEOUT_MS + "ms 未就绪；conversationLoaded=" +
                    vm.conversationLoaded.value + ", 当前阶段: " + vm.getConversationInitStage()
            )
            vm.chatListForcedOpen = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    composerFocusRequest = composerFocusRequest,
                    onRequestComposerFocus = { composerFocusRequest++ },
                    onComposerFocusHandled = { composerFocusRequest = 0 },
                    conversationLoaded = conversationLoaded,
                    showLoading = showLoading,
                    loadingJob = loadingJob,
                    hasActiveSlots = hasActiveSlots,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                    onStartVoiceMode = startVoiceMode,
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState,
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    composerFocusRequest = composerFocusRequest,
                    onRequestComposerFocus = { composerFocusRequest++ },
                    onComposerFocusHandled = { composerFocusRequest = 0 },
                    conversationLoaded = conversationLoaded,
                    showLoading = showLoading,
                    loadingJob = loadingJob,
                    hasActiveSlots = hasActiveSlots,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                    onStartVoiceMode = startVoiceMode,
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    composerFocusRequest: Int = 0,
    onRequestComposerFocus: () -> Unit = {},
    onComposerFocusHandled: () -> Unit = {},
    conversationLoaded: Boolean,
    showLoading: Boolean,
    loadingJob: Job?,
    hasActiveSlots: Boolean = false,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onStartVoiceMode: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val selectionState = rememberChatSelectionState()
    val previewListState = rememberLazyListState()
    val sessionActivity by vm.sessionActivity.collectAsStateWithLifecycle()
    var showMessageJumperOverlay by rememberSaveable { mutableStateOf(false) }
    // 缩略页跳转请求：nonce 保证同一 index 重复点击时 LaunchedEffect 也能重新触发
    var jumpNonce by remember { mutableIntStateOf(0) }
    var jumpRequest by remember { mutableStateOf<JumpRequest?>(null) }
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    // 概览页可通过顶栏关闭按钮或返回键退出
    BackHandler(previewMode) {
        previewMode = false
    }

    @Suppress("DEPRECATION")
    val noopBringIntoViewResponder = remember {
        object : androidx.compose.foundation.relocation.BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = Rect.Zero
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .bringIntoViewResponder(noopBringIntoViewResponder)
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        val waitingForChat = showLoading && !shouldShowChatContent(conversationLoaded, vm.chatListReady, vm.chatListForcedOpen)
        AnimatedContent(
            targetState = waitingForChat,
            transitionSpec = {
                fadeIn(tween(260)) togetherWith fadeOut(tween(200))
            },
            label = "chat_content_ready",
        ) { isLoadingGate ->
            if (isLoadingGate) {
                // 会话数据尚未从数据库加载完成，先展示 loading，避免闪现空会话骨架
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    RabbitLoadingIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                var expandedInputHeightDelta by remember { mutableStateOf(0.dp) }
                var isComposerFloating by remember { mutableStateOf(false) }
                Scaffold(
                topBar = {
                    TopBar(
                        settings = setting,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        conversation = conversation,
                        bigScreen = bigScreen,
                        drawerState = drawerState,
                        previewMode = previewMode,
                        vm = vm,
                        onClickMenu = {
                            previewMode = !previewMode
                        },
                        onUpdateTitle = {
                            vm.updateTitle(it)
                        },
                        onUpdateConversationParams = {
                            vm.updateConversationParams(it)
                        },
                        onSaveTemporary = { vm.saveTemporaryConversation() },
                        onDeleteTemporary = { navigateToChatPage(navController) },
                    )
                },
                bottomBar = {
                    // 编辑态下点击发送是否会触发重试：决定发送按钮显示“发送”(↑) 还是“保存”(FloppyDisk) 图标
                    val editWillRegenerate = remember(conversation, inputState.editingMessage) {
                        inputState.editingMessage?.let { conversation.editWillRegenerate(it) } == true
                    }
                    val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                    val backgroundTasks by vm.backgroundTasks.collectAsStateWithLifecycle()
                    var showBackgroundTasks by remember { mutableStateOf(false) }
                    if (showBackgroundTasks) {
                        BackgroundTasksSheet(
                            tasks = backgroundTasks,
                            onStop = vm::stopBackgroundTask,
                            onDismiss = { showBackgroundTasks = false },
                        )
                    }
                    ChatInput(
                        state = inputState,
                        autoFocusRequest = composerFocusRequest,
                        onAutoFocusHandled = onComposerFocusHandled,
                        onRequestComposerFocus = onRequestComposerFocus,
                        editWillRegenerate = editWillRegenerate,
                        messageQueue = messageQueue,
                        onRemoveQueuedMessage = vm::removeQueuedMessage,
                        onMoveQueuedMessage = vm::moveQueuedMessage,
                        onEditQueuedMessage = vm::editQueuedMessage,
                        onResumeMessageQueue = vm::resumeMessageQueue,
                        onStartVoiceMode = onStartVoiceMode,
                        voiceState = voiceState,
                        onStopVoiceMode = vm.voiceSession::stop,
                        loading = loadingJob != null || hasActiveSlots,
                        settings = setting,
                        assistant = assistant,
                        chatModel = currentChatModel,
                        hazeState = hazeState,
                        completionProviders = completionProviders,
                        onCancelClick = {
                            vm.stopGeneration()
                        },
                        onSendClick = {
                            if (currentChatModel == null) {
                                toaster.show("请先选择模型", type = ToastType.Error)
                                return@ChatInput
                            }
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
                                    regenerate = !inputState.isEditingAssistant(),
                                )
                            } else {
                                vm.handleMessageSend(inputState.getContents())
                            }
                            inputState.clearInput()
                        },
                        onLongSendClick = {
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
                                    regenerate = inputState.isEditingAssistant(), // 编辑AI回答时长按触发重新生成
                                )
                            } else {
                                vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            }
                            inputState.clearInput()
                        },
                        onUpdateChatModel = vm::setChatModel,
                        onUpdateAssistant = vm::updateAssistant,
                        onMoreClick = {
                            showFilesSheet = true
                        },
                        onJumperClick = {
                            showMessageJumperOverlay = !showMessageJumperOverlay
                        },
                        runningBackgroundTasks = backgroundTasks.count { it.status == BackgroundShellStatus.RUNNING },
                        onShowBackgroundTasks = { showBackgroundTasks = true },
                        onExpandedHeightDeltaChange = {
                            if (expandedInputHeightDelta != it) expandedInputHeightDelta = it
                        },
                        onFloatingChange = {
                            if (isComposerFloating != it) isComposerFloating = it
                        },
                    )
                },
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0),
            ) { innerPadding ->
                // Scaffold 传入的是可变的 MutablePaddingValues 单例，不能把 innerPadding 当作 remember key 快照其数值，
                // 否则键盘弹出/收起更新 paddingHolder 时无法刷新，导致列表不跟滚及底部残留大片空白。
                val stabilizedInnerPadding = remember(innerPadding) {
                    object : PaddingValues {
                        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                            innerPadding.calculateLeftPadding(layoutDirection)

                        override fun calculateTopPadding(): Dp =
                            innerPadding.calculateTopPadding()

                        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                            innerPadding.calculateRightPadding(layoutDirection)

                        override fun calculateBottomPadding(): Dp =
                            (innerPadding.calculateBottomPadding() - expandedInputHeightDelta).coerceAtLeast(0.dp)
                    }
                }
                ChatList(
                    innerPadding = stabilizedInnerPadding,
                    suppressBottomPaddingAutoScroll = { isComposerFloating || expandedInputHeightDelta > 0.dp },
                    conversation = conversation,
                    state = chatListState,
                    previewListState = previewListState,
                    selectionState = selectionState,
                    loading = loadingJob != null || hasActiveSlots,
                    compressing = sessionActivity == SessionActivity.Compressing,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    settings = setting,
                    hazeState = hazeState,
                    showJumper = showMessageJumperOverlay,
                    onDismissJumper = { showMessageJumperOverlay = false },
                    jumpRequest = jumpRequest,
                    errors = errors,
                    onDismissError = onDismissError,
                    onClearAllErrors = onClearAllErrors,
                    onRegenerate = {
                        vm.regenerateAtMessage(it)
                    },
                    onEdit = {
                        inputState.editingMessage = it.id
                        inputState.editingMessageRole = it.role
                        inputState.setContents(it.parts)
                        onRequestComposerFocus()
                    },
                    onForkMessage = {
                        scope.launch {
                            val fork = vm.forkMessage(message = it)
                            navigateToChatPage(navController, chatId = fork.id)
                        }
                    },
                    onDelete = {
                        if (loadingJob != null || hasActiveSlots) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(it)
                        }
                    },
                    onDeleteBeforeMessage = {
                        if (loadingJob != null || hasActiveSlots) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessagesBeforeMessage(it)
                        }
                    },
                    onDeleteAfterMessage = {
                        if (loadingJob != null || hasActiveSlots) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessagesAfterMessage(it)
                        }
                    },
                    onUpdateMessage = { newNode ->
                        vm.updateConversation(
                            conversation.copy(
                                messageNodes = conversation.messageNodes.map { node ->
                                    if (node.id == newNode.id) {
                                        newNode
                                    } else {
                                        node
                                    }
                                }
                            ))
                        vm.saveConversationAsync()
                    },
                    onClickSuggestion = { suggestion ->
                        inputState.editingMessage = null
                        inputState.editingMessageRole = null
                        inputState.setMessageText(suggestion)
                        onRequestComposerFocus()
                    },
                    onTranslate = { message, locale ->
                        vm.translateMessage(message, locale)
                    },
                    onClearTranslation = { message ->
                        vm.clearTranslationField(message.id)
                    },
                    onJumpToMessage = { index ->
                        // 先切到普通模式，再通过 jumpRequest 通知 ChatListNormal
                        // ChatListNormal 内部会原子地“禁用自动贴底 + 滚动到目标”，防止贴底覆盖跳转位置
                        previewMode = false
                        jumpNonce++
                        jumpRequest = JumpRequest(index, jumpNonce)
                    },
                    onToolApproval = { toolCallId, approved, reason ->
                        vm.handleToolApproval(toolCallId, approved, reason)
                    },
                    onToolAnswer = { toolCallId, answer ->
                        vm.handleToolAnswer(toolCallId, answer)
                    },
                    onToggleFavorite = { node ->
                        vm.toggleMessageFavorite(node)
                    },
                    onConversationSystemPromptChange = { newPrompt ->
                        val newParams = conversation.conversationParams.copy(
                            systemPrompt = newPrompt?.ifBlank { null },
                        )
                        vm.updateConversation(conversation.copy(conversationParams = newParams))
                        vm.saveConversationAsync()
                    },
                    // [FORK] 固定到上下文
                    onTogglePin = { node -> vm.toggleMessagePin(node) },
                )
            }
        }
        }

        // 记住本对话内上次选择的相册文件夹与排序方式（切换对话或重启后回到默认）。
        // 状态放在 ChatFilesPickerSheet 之外，避免文件选择弹窗关闭时被销毁。
        var imagePickerBucketId by remember(conversation.id) { mutableStateOf<String?>(null) }
        var imagePickerSortOrder by remember(conversation.id) { mutableStateOf(ImageSortOrder.Default) }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                imagePickerBucketId = imagePickerBucketId,
                onImagePickerBucketSelected = { imagePickerBucketId = it },
                imagePickerSortOrder = imagePickerSortOrder,
                onImagePickerSortOrderChange = { imagePickerSortOrder = it },
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    imagePickerBucketId: String?,
    onImagePickerBucketSelected: (String?) -> Unit,
    imagePickerSortOrder: ImageSortOrder,
    onImagePickerSortOrderChange: (ImageSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val launchCameraCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            dismissAll()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                inputState.addImages(filesManager.createChatFilesByContents(listOf(cameraOutputUri!!)))
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                dismissAll()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onLaunchCamera: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            dismissAll()
        },
    )
    // 处理从相册选中的图片：单张走裁剪（可在设置中跳过），多张直接加入输入框
    fun processPickedImages(selectedUris: List<Uri>) {
        if (selectedUris.isEmpty()) {
            Log.d("ImagePickButton", "No images selected")
            return
        }
        Log.d("ImagePickButton", "Selected URIs: $selectedUris")
        if (setting.displaySetting.skipCropImage) {
            inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
            dismissAll()
        } else if (selectedUris.size == 1) {
            // 裁剪器用 ImageDecoder 解码，自带 EXIF 方向并支持 HEIF，直接用 content uri
            launchImageCrop(selectedUris.first())
        } else {
            inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
            dismissAll()
        }
    }

    // 系统照片选择器（无相册权限时的兜底方案）
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            processPickedImages(selectedUris)
        }

    // 自研相册选择器（支持自定义相册文件夹，需要相册读取权限）
    var showImagePicker by remember { mutableStateOf(false) }
    var mediaDataVersion by remember { mutableIntStateOf(0) }
    val mediaImagePermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasFullMediaAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, mediaImagePermission) == PackageManager.PERMISSION_GRANTED

    // Android 14+：用户仅授权了部分照片
    fun hasPartialMediaAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !hasFullMediaAccess() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED

    // 权限请求结果：授权（含部分授权）打开自研选择器，否则回退到系统选择器
    val mediaPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            mediaDataVersion++
            if (hasFullMediaAccess() || hasPartialMediaAccess()) {
                showImagePicker = true
            } else {
                imagePickerLauncher.launch("image/*")
            }
        }

    // 选择器内“管理”部分授权时使用：仅刷新数据，不做回退
    val manageMediaPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            mediaDataVersion++
        }

    val isPartialMediaAccess = remember(mediaDataVersion) { hasPartialMediaAccess() }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addVideos(filesManager.createChatFilesByContents(selectedUris))
                dismissAll()
            }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addAudios(filesManager.createChatFilesByContents(selectedUris))
                dismissAll()
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val fileName = filesManager.getFileNameFromUri(uri) ?: "file"
                    val mime = filesManager.getFileMimeType(uri) ?: "text/plain"
                    if (isAllowedFileType(fileName, mime)) {
                        val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                            ?: run {
                                toaster.show(
                                    context.getString(R.string.chat_input_file_read_failed, fileName),
                                    type = ToastType.Error
                                )
                                return@mapNotNull null
                            }
                        UIMessagePart.Document(url = localUri.toString(), fileName = fileName, mime = mime)
                    } else {
                        toaster.show(
                            context.getString(R.string.chat_input_unsupported_file_type, fileName),
                            type = ToastType.Error
                        )
                        null
                    }
                }
                if (documents.isNotEmpty()) {
                    inputState.addFiles(documents)
                    dismissAll()
                }
            }
        }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    AppModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onUpdateSearchService = { index ->
                vm.updateSettings { it.copy(searchServiceSelected = index) }
            },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = onLaunchCamera,
            onPickImage = {
                if (hasFullMediaAccess() || hasPartialMediaAccess()) {
                    showImagePicker = true
                } else {
                    mediaPermissionLauncher.launch(mediaImagePermission)
                }
            },
            onPickVideo = { videoPickerLauncher.launch("video/*") },
            onPickAudio = { audioPickerLauncher.launch("audio/*") },
            onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
            onSketch = attachmentPickerActions.onSketch,
            // 切换语音识别开关：asrEnabled 取反即可，controller 会自动响应
            onToggleAsr = { vm.updateSettings { it.copy(asrEnabled = !it.asrEnabled) } },
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }

    if (showImagePicker) {
        ImagePickerSheet(
            isPartialAccess = isPartialMediaAccess,
            dataVersion = mediaDataVersion,
            selectedBucketId = imagePickerBucketId,
            onBucketSelected = onImagePickerBucketSelected,
            sortOrder = imagePickerSortOrder,
            onSortOrderChange = onImagePickerSortOrderChange,
            onManagePartialAccess = { manageMediaPermissionLauncher.launch(mediaImagePermission) },
            onDismiss = { showImagePicker = false },
            onConfirm = { uris ->
                showImagePicker = false
                processPickedImages(uris)
            },
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    vm: ChatVM,
    onClickMenu: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateConversationParams: (ConversationParams) -> Unit,
    onSaveTemporary: () -> Unit,
    onDeleteTemporary: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
            navigationIcon = {
                if (!bigScreen) {
                    IconButton(
                        onClick = {
                            scope.launch { drawerState.open() }
                        }
                    ) {
                        Icon(HugeIcons.Menu03, "Messages")
                    }
                }
            },
            title = {
                val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
                Surface(
                    onClick = {
                        if (conversation.messageNodes.isNotEmpty()) {
                            titleState.open(conversation.title)
                        } else {
                            toaster.show(editTitleWarning, type = ToastType.Warning)
                        }
                    },
                    color = Color.Transparent,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        val provider = chatModel?.findProvider(providers = settings.providers, checkOverwrite = false)
                        Text(
                            text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (chatModel != null && provider != null) {
                            Text(
                                text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${chatModel.displayName} (${provider.name})",
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            },
        actions = {
            var showParamsSheet by remember { mutableStateOf(false) }
            var showTemporaryDialog by remember { mutableStateOf(false) }

            if (conversation.isTemporary) {
                IconButton(onClick = { showTemporaryDialog = true }) {
                    Icon(HugeIcons.Hourglass, contentDescription = "临时对话")
                }
            }

            IconButton(
                onClick = { showParamsSheet = true }
            ) {
                Icon(HugeIcons.Crane, "Conversation Params")
            }

            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            if (showParamsSheet) {
                ConversationParamsSheet(
                    conversation = conversation,
                    assistant = assistant,
                    settings = settings,
                    vm = vm,
                    onDismiss = { showParamsSheet = false },
                    onUpdate = onUpdateConversationParams,
                )
            }

            if (showTemporaryDialog) {
                AppAlertDialog(
                    onDismissRequest = { showTemporaryDialog = false },
                    title = { Text("临时对话") },
                    text = { Text("这段对话目前不会保存到历史记录中。") },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showTemporaryDialog = false }) {
                                Text("取消")
                            }
                            TextButton(
                                onClick = {
                                    showTemporaryDialog = false
                                    onDeleteTemporary()
                                }
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(
                                onClick = {
                                    showTemporaryDialog = false
                                    onSaveTemporary()
                                }
                            ) {
                                Text("保存")
                            }
                        }
                    }
                )
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        val focusRequester = rememberAutoFocusRequester()
        AppAlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                AppOutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationParamsSheet(
    conversation: Conversation,
    assistant: Assistant,
    settings: Settings,
    vm: ChatVM,
    onDismiss: () -> Unit,
    onUpdate: (ConversationParams) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    // 当前编辑的参数，随外部会话变化（如后台推进裁剪锚点）保持同步
    var params by remember(conversation.conversationParams) {
        mutableStateOf(conversation.conversationParams)
    }
    val hasCustomOverrides = params.contextLimitMode != null ||
        params.contextMessageSize != null ||
        params.contextTokenLimit != null ||
        params.contextAnchorNodeId != null ||
        params.battleModeEnabled ||
        params.systemPrompt != null ||
        params.temperature != null ||
        params.topP != null

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        val maxSheetHeight = (currentWindowDpSize().height * 0.82f).coerceAtLeast(360.dp)
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val bottomComfortPx = with(density) { 24.dp.toPx() }
        var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

        val ensureVisibleInViewport: (LayoutCoordinates) -> Unit = { itemCoords ->
            val vp = viewportCoords
            if (vp != null && vp.isAttached && itemCoords.isAttached) {
                val rect = vp.localBoundingBoxOf(itemCoords, clipBounds = false)
                val vpHeight = vp.size.height.toFloat()
                val overflow = (rect.bottom + bottomComfortPx) - vpHeight
                val maxShift = rect.top.coerceAtLeast(0f)
                val delta = minOf(overflow, maxShift)
                if (delta > 0.5f && scrollState.value < scrollState.maxValue) {
                    scope.launch {
                        scrollState.scrollBy(delta)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .imePadding(),
        ) {
            // 顶部固定标题栏：带图标与一键恢复默认（对齐下方 CardGroup 的 16.dp 内边距与 16.dp 间距轴线）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CardGroupIcon(
                        icon = HugeIcons.Crane,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "对话专属参数",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "仅对当前会话生效，未开启项跟随助手默认设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = hasCustomOverrides,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                ) {
                    FilledTonalButton(
                        onClick = {
                            val reset = ConversationParams()
                            params = reset
                            onUpdate(reset)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Refresh01,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "重置",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .onGloballyPositioned { viewportCoords = it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {

            // Context Limit (缓存优先双水位滑块)
            val resolvedParams = remember(params, assistant) { params.resolveWith(assistant) }
            val pinnedIds = remember(conversation.messageNodes) { conversation.collectProtectedMessageIds() }
            val pinnedCount = remember(conversation.messageNodes, pinnedIds) {
                conversation.contextMessages.count { it.id in pinnedIds }
            }
            val pinnedGroups = remember(conversation.messageNodes) { conversation.pinnedGroupCount() }
            val contextCustomEnabled = params.contextLimitMode != null ||
                params.contextMessageSize != null ||
                params.contextTokenLimit != null

            var editingMode by remember(
                params.contextLimitMode,
                params.contextMessageSize,
                params.contextTokenLimit,
            ) {
                mutableStateOf(resolvedParams.contextLimitMode)
            }
            var editingTokenLimit by remember(params.contextTokenLimit) {
                mutableIntStateOf(
                    params.contextTokenLimit ?: ContextLimitPresets.DEFAULT_TOKEN_LIMIT
                )
            }
            var editingMessageLimit by remember(params.contextMessageSize, assistant.contextMessageLimit) {
                mutableIntStateOf(
                    params.contextMessageSize
                        ?: assistant.contextMessageLimit.takeIf { it >= 20 }
                        ?: ContextLimitPresets.DEFAULT_MESSAGE_LIMIT
                )
            }
            var editingPolicy by remember(params.contextKeepPolicy) {
                mutableStateOf(params.contextKeepPolicy)
            }
            var showAdvancedContextSettings by rememberSaveable { mutableStateOf(false) }

            val currentStops = if (editingMode == ContextLimitMode.TOKENS) {
                ContextLimitPresets.TOKEN_STOPS
            } else {
                ContextLimitPresets.MESSAGE_STOPS
            }
            val committedValue = if (editingMode == ContextLimitMode.TOKENS) {
                editingTokenLimit
            } else {
                editingMessageLimit
            }
            val committedStopIndex = remember(editingMode, committedValue) {
                if (editingMode == ContextLimitMode.TOKENS) {
                    ContextLimitPresets.closestTokenStopIndex(committedValue)
                } else {
                    ContextLimitPresets.closestMessageStopIndex(committedValue)
                }
            }
            var displayStopIndex by remember(editingMode, committedStopIndex) {
                mutableIntStateOf(committedStopIndex)
            }
            val displayLimitValue = currentStops.getOrElse(displayStopIndex) { 0 }

            val previewParams = remember(params, contextCustomEnabled, editingMode, displayLimitValue, editingPolicy) {
                if (contextCustomEnabled) {
                    params.copy(
                        contextLimitMode = editingMode,
                        contextTokenLimit = if (editingMode == ContextLimitMode.TOKENS) displayLimitValue else null,
                        contextMessageSize = if (editingMode == ContextLimitMode.MESSAGES) displayLimitValue else null,
                        contextKeepPolicy = editingPolicy,
                    )
                } else {
                    params
                }
            }
            val previewResult by produceState<ContextLimitResult?>(
                initialValue = null,
                conversation.messageNodes,
                conversation.conversationParams.contextAnchorNodeId,
                previewParams,
                assistant,
                settings,
            ) {
                value = withContext(Dispatchers.IO) {
                    conversation.previewContextLimit(assistant, previewParams, settings)
                }
            }
            val lastUsageReadout = remember(conversation.messageNodes) {
                conversation.currentMessages
                    .lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?.lastRequestUsage()
                    ?.takeIf { it.promptTokens > 0 }
            }

            CardGroup {
                formItem(
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.Message02,
                            containerColor = if (contextCustomEnabled) colors.primaryContainer else colors.secondaryContainer,
                            contentColor = if (contextCustomEnabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        )
                    },
                    headlineContent = {
                        Text("上下文长度")
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (contextCustomEnabled) {
                                CardGroupValue(
                                    text = ContextLimitPresets.formatLimitValue(editingMode, displayLimitValue)
                                )
                            }
                            AppSwitch(
                                checked = contextCustomEnabled,
                                onCheckedChange = { enabled ->
                                    val newParams = if (enabled) {
                                        if (assistant.contextMessageLimit > 0) {
                                            params.copy(
                                                contextLimitMode = ContextLimitMode.MESSAGES,
                                                contextMessageSize = assistant.contextMessageLimit.coerceAtLeast(20),
                                                contextTokenLimit = null,
                                            )
                                        } else {
                                            params.copy(
                                                contextLimitMode = ContextLimitMode.TOKENS,
                                                contextTokenLimit = ContextLimitPresets.DEFAULT_TOKEN_LIMIT,
                                                contextMessageSize = null,
                                            )
                                        }
                                    } else {
                                        params.copy(
                                            contextLimitMode = null,
                                            contextMessageSize = null,
                                            contextTokenLimit = null,
                                            contextAnchorNodeId = if (assistant.contextMessageLimit <= 0) {
                                                null
                                            } else {
                                                params.contextAnchorNodeId
                                            },
                                        )
                                    }
                                    params = newParams
                                    onUpdate(newParams)
                                }
                            )
                        }
                    },
                    contentSpacing = 0.dp,
                ) {
                    ParamAnimatedVisibility(
                        visible = contextCustomEnabled,
                        onExpandingBoundsChanged = ensureVisibleInViewport,
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val limitDescription = when {
                                displayLimitValue <= 0 ->
                                    "发送全部历史消息，不设置长度上限。不会删除聊天记录。"
                                editingMode == ContextLimitMode.TOKENS -> {
                                    val limit = ContextLimitPresets.formatCompactTokens(displayLimitValue)
                                    "每次最多发送约 $limit tokens 的历史。" +
                                        "超出后自动跳过最早的消息，不会删除聊天记录。"
                                }
                                else ->
                                    "每次最多发送最近 $displayLimitValue 条消息。" +
                                        "超出后自动跳过最早的消息，不会删除聊天记录。"
                            }
                            Text(
                                text = limitDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )

                            AnimatedVisibility(visible = showAdvancedContextSettings) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "长度计算方式",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                    val limitModes = remember {
                                        listOf(ContextLimitMode.TOKENS, ContextLimitMode.MESSAGES)
                                    }
                                    ConnectedChoiceButtons(
                                        options = limitModes,
                                        selected = editingMode,
                                        onSelect = { nextMode ->
                                            editingMode = nextMode
                                            val newParams = when (nextMode) {
                                                ContextLimitMode.TOKENS -> params.copy(
                                                    contextLimitMode = ContextLimitMode.TOKENS,
                                                    contextTokenLimit = editingTokenLimit.takeIf { it > 0 }
                                                        ?: ContextLimitPresets.DEFAULT_TOKEN_LIMIT,
                                                    contextMessageSize = null,
                                                )

                                                ContextLimitMode.MESSAGES -> params.copy(
                                                    contextLimitMode = ContextLimitMode.MESSAGES,
                                                    contextMessageSize = editingMessageLimit.takeIf { it >= 20 }
                                                        ?: ContextLimitPresets.DEFAULT_MESSAGE_LIMIT,
                                                    contextTokenLimit = null,
                                                )
                                            }
                                            params = newParams
                                            onUpdate(newParams)
                                        },
                                        minHeight = 36.dp,
                                    ) { mode, selected ->
                                        ConnectedChoiceLabel(
                                            text = when (mode) {
                                                ContextLimitMode.TOKENS -> "按 Token"
                                                ContextLimitMode.MESSAGES -> "按条数"
                                            },
                                            selected = selected,
                                            icon = HugeIcons.Tick01,
                                        )
                                    }
                                }
                            }

                            IntSliderItem(
                                value = committedStopIndex,
                                onValueChange = { nextIdx ->
                                    displayStopIndex = nextIdx
                                    val nextValue = currentStops[nextIdx]
                                    if (editingMode == ContextLimitMode.TOKENS) {
                                        editingTokenLimit = nextValue
                                    } else {
                                        editingMessageLimit = nextValue
                                    }
                                    val newParams = params.copy(
                                        contextLimitMode = editingMode,
                                        contextTokenLimit = if (editingMode == ContextLimitMode.TOKENS) {
                                            nextValue
                                        } else {
                                            null
                                        },
                                        contextMessageSize = if (editingMode == ContextLimitMode.MESSAGES) {
                                            nextValue
                                        } else {
                                            null
                                        },
                                    )
                                    params = newParams
                                    onUpdate(newParams)
                                },
                                onValueChanging = { displayStopIndex = it },
                                valueRange = 0..currentStops.lastIndex,
                            )

                            previewResult?.let { preview ->
                                val currentTokens = ContextLimitPresets.formatCompactTokens(
                                    preview.estimatedTotalTokens
                                )
                                val statusText = when {
                                    preview.didAdvanceAnchor ->
                                        "下次发送将省略部分较早消息，之后沿用新的范围。"
                                    preview.hasTruncatedHistory ->
                                        "当前发送第 ${preview.activeNormalStartIndex + 1} 条起的消息，" +
                                            "早期内容仍保留在本机。"
                                    displayLimitValue <= 0 ->
                                        "当前约 $currentTokens tokens，未设置上限。"
                                    else ->
                                        "当前约 $currentTokens tokens，未超过上限。"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (preview.didAdvanceAnchor) colors.primary else colors.onSurfaceVariant,
                                )
                            }
                            lastUsageReadout?.let { usage ->
                                val pct = ((usage.cachedTokens.toDouble() / usage.promptTokens) * 100)
                                    .roundToInt()
                                    .coerceIn(0, 100)
                                Text(
                                    text = "上轮缓存命中 $pct%（${usage.cachedTokens.formatNumber()} tokens）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { showAdvancedContextSettings = !showAdvancedContextSettings },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = if (showAdvancedContextSettings) "收起高级设置" else "高级设置",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            AnimatedVisibility(visible = showAdvancedContextSettings) {
                                if (displayLimitValue > 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "超出上限后，保留多少近期历史",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.onSurfaceVariant,
                                        )
                                        val policies = remember {
                                            listOf(
                                                ContextKeepPolicy.ULTRA_CACHE,
                                                ContextKeepPolicy.HIGH_CACHE,
                                                ContextKeepPolicy.BALANCED,
                                                ContextKeepPolicy.HIGH_MEMORY,
                                            )
                                        }
                                        ConnectedChoiceButtons(
                                            options = policies,
                                            selected = editingPolicy,
                                            onSelect = { nextPolicy ->
                                                editingPolicy = nextPolicy
                                                val newParams = params.copy(contextKeepPolicy = nextPolicy)
                                                params = newParams
                                                onUpdate(newParams)
                                            },
                                            minHeight = 36.dp,
                                        ) { policy, selected ->
                                            ConnectedChoiceLabel(
                                                text = "${(policy.keepRatio * 100).roundToInt()}%",
                                                selected = selected,
                                                icon = HugeIcons.Tick01,
                                            )
                                        }
                                        Text(
                                            text = "比例越低，发送量越少；" +
                                                "比例越高，保留的旧对话越多。",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showAdvancedContextSettings) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceContainerHigh.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        lastUsageReadout?.let { usage ->
                                            val pct = ((usage.cachedTokens.toDouble() / usage.promptTokens) * 100)
                                                .roundToInt()
                                                .coerceIn(0, 100)
                                            val prompt = usage.promptTokens.formatNumber()
                                            val cached = usage.cachedTokens.formatNumber()
                                            Text(
                                                text = "上轮输入 $prompt · 缓存命中 $cached ($pct%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }

                                        val preview = previewResult
                                        if (displayLimitValue > 0 && preview != null) {
                                            val truncationSummary = if (editingMode == ContextLimitMode.TOKENS) {
                                                val low = preview.targetTokensAfterTruncate
                                                val stride = (displayLimitValue - low).coerceAtLeast(0)
                                                val lowText = ContextLimitPresets.formatCompactTokens(low)
                                                val strideText = ContextLimitPresets.formatCompactTokens(stride)
                                                "裁剪后约保留 $lowText tokens，" +
                                                    "之后可再新增约 $strideText tokens"
                                            } else {
                                                val budget = (displayLimitValue - pinnedCount).coerceAtLeast(1)
                                                val targetCount = (budget * editingPolicy.keepRatio).roundToInt()
                                                    .coerceIn(1, budget)
                                                val stride = (budget - targetCount).coerceAtLeast(0)
                                                "裁剪后约保留 $targetCount 条普通消息，" +
                                                    "之后可再新增约 $stride 条"
                                            }
                                            Text(
                                                text = truncationSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onSurfaceVariant,
                                            )
                                        } else if (displayLimitValue <= 0) {
                                            Text(
                                                text = "不限制历史长度，新消息会继续追加。",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }

                                        if (params.contextAnchorNodeId != null) {
                                            TextButton(
                                                onClick = {
                                                    val resetAnchor = params.copy(contextAnchorNodeId = null)
                                                    params = resetAnchor
                                                    onUpdate(resetAnchor)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier
                                                    .align(Alignment.End)
                                                    .height(28.dp),
                                            ) {
                                                Icon(
                                                    imageVector = HugeIcons.Refresh01,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "重新包含全部历史（下次会重建缓存）",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (pinnedCount > 0) {
                                val preview = previewResult
                                val isOverBudget = when {
                                    displayLimitValue <= 0 -> false
                                    editingMode == ContextLimitMode.TOKENS ->
                                        preview != null && preview.estimatedProtectedTokens >= displayLimitValue
                                    else -> pinnedCount >= displayLimitValue
                                }
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isOverBudget) HugeIcons.Alert01 else HugeIcons.Pin02,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isOverBudget) colors.error else colors.primary,
                                    )
                                    val pinnedTokensDesc = preview?.let {
                                        val tokens = ContextLimitPresets.formatCompactTokens(
                                            it.estimatedProtectedTokens
                                        )
                                        " · 约 $tokens"
                                    }.orEmpty()
                                    val pinnedSummary =
                                        "固定 $pinnedGroups 组（$pinnedCount 条$pinnedTokensDesc）"
                                    Text(
                                        text = when {
                                            isOverBudget ->
                                                "固定消息已超过上限" +
                                                    "（$pinnedCount 条$pinnedTokensDesc），" +
                                                    "只额外保留最新提问"
                                            editingMode == ContextLimitMode.TOKENS || displayLimitValue <= 0 ->
                                                "$pinnedSummary，会始终随上下文发送"
                                            else -> {
                                                val normalShown = (displayLimitValue - pinnedCount).coerceAtLeast(0)
                                                "$pinnedSummary，另外最多发送 $normalShown 条普通消息"
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOverBudget) colors.error else colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // [FORK] Battle Mode (独立 CardGroup)
            var wasBattleEnabled by remember { mutableStateOf(params.battleModeEnabled) }
            var battleRevealActive by remember { mutableStateOf(false) }
            LaunchedEffect(params.battleModeEnabled) {
                if (params.battleModeEnabled && !wasBattleEnabled) {
                    battleRevealActive = true
                    delay(260)
                    battleRevealActive = false
                } else if (!params.battleModeEnabled) {
                    battleRevealActive = false
                }
                wasBattleEnabled = params.battleModeEnabled
            }
            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (battleRevealActive && coords.isAttached) {
                        ensureVisibleInViewport(coords)
                    }
                }
            ) {
                BattleModeSection(
                    params = params,
                    settings = settings,
                    onUpdate = { newParams ->
                        params = newParams
                        onUpdate(newParams)
                    }
                )
            }

            // 对话专属系统提示词（独立无边框卡片，内部使用柔和填充输入区替代带粗框的 OutlinedTextField）
            CardGroup {
                val promptCustomEnabled = params.systemPrompt != null
                formItem(
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.QuillWrite01,
                            containerColor = if (promptCustomEnabled) colors.primaryContainer else colors.secondaryContainer,
                            contentColor = if (promptCustomEnabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        )
                    },
                    headlineContent = {
                        Text("对话专属系统提示词")
                    },
                    trailingContent = {
                        AppSwitch(
                            checked = promptCustomEnabled,
                            onCheckedChange = { enabled ->
                                val newParams = params.copy(
                                    systemPrompt = if (enabled) assistant.systemPrompt else null
                                )
                                params = newParams
                                onUpdate(newParams)
                            }
                        )
                    },
                    contentSpacing = 0.dp,
                ) {
                    var lastPrompt by remember { mutableStateOf(assistant.systemPrompt) }
                    params.systemPrompt?.let { lastPrompt = it }
                    ParamAnimatedVisibility(
                        visible = params.systemPrompt != null,
                        onExpandingBoundsChanged = ensureVisibleInViewport,
                    ) {
                        val modes = remember {
                            listOf(
                                SystemPromptMode.APPEND,
                                SystemPromptMode.OVERRIDE,
                            )
                        }
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ConnectedChoiceButtons(
                                options = modes,
                                selected = params.systemPromptMode,
                                onSelect = { mode ->
                                    val newParams = params.copy(systemPromptMode = mode)
                                    params = newParams
                                    onUpdate(newParams)
                                },
                                minHeight = 38.dp,
                            ) { mode, selected ->
                                ConnectedChoiceLabel(
                                    text = when (mode) {
                                        SystemPromptMode.APPEND -> "追加"
                                        SystemPromptMode.OVERRIDE -> "覆盖"
                                    },
                                    selected = selected,
                                    icon = HugeIcons.Tick01,
                                )
                            }
                            TextField(
                                value = lastPrompt,
                                onValueChange = {
                                    lastPrompt = it
                                    val newParams = params.copy(systemPrompt = it)
                                    params = newParams
                                    onUpdate(newParams)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                minLines = 3,
                                maxLines = 8,
                                placeholder = {
                                    Text(
                                        text = "输入此对话的专属系统提示词…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colors.surfaceContainerHigh.copy(alpha = 0.55f),
                                    unfocusedContainerColor = colors.surfaceContainerHigh.copy(alpha = 0.55f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }

            // 温度（独立卡片，去除分割线与底部重复的 Tag 框）
            CardGroup {
                val tempCustomEnabled = params.temperature != null
                formItem(
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.DashboardSpeed02,
                            containerColor = if (tempCustomEnabled) colors.primaryContainer else colors.secondaryContainer,
                            contentColor = if (tempCustomEnabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.assistant_page_temperature))
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            params.temperature?.let { currentTemp ->
                                val modeText = when (currentTemp) {
                                    in 0.0f..0.3f -> stringResource(R.string.assistant_page_strict)
                                    in 0.3f..1.0f -> stringResource(R.string.assistant_page_balanced)
                                    in 1.0f..1.5f -> stringResource(R.string.assistant_page_creative)
                                    in 1.5f..2.0f -> stringResource(R.string.assistant_page_chaotic)
                                    else -> ""
                                }
                                CardGroupValue(
                                    text = if (modeText.isNotEmpty()) "$currentTemp · $modeText" else "$currentTemp"
                                )
                            }
                            AppSwitch(
                                checked = tempCustomEnabled,
                                onCheckedChange = { enabled ->
                                    val newParams = params.copy(
                                        temperature = if (enabled) (assistant.temperature ?: 1.0f) else null
                                    )
                                    params = newParams
                                    onUpdate(newParams)
                                }
                            )
                        }
                    },
                    contentSpacing = 0.dp,
                ) {
                    var lastTemperature by remember { mutableFloatStateOf(assistant.temperature ?: 1.0f) }
                    params.temperature?.let { lastTemperature = it }
                    ParamAnimatedVisibility(
                        visible = params.temperature != null,
                        onExpandingBoundsChanged = ensureVisibleInViewport,
                    ) {
                        AppSlider(
                            value = lastTemperature,
                            onValueChange = {
                                val next = it.toFixed(2).toFloatOrNull() ?: 0.6f
                                lastTemperature = next
                                val newParams = params.copy(temperature = next)
                                params = newParams
                                onUpdate(newParams)
                            },
                            valueRange = 0f..2f,
                            steps = 19,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }
                }
            }

            // Top P（独立卡片，去除分割线）
            CardGroup {
                val topPCustomEnabled = params.topP != null
                formItem(
                    leadingContent = {
                        CardGroupIcon(
                            icon = HugeIcons.PercentCircle,
                            containerColor = if (topPCustomEnabled) colors.primaryContainer else colors.secondaryContainer,
                            contentColor = if (topPCustomEnabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.assistant_page_top_p))
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            params.topP?.let { currentTopP ->
                                CardGroupValue(text = "$currentTopP")
                            }
                            AppSwitch(
                                checked = topPCustomEnabled,
                                onCheckedChange = { enabled ->
                                    val newParams = params.copy(
                                        topP = if (enabled) (assistant.topP ?: 1.0f) else null
                                    )
                                    params = newParams
                                    onUpdate(newParams)
                                }
                            )
                        }
                    },
                    contentSpacing = 0.dp,
                ) {
                    var lastTopP by remember { mutableFloatStateOf(assistant.topP ?: 1.0f) }
                    params.topP?.let { lastTopP = it }
                    ParamAnimatedVisibility(
                        visible = params.topP != null,
                        onExpandingBoundsChanged = ensureVisibleInViewport,
                    ) {
                        AppSlider(
                            value = lastTopP,
                            onValueChange = {
                                val next = it.toFixed(2).toFloatOrNull() ?: 1.0f
                                lastTopP = next
                                val newParams = params.copy(topP = next)
                                params = newParams
                                onUpdate(newParams)
                            },
                            valueRange = 0f..1f,
                            steps = 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }
                }
            }
                }
            }
        }
    }
}

/**
 * 同步折叠高度与淡出节奏，避免开关关闭时内容先透明而在卡片中留下空白；
 * 当开关由关闭切换为开启时，随展开动画逐帧上报外框位置以便滚动容器自动上顶露出新展开项。
 */
@Composable
private fun ParamAnimatedVisibility(
    visible: Boolean,
    onExpandingBoundsChanged: ((LayoutCoordinates) -> Unit)? = null,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    var wasVisible by remember { mutableStateOf(visible) }
    var revealActive by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible && !wasVisible) {
            revealActive = true
            delay(260)
            revealActive = false
        } else if (!visible) {
            revealActive = false
        }
        wasVisible = visible
    }
    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            if (revealActive && coords.isAttached) {
                onExpandingBoundsChanged?.invoke(coords)
            }
        }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            content = content,
        )
    }
}
