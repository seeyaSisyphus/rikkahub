package me.rerere.rikkahub.service

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.service.BattleService
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationLoop
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.data.ai.resolveWith
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"
private const val INIT_SLOW_LOG_THRESHOLD_MS = 1000L

internal fun backgroundTextGenerationParams(
    model: Model,
    conversationId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = conversationId.toString(),
)

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}
internal fun createForkConversation(
    source: Conversation,
    messageNodes: List<MessageNode>,
    existingTitles: Set<String> = emptySet(),
): Conversation = Conversation(
    id = Uuid.random(),
    assistantId = source.assistantId,
    title = generateSequence(1) { it + 1 }
        .map { "${source.title}($it)" }
        .first { it !in existingTitles },
    messageNodes = messageNodes,
    customSystemPrompt = source.customSystemPrompt,
    modeInjectionIds = source.modeInjectionIds,
    lorebookIds = source.lorebookIds,
    workspaceCwd = source.workspaceCwd,
    folderId = source.folderId,
    conversationParams = source.conversationParams,
    conversationTagId = source.conversationTagId,
)

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationLoop: GenerationLoop,
    private val translationHandler: TranslationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val chatToolFactory: ChatToolFactory,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val workspaceRepository: WorkspaceRepository,
    private val folderRepository: FolderRepository,
    // [FORK] Battle Mode Service
    private val battleService: BattleService,
    private val favoriteRepository: FavoriteRepository,
) {
    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        createInitialConversation = { id ->
            Conversation.ofId(id, assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id)
        },
        onGenerationFinished = ::onSessionGenerationFinished,
    )

    // 已删除的对话 ID 集合，防止异步任务将已删除的对话重新插入数据库
    private val deletedConversationIds = Collections.newSetFromMap(ConcurrentHashMap<Uuid, Boolean>())

    // [FORK] 临时对话：待标记为临时的对话 ID（在导航前登记，由 ChatVM.init 消费）
    private val pendingTemporaryConversationIds = Collections.newSetFromMap(ConcurrentHashMap<Uuid, Boolean>())

    // 预登记一个对话 ID 为临时对话，在导航跳转前调用
    fun schedulePendingTemporary(conversationId: Uuid) {
        pendingTemporaryConversationIds.add(conversationId)
    }

    // 消费并检查是否为待临时对话，由 ChatVM.init 在初始化完成后调用
    fun consumePendingTemporary(conversationId: Uuid): Boolean {
        return pendingTemporaryConversationIds.remove(conversationId)
    }

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
        Logging.logError(
            tag = TAG,
            title = title,
            message = error.message ?: "Unknown error",
            throwable = error,
        )
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    fun cleanup() = runCatching { sessionManager.cleanup() }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        if (cause != null) session.messageQueue.pause()
        if (session.state.value.currentMessages.any { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) {
            session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch { dispatchNextQueuedMessage(session.id) }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    // 会话是否已完成初始化（真实数据已加载）
    fun getConversationInitializedFlow(conversationId: Uuid): StateFlow<Boolean> {
        return sessionManager.getOrCreate(conversationId).initialized
    }

    // 会话初始化当前所处阶段（诊断用）
    fun getConversationInitStage(conversationId: Uuid): String {
        return sessionManager.get(conversationId)?.initStage ?: "no_session"
    }

    fun getActiveSlotCountFlow(conversationId: Uuid): Flow<Int> {
        return battleService.activeSlotCounts.map { it[conversationId] ?: 0 }
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        val startTime = System.currentTimeMillis()
        var dbMs = -1L
        sessionManager.withSession(conversationId) { session ->
            try {
                session.initStage = "db_query"
                session.initialize {
                    conversationRepo.getConversationById(conversationId) ?: run {
                        // 新建对话, 并添加预设消息
                        session.initStage = "read_settings"
                        val currentSettings = settingsStore.settingsFlowRaw.first()
                        val assistant = currentSettings.getCurrentAssistant()
                        Conversation.ofId(
                            id = conversationId,
                            assistantId = assistant.id,
                            newConversation = true
                        ).updateCurrentMessages(assistant.presetMessages)
                    }
                }
                dbMs = System.currentTimeMillis() - startTime
                session.initStage = "update_assistant"
                settingsStore.updateAssistant(session.state.value.assistantId)
            } finally {
                // 无论成功失败都标记初始化完成，失败时退化为旧行为（展示空会话而非永久 loading）
                session.initStage = "done"
                session.markInitialized()
                val totalMs = System.currentTimeMillis() - startTime
                // 初始化缓慢时写入应用内错误日志模块，便于无 logcat 场景定位冷启动卡顿
                if (totalMs >= INIT_SLOW_LOG_THRESHOLD_MS) {
                    Logging.logError(
                        tag = TAG,
                        title = "会话初始化缓慢",
                        message = "id=$conversationId, db=${dbMs}ms, total=${totalMs}ms"
                    )
                }
            }
        }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        sessionManager.getMessageQueueFlow(conversationId)

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = sessionManager.getOrCreate(conversationId)
        battleService.cancelAllSlots(conversationId)
        synchronized(session) {
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(session.state.value.currentMessages.none { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) { context.getString(R.string.chat_page_voice_tools_before_resume) }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.currentMessages.any { message ->
                    message.parts.any { it is UIMessagePart.Tool && it.isPending }
                }) return null
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        val content = queued.parts
        val answer = queued.answer
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = answer,
        ) {
            try {
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val processedContent = preprocessUserInputParts(content, assistant)

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)
                session.submittingMessage = null

                // 开始补全
                if (answer) {
                    dispatchGeneration(
                        conversationId = conversationId,
                        conversation = newConversation,
                        session = session,
                    )
                }

                queued.reply?.completeWith(runCatching {
                    val messages = session.state.value.currentMessages
                    check(!session.messageQueue.state.value.paused) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check(messages.none { message -> message.parts.any { it is UIMessagePart.Tool && it.isPending } }) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    val previousIds = currentConversation.currentMessages.map { it.id }.toSet()
                    messages.filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) queued.reply?.completeExceptionally(cause)
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        return job
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>, assistant: Assistant): List<UIMessagePart> {
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    /**
     * [FORK] Battle 单 slot 重试前清理：重试会原位替换该回答的内容，
     * 而 pin/收藏是对旧内容的背书，内容被替换后应由用户重新审视，故清除。
     * 仅当被重试的 message 正是锚定/收藏的那条时才清除；
     * 配对的 USER 提问 pin 保留（其内容未被重试改变）。
     */
    private suspend fun clearAnchorAndFavoriteOnSlotRetry(
        conversationId: Uuid,
        node: MessageNode,
        messageId: Uuid,
    ) {
        val clearPin = node.pinnedMessageId == messageId
        val clearFavorite = node.favoriteMessageId == messageId
        if (!clearPin && !clearFavorite) return

        if (clearFavorite) {
            favoriteRepository.removeNodeFavorite(conversationId, node.id)
        }
        updateConversationState(conversationId) { conv ->
            conv.copy(
                messageNodes = conv.messageNodes.map { n ->
                    if (n.id != node.id) {
                        n
                    } else {
                        n.copy(
                            pinnedMessageId = if (clearPin) null else n.pinnedMessageId,
                            favoriteMessageId = if (clearFavorite) null else n.favoriteMessageId,
                        )
                    }
                }
            )
        }
        saveConversation(conversationId, getConversationFlow(conversationId).value)
    }

    /**
     * [FORK] 同步清理悬空收藏：重试截断/删除消息/压缩上下文后，
     * 收藏表中指向已删除节点或已删除消息的条目会变成无法打开的悬空数据。
     * 以当前会话状态为准对账：节点不存在、或收藏的具体 message 已不在节点内，则删除该收藏，
     * 并同步清掉内存节点上残留的 favoriteMessageId。
     */
    private suspend fun cleanupDanglingNodeFavorites(conversationId: Uuid) {
        val refs = favoriteRepository.getNodeFavoriteRefsOfConversation(conversationId)
        if (refs.isEmpty()) return

        val conversation = getConversationFlow(conversationId).value
        val messageIdsByNode = conversation.messageNodes.associate { node ->
            node.id to node.messages.map { it.id }.toSet()
        }
        val danglingNodeIds = refs.mapNotNull { ref ->
            val validIds = messageIdsByNode[ref.nodeId]
            when {
                validIds == null -> ref.nodeId  // 节点已删除
                ref.messageId != null && ref.messageId !in validIds -> ref.nodeId  // 收藏的具体 message 已不在节点内
                else -> null
            }
        }.distinct()
        if (danglingNodeIds.isEmpty()) return

        favoriteRepository.removeNodeFavorites(conversationId, danglingNodeIds)
        updateConversationState(conversationId) { conv ->
            conv.copy(
                messageNodes = conv.messageNodes.map { node ->
                    if (node.id in danglingNodeIds) {
                        node.copy(favoriteMessageId = null)
                    } else {
                        node
                    }
                }
            )
        }
    }

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)

        // 先判断是否为 Battle 单 slot 重试（不取消主 Job）
        val snapshot = session.state.value
        val node = snapshot.getMessageNodeByMessageId(message.id)
        val isSingleSlotRetry = node?.isBattleNodeEffective == true &&
                message.role == MessageRole.ASSISTANT &&
                regenerateAssistantMsg

        if (isSingleSlotRetry) {
            // Battle 单 slot 重试：不取消主 Job，直接委托 BattleService
            val currentModelId = node.messages
                .firstOrNull { it.id == message.id }?.modelId ?: message.modelId
            val nodeIndex = snapshot.messageNodes.indexOf(node)
            val contextConversation = snapshot.copy(
                messageNodes = snapshot.messageNodes.subList(0, nodeIndex)
            )

            appScope.launch {
                clearAnchorAndFavoriteOnSlotRetry(conversationId, node, message.id)
                battleService.rerunSlot(
                    conversationId = conversationId,
                    battleNodeId = node.id,
                    messageId = message.id,
                    modelId = currentModelId ?: return@launch,
                    conversation = contextConversation,
                    getConversation = { getConversationFlow(conversationId).value },
                    updateConversationState = ::updateConversationState,
                    saveConversation = ::saveConversation,
                    processingStatus = session.processingStatus,
                )
            }
            return  // 不调用 session.setJob()，不干扰主 Battle Job
        }

        // 非 Battle 单 slot 重试：全量重试逻辑
        battleService.cancelAllSlots(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = message.role == MessageRole.USER || regenerateAssistantMsg,
        ) {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val conversation = session.state.value
                // [FORK] Battle Mode: 提前读取参数，以便在各分支判断
                val battleParams = conversation.conversationParams
                val isBattle = battleParams.battleModeEnabled && battleParams.battleModelIds.isNotEmpty()

                if (message.role == MessageRole.USER) {
                    // 用 id 查找节点，避免异步更新导致 equals 失效
                    val nodeInner = conversation.getMessageNodeByMessageId(message.id)
                    val indexAt = conversation.messageNodes.indexOf(nodeInner)
                    // [FORK] 安全防护：定位不到目标 USER 节点（id 不在当前状态）时绝不截断保存，
                    // 否则 indexOf 返回 -1 → subList(0, 0) → 空会话被持久化 → 误删全部历史。
                    // 此处宁可放弃本次重试，也不能丢历史数据。
                    if (nodeInner == null || indexAt < 0) {
                        Log.w(TAG, "regenerateAtMessage: USER node not found (id=${message.id}), abort to avoid data loss")
                        return@launchGenerationJob
                    }
                    // subList(0, indexAt + 1) 保留“该 USER 消息及其之前”的全部历史，仅移除其之后的回答。
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    cleanupDanglingNodeFavorites(conversationId)
                    dispatchGeneration(
                        conversationId = conversationId,
                        conversation = newConversation,
                        session = session,
                    )
                } else {
                    if (regenerateAssistantMsg) {
                        // [FORK] Battle Mode 根本修复：
                        // 用 message.id 查找 node，而不是 equals（因为 cancel 后 onCompletion 会
                        // 调用 finishReasoning 修改消息内容，导致快照对象与最新状态不 equals，
                        // getMessageNodeByMessage 返回 null，从而错误地触发全量重试所有模型）
                        val nodeInner = conversation.getMessageNodeByMessageId(message.id)
                        val nodeIndex = conversation.messageNodes.indexOf(nodeInner)
                        // 从最新状态的 node 中取 modelId，而非依赖已过期的快照消息
                        val currentModelId = nodeInner?.messages?.firstOrNull { it.id == message.id }?.modelId
                            ?: message.modelId
                        if (nodeInner != null && nodeInner.isBattleNodeEffective && currentModelId != null) {
                            // [FORK] Battle Mode: 消息属于 battle 节点，只重试当前显示的模型
                            val contextConversation = conversation.copy(
                                messageNodes = conversation.messageNodes.subList(0, nodeIndex)
                            )
                            clearAnchorAndFavoriteOnSlotRetry(conversationId, nodeInner, message.id)
                            battleService.rerunSlot(
                                conversationId = conversationId,
                                battleNodeId = nodeInner.id,
                                messageId = message.id,
                                modelId = currentModelId,
                                conversation = contextConversation,
                                getConversation = { getConversationFlow(conversationId).value },
                                updateConversationState = ::updateConversationState,
                                saveConversation = ::saveConversation,
                                processingStatus = session.processingStatus,
                            )
                        } else {
                            // [FORK] 安全防护：定位不到目标节点（nodeInner 为 null → nodeIndex 为 -1）时
                            // 绝不截断保存，避免 subList 越界或把历史误删。宁可放弃本次重试。
                            if (nodeInner == null || nodeIndex < 0) {
                                Log.w(TAG, "regenerateAtMessage: ASSISTANT node not found (id=${message.id}), abort to avoid data loss")
                                return@launchGenerationJob
                            }
                            // [FORK] Battle Mode: 非 battle 节点或普通模式，统一走 dispatchGeneration
                            // subList(0, nodeIndex) 只保留“被重试节点之前”的全部历史，
                            // 仅移除被重试的这条回答（含中断产生的半截回答）及其之后的节点。
                            val contextConversation = conversation.copy(
                                messageNodes = conversation.messageNodes.subList(0, nodeIndex)
                            )
                            // [FORK] 方案A：非 Battle 也持久化截断后的会话，让重试结果替换被重试的回答，
                            // 不再堆叠在其下方成为新节点。截断只发生在“被重试节点及其之后”，之前历史完整保留。
                            saveConversation(conversationId, contextConversation)
                            cleanupDanglingNodeFavorites(conversationId)
                            dispatchGeneration(
                                conversationId = conversationId,
                                conversation = contextConversation,
                                session = session,
                                messageRange = if (isBattle) null else 0..<nodeIndex,
                            )
                        }
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        battleService.cancelAllSlots(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val hasOtherPendingTools = session.state.value.messageNodes.any { node ->
            node.currentMessage.parts.any { part ->
                part is UIMessagePart.Tool && part.isPending && part.toolCallId != toolCallId
            }
        }

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = !hasOtherPendingTools,
        ) {
            try {
                finishInterruptedPendingTools(conversationId)
                afterPreviousGeneration(previousJob) {
                    val conversation = session.state.value
                    // Ignore double taps and stale approvals for completed or inactive tools.
                    if (conversation.currentMessages.none { message ->
                            message.getTools().any { it.toolCallId == toolCallId && it.isPending }
                        }) return@afterPreviousGeneration
                    val newApprovalState = when {
                        answer != null -> ToolApprovalState.Answered(answer)
                        approved -> ToolApprovalState.Approved
                        else -> ToolApprovalState.Denied(reason)
                    }

                    // Update the tool approval state
                    val updatedNodes = conversation.messageNodes.map { node ->
                        node.copy(
                            messages = node.messages.map { msg ->
                                msg.copy(
                                    parts = msg.parts.map { part ->
                                        when {
                                            part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                                part.copy(approvalState = newApprovalState)
                                            }

                                            else -> part
                                        }
                                    }
                                )
                            }
                        )
                    }
                    val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                    saveConversation(conversationId, updatedConversation)

                    // Check if there are still pending tools
                    val hasPendingTools = updatedNodes.any { node ->
                        node.currentMessage.parts.any { part ->
                            part is UIMessagePart.Tool && part.isPending
                        }
                    }

                    // Only continue generation when all pending tools are handled
                    if (!hasPendingTools) {
                        handleMessageComplete(conversationId)
                    }

                    _generationDoneFlow.emit(conversationId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job, cancelPrevious = false)
    }

    // ---- [FORK] Battle Mode 生成路由 ----

    /**
     * [FORK] Battle Mode 路由入口。
     * 根据 ConversationParams 决定走 BattleService 并发生成，还是走普通的 handleMessageComplete。
     * 将 Battle Mode 分支收拢到此处，避免在 sendMessage/regenerateAtMessage 里散落 if/else。
     */
    private suspend fun dispatchGeneration(
        conversationId: Uuid,
        conversation: Conversation,
        session: ConversationSession,
        messageRange: ClosedRange<Int>? = null,
    ) {
        val battleParams = conversation.conversationParams
        if (battleParams.battleModeEnabled && battleParams.battleModelIds.isNotEmpty()) {
            battleService.runBattle(
                conversationId = conversationId,
                conversation = conversation,
                battleModelIds = battleParams.battleModelIds,
                getConversation = { getConversationFlow(conversationId).value },
                updateConversationState = ::updateConversationState,
                saveConversation = ::saveConversation,
                processingStatus = session.processingStatus,
            )
        } else {
            handleMessageComplete(conversationId, messageRange)
        }
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null,
        retryCount: Int = 0
    ) {
        val maxRetries = 3
        val retryDelayMs = 2000L
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
            ?: throw IllegalStateException("No chat model selected")

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }
        val useExternalWebSearch = shouldUseExternalWebSearch(assistant, model)

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (useExternalWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            val tools = try {
                chatToolFactory.createTools(
                    settings = settings,
                    assistant = assistant,
                    model = model,
                    workspaceCwd = conversation.workspaceCwd,
                )
            } catch (error: InvalidMcpServerNamesException) {
                sessionManager.get(conversationId)?.messageQueue?.pause()
                addError(
                    error = IllegalStateException(
                        context.getString(
                            R.string.error_mcp_invalid_server_name,
                            error.names.joinToString(", "),
                        )
                    ),
                    conversationId = conversationId,
                )
                return
            }

            // start generating
            val session = sessionManager.getOrCreate(conversationId)

            // [FORK] 固定到上下文的「整 turn」消息 id：在 limitContext 中优先占用总预算，超出固定上限时自动砍旧固定。
            // 收藏(favorite)仅为书签，不再进入上下文。
            // 仅在普通发送路径下计算,messageRange(重生成)路径不携带(避免 token 暴涨)
            val protectedMessageIds: Set<Uuid> = if (messageRange != null) {
                emptySet()
            } else {
                conversation.collectProtectedMessageIds()
            }
            generationLoop.generateText(
                settings = settings,
                model = model,
                processingStatus = session.processingStatus,
                messages = if (messageRange != null) {
                    // 重生成某条：按索引截取,不再走 pin/favorite 保护
                    // [FORK] contextMessages 与 currentMessages 一一对应（每节点一条），索引对齐，截取安全
                    conversation.contextMessages.subList(
                        messageRange.start.coerceAtLeast(0),
                        (messageRange.endInclusive + 1).coerceAtMost(conversation.contextMessages.size)
                    )
                } else {
                    // 正常发送：全量消息 + protectedMessageIds 让 GenerationHandler.limitContext 保留收藏/固定
                    // [FORK] 使用 contextMessages：被固定节点强制使用固定时锚定的分支，不随 selectIndex 变化
                    conversation.contextMessages
                },
                protectedMessageIds = protectedMessageIds,
                // [FORK] 显式传 conversationParams,确保对话专属 contextMessageSize 在 GenerationHandler.limitContext 中生效
                // (若不传,generateInternal 会用默认空 ConversationParams(),只能读到助手级别设置)
                conversationParams = conversation.conversationParams,
                assistant = assistant,
                conversationId = conversationId,
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = conversation.workspaceCwd,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(workspaceReminderTransformer)
                },
                outputTransformers = outputTransformers,
                tools = tools,
            ).onCompletion {
                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = session.finishGeneration { conversation ->
                    saveConversation(conversationId, conversation)
                }

                // 生成结束：取消 Live Update 通知，后台时发送完成通知
                appEventBus.emit(
                    AppEvent.ChatGenerationEnded(
                        conversationId = conversationId,
                        senderName = senderName,
                        contentPreview = updatedConversation.currentMessages.lastOrNull()
                            ?.toText()?.take(50)?.trim() ?: "",
                    )
                )
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

                        // 通知等边缘副作用由 ChatNotificationManager 消费；
                        // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                        chunk.messages.lastOrNull()?.let { lastMessage ->
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                            )
                        }
                    }
                }
            }
        }.onFailure {
            // 兜底取消 Live Update 通知（生成开始前失败时 onCompletion 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            if (it is CancellationException) throw it
            sessionManager.get(conversationId)?.messageQueue?.pause()

            it.printStackTrace()

            // 自动重试：非429错误、非取消、未达最大重试次数时自动重试
            val is429 = it.message?.contains("429") == true
            val isCancellation = it is CancellationException
            if (!is429 && !isCancellation && retryCount < maxRetries) {
                Log.i(TAG, "Auto-retry ${retryCount + 1}/$maxRetries after error: ${it.message}")
                addError(it, conversationId, title = "${context.getString(R.string.error_title_generation)} (${retryCount + 1}/$maxRetries 重试中...)")
                delay(retryDelayMs)
                // 兜底：provider 层的取消信号可能被错误体解析吞掉（见各 provider onFailure），
                // 导致 delay 不抛 CancellationException；这里在重试发起前再检查一次协程活性，
                // 确保用户点"暂停"后不会继续发起新的重试请求。
                currentCoroutineContext().ensureActive()
                handleMessageComplete(conversationId, messageRange, retryCount + 1)
                return
            }

            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value

            sessionManager.launchWithSession(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            sessionManager.launchWithSession(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
        }
    }

    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            )
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        saveConversation(conversationId, updatedConversation)
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return@withContext

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.fastModelId)
                ?: return@runCatching
            val provider = model.findProvider(settings.providers) ?: return@runCatching

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText(maxLength = 500) })
                    ),
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.message.toText().trim())
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckFastModelSettings,
            )
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(
        conversationId: Uuid,
        conversation: Conversation,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            if (!settings.enableSuggestion) return@runCatching
            val model = settings.findModelById(settings.fastModelId)
                ?: return@runCatching
            val provider = model.findProvider(settings.providers) ?: return@runCatching

            sessionManager.get(conversationId)?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText(maxLength = 500) }),
                    )
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )
            val suggestions =
                result.message.toText().split("\n").map { it.trim() }
                    .filter { it.isNotBlank() }

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessionManager.get(conversationId)?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_suggestion)
            )
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText(maxLength = 2000) }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = backgroundTextGenerationParams(model, conversationId),
            )

            return result.message.toText().trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
        cleanupDanglingNodeFavorites(conversationId)
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = sessionManager.getOrCreate(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.updateConversation(conversation)
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        update: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize {
                conversationRepo.getConversationById(conversationId)
                    ?: throw NotFoundException("Conversation not found")
            }
            session.updateMetadata(update, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            update = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            update = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) != null) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威（移动会先同步内存态）。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹（folder_id 归属会被清空，会话本身保留）。
     *
     * 先把内存中归属该文件夹的活跃 session folderId 置空，再删库：
     * 否则 clearFolder 只改了数据库，而活跃 session 内存态仍指向该文件夹，
     * 后续整对象保存会写回一个已被删除的 folder_id，导致会话在列表中悬空。
     */
    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val session = sessionManager.get(newConversation.id)
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
                listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val newFiles = newConversation.files + queuedFiles
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        // 已被删除的对话不允许重新插入，防止异步任务（如生成标题/建议）将其复活
        if (!exists && deletedConversationIds.contains(conversation.id)) {
            Log.w(TAG, "saveConversation: skipping insert for deleted conversation ${conversation.id}")
            return
        }

        // 临时对话只更新内存状态，不写入数据库
        if (conversation.isTemporary) {
            updateConversation(conversationId, conversation)
            return
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }

        // 删除消息或切换分支也可能解除工具审批阻塞，保存成功后重新检查队列。
        // 调度器仍会检查当前生成任务、待审批工具、暂停状态及编辑占位。
        dispatchNextQueuedMessage(conversationId)
    }

    /**
     * 标记对话已被删除，防止后续异步任务将其重新插入数据库
     */
    fun markConversationDeleted(conversationId: Uuid) {
        deletedConversationIds.add(conversationId)
        // 同时取消该对话的生成任务
        sessionManager.get(conversationId)?.getJob()?.cancel()
        Log.i(TAG, "markConversationDeleted: $conversationId")
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                translationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            // 直接替换原消息，不新增分支
            // 用 copy 仅替换 parts，保留原消息的 modelId / modelName / usage 等元数据，
            // 否则编辑 assistant 回答（如 Battle Mode）后会丢失模型名称与 token 用量显示
            val newParts = if (node.role == MessageRole.USER) {
                // 用户输入正则预处理仅适用于用户消息
                preprocessUserInputParts(parts, assistant)
            } else {
                parts
            }
            val newMessages = node.messages.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.id == messageId }
                if (idx != -1) list[idx] = list[idx].copy(parts = newParts)
            }
            node.copy(
                messages = newMessages,
                selectIndex = node.selectIndex.coerceAtMost(newMessages.lastIndex)
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val existingTitles = conversationRepo
            .getConversationsOfAssistant(currentConversation.assistantId)
            .first()
            .mapTo(mutableSetOf()) { it.title }
        val forkConversation = createForkConversation(currentConversation, copiedNodes, existingTitles)

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessagesBeforeMessage(
        conversationId: Uuid,
        messageId: Uuid,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) return
        if (targetNodeIndex == 0) return // 没有之前的消息，无需操作

        val updatedNodes = currentConversation.messageNodes.subList(targetNodeIndex, currentConversation.messageNodes.size)
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
        cleanupDanglingNodeFavorites(conversationId)
    }

    suspend fun deleteMessagesAfterMessage(
        conversationId: Uuid,
        messageId: Uuid,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) return
        val lastIndex = currentConversation.messageNodes.lastIndex
        if (targetNodeIndex == lastIndex) return // 没有之后的消息，无需操作

        val updatedNodes = currentConversation.messageNodes.subList(0, targetNodeIndex + 1)
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
        cleanupDanglingNodeFavorites(conversationId)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
        cleanupDanglingNodeFavorites(conversationId)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        val currentConversation = getConversationFlow(conversationId).value

        // 如果删除的是 USER 消息，连带删除其后紧跟的 ASSISTANT 节点（如果有）
        if (message.role == me.rerere.ai.core.MessageRole.USER) {
            val nodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
                node.messages.any { it.id == message.id }
            }
            val nextNode = currentConversation.messageNodes.getOrNull(nodeIndex + 1)
            if (nodeIndex != -1 && nextNode != null &&
                nextNode.currentMessage.role == me.rerere.ai.core.MessageRole.ASSISTANT
            ) {
                // 先删除当前 USER 节点，再删除紧跟的 ASSISTANT 节点
                val afterDeleteUser = buildConversationAfterMessageDelete(currentConversation, message.id)
                    ?: return
                // 删除 USER 节点后，原来的 nextNode 在新列表中仍在相同 nodeIndex 位置
                val assistantNodeInNew = afterDeleteUser.messageNodes.getOrNull(nodeIndex)
                if (assistantNodeInNew != null &&
                    assistantNodeInNew.id == nextNode.id
                ) {
                    // 删除对应的 ASSISTANT 节点（整个节点）
                    val finalNodes = afterDeleteUser.messageNodes.filterIndexed { idx, _ -> idx != nodeIndex }
                    saveConversation(conversationId, afterDeleteUser.copy(messageNodes = finalNodes))
                } else {
                    saveConversation(conversationId, afterDeleteUser)
                }
                cleanupDanglingNodeFavorites(conversationId)
                return
            }
        }

        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    suspend fun stopGeneration(conversationId: Uuid) {
        battleService.cancelAllSlots(conversationId)
        val session = sessionManager.get(conversationId) ?: return
        val jobs = synchronized(session) {
            session.messageQueue.pause()
            session.cancelJobs()
        }
        if (jobs.isEmpty()) return
        jobs.forEach { it.join() }
        finishInterruptedPendingTools(conversationId)
    }
}
