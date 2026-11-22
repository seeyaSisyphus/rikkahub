package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Attachment01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.Pin02
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.uuid.Uuid

/** [FORK] 概览筛选：全部 / 只看收藏 / 只看固定到上下文的轮次 */
internal enum class PreviewFilter { ALL, FAVORITE, PINNED }

/**
 * 概览里的一张卡片：一轮对话 = 一条用户消息 + 后面跟着的助手回复（工具调用会拆成多个节点，这里合并）。
 * 对话开头没有用户消息时（如开场白），[user] 为空。
 */
private data class PreviewRound(
    // 这一轮第一个节点的 id，用作列表 key
    val id: Uuid,
    // 1-based 轮次序号
    val roundIndex: Int,
    // 点击跳转的目标，取这一轮第一个节点
    val firstIndex: Int,
    val lastIndex: Int,
    val user: UIMessage?,
    val reply: UIMessage?,
    // 去掉 Markdown 标记、折叠空白后的纯文本，搜索和摘要都用它
    val userText: String,
    val replyText: String,
    val replyParts: List<UIMessagePart>,
    val favorite: Boolean,
    // [FORK] 这一轮里有没有被固定到上下文的节点
    val pinned: Boolean,
    // 压缩检查点单独成一张卡片，userText 是摘要
    val checkpoint: Boolean,
) {
    fun matches(query: String): Boolean =
        userText.contains(query, ignoreCase = true) || replyText.contains(query, ignoreCase = true)
}

private fun List<MessageNode>.toPreviewRounds(): List<PreviewRound> {
    val groups = mutableListOf<MutableList<IndexedValue<MessageNode>>>()
    forEachIndexed { index, node ->
        // 检查点本身是 USER 消息会自己开一组；它后面的节点也要另起一组，不能并进检查点卡片
        val afterCheckpoint = groups.lastOrNull()?.last()?.value?.currentMessage?.isContextCheckpoint == true
        if (node.currentMessage.role == MessageRole.USER || groups.isEmpty() || afterCheckpoint) {
            groups.add(mutableListOf())
        }
        groups.last().add(IndexedValue(index, node))
    }
    return groups.mapIndexed { idx, group ->
        val user = group.first().value.currentMessage.takeIf { it.role == MessageRole.USER }
        val replies = group.map { it.value.currentMessage }.filter { it.role != MessageRole.USER }
        PreviewRound(
            id = group.first().value.id,
            roundIndex = idx + 1,
            firstIndex = group.first().index,
            lastIndex = group.last().index,
            user = user,
            reply = replies.lastOrNull(),
            userText = user?.toPreviewText().orEmpty(),
            replyText = replies.map { it.toPreviewText() }.filter { it.isNotEmpty() }.joinToString(" "),
            replyParts = replies.flatMap { it.parts },
            favorite = group.any { it.value.isFavorite },
            pinned = group.any { it.value.isPinned },
            checkpoint = user?.isContextCheckpoint == true,
        )
    }
}

/**
 * 消息概览（对话地图）：聊天页左滑或点击顶栏按钮进入，每轮对话一张卡片，点击跳到对应位置。
 * [chatListState] 用来标出当前阅读位置，并在打开时把它滚到可见范围内。
 */
@Composable
internal fun ChatListPreview(
    innerPadding: PaddingValues,
    conversation: Conversation,
    chatListState: LazyListState,
    listState: LazyListState,
    settings: Settings,
    hazeState: HazeState,
    onJumpToMessage: (Int) -> Unit,
    // [FORK] 概览筛选（全部 / 收藏 / 固定），状态由 ChatList 持有
    previewFilter: PreviewFilter = PreviewFilter.ALL,
    onPreviewFilterChange: (PreviewFilter) -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val query = searchQuery.trim()

    // 长对话里逐条做正则清洗很慢，放到后台算；没算完时为 null。
    // 生成中节点每个 chunk 都在变，conflate 掉中间状态，只算最新的
    val nodes by rememberUpdatedState(conversation.messageNodes)
    val loadedRounds by produceState<List<PreviewRound>?>(initialValue = null) {
        snapshotFlow { nodes }
            .conflate()
            .collect { value = withContext(Dispatchers.Default) { it.toPreviewRounds() } }
    }
    val rounds = loadedRounds.orEmpty()
    val filtered = remember(rounds, query, previewFilter) {
        rounds.filter { round ->
            val matchesQuery = query.isEmpty() || round.matches(query)
            val matchesFilter = when (previewFilter) {
                PreviewFilter.ALL -> true
                PreviewFilter.FAVORITE -> round.favorite
                PreviewFilter.PINNED -> round.pinned
            }
            matchesQuery && matchesFilter
        }
    }

    val assistant = remember(settings.assistants, conversation.assistantId) {
        settings.getAssistantById(conversation.assistantId)
    }
    val modelById = remember(settings.providers) {
        settings.providers.flatMap { it.models }.associateBy { it.id }
    }

    // 打开时的阅读位置；概览期间聊天列表不动，记一次就够
    val currentIndex = remember { chatListState.firstVisibleItemIndex }
    val loaded = loadedRounds != null
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        val currentRound = rounds.indexOfFirst { currentIndex in it.firstIndex..it.lastIndex }
        listState.scrollToItem((currentRound - 1).coerceAtLeast(0))
    }
    // 搜索框固定在列表外，滚到中间时改搜索词，结果要从头看
    LaunchedEffect(listState) {
        snapshotFlow { searchQuery.trim() }.drop(1).collect { listState.scrollToItem(0) }
    }

    val previewTopPadding = remember(innerPadding) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

            override fun calculateTopPadding(): Dp = innerPadding.calculateTopPadding()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp

            override fun calculateBottomPadding(): Dp = 0.dp
        }
    }
    val previewContentPadding = remember(innerPadding) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp

            override fun calculateTopPadding(): Dp = 4.dp

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp

            override fun calculateBottomPadding(): Dp = 32.dp + innerPadding.calculateBottomPadding()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(previewTopPadding),
    ) {
        // 顶部固定控制面板：搜索栏 + 筛选胶囊与字数统计
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                resultCount = filtered.size,
                totalCount = rounds.size,
                modifier = Modifier.fillMaxWidth(),
            )
            PreviewFilterAndStatsRow(
                rounds = rounds,
                previewFilter = previewFilter,
                onPreviewFilterChange = onPreviewFilterChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 对话轮次卡片列表
        LazyColumn(
            state = listState,
            contentPadding = previewContentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .hazeSource(state = hazeState),
        ) {
            if (loaded && filtered.isEmpty()) {
                item(key = PreviewEmptyKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PreviewEmptyIllustration(modifier = Modifier.size(72.dp))
                        Text(
                            text = stringResource(R.string.search_page_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            items(items = filtered, key = { it.id }) { round ->
                if (round.checkpoint) {
                    PreviewCheckpointCard(
                        round = round,
                        query = query,
                        isCurrent = currentIndex in round.firstIndex..round.lastIndex,
                        onClick = { onJumpToMessage(round.firstIndex) },
                        modifier = Modifier.animateItem(),
                    )
                    return@items
                }
                val reply = round.reply
                PreviewRoundCard(
                    round = round,
                    query = query,
                    assistant = assistant,
                    model = reply?.let { it.modelId?.let(modelById::get) ?: it.snapshotModel() },
                    isCurrent = currentIndex in round.firstIndex..round.lastIndex,
                    onClick = { onJumpToMessage(round.firstIndex) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private val PreviewSearchBarHeight = 48.dp

/** [FORK] 筛选胶囊栏 + 字数统计行 */
@Composable
private fun PreviewFilterAndStatsRow(
    rounds: List<PreviewRound>,
    previewFilter: PreviewFilter,
    onPreviewFilterChange: (PreviewFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val favoriteCount = remember(rounds) { rounds.count { it.favorite } }
    val pinnedCount = remember(rounds) { rounds.count { it.pinned } }
    val (userChars, replyChars) = remember(rounds) {
        rounds.sumOf { it.userText.length } to rounds.sumOf { it.replyText.length }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 左侧三态直达筛选胶囊：全部 / 收藏 / 固定
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewFilterChip(
                label = "全部",
                count = rounds.size,
                icon = null,
                selected = previewFilter == PreviewFilter.ALL,
                onClick = { onPreviewFilterChange(PreviewFilter.ALL) },
            )
            PreviewFilterChip(
                label = "收藏",
                count = favoriteCount,
                icon = HugeIcons.Favourite,
                selected = previewFilter == PreviewFilter.FAVORITE,
                onClick = {
                    onPreviewFilterChange(
                        if (previewFilter == PreviewFilter.FAVORITE) PreviewFilter.ALL else PreviewFilter.FAVORITE
                    )
                },
            )
            PreviewFilterChip(
                label = "固定",
                count = pinnedCount,
                icon = HugeIcons.Pin02,
                selected = previewFilter == PreviewFilter.PINNED,
                onClick = {
                    onPreviewFilterChange(
                        if (previewFilter == PreviewFilter.PINNED) PreviewFilter.ALL else PreviewFilter.PINNED
                    )
                },
            )
        }

        // 右侧提问与回答字数统计
        Text(
            text = "问 ${"%,d".format(userChars)} · 答 ${"%,d".format(replyChars)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun PreviewFilterChip(
    label: String,
    count: Int,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = if (count > 0 || selected) "$label $count" else label,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private const val PreviewEmptyKey = "PreviewEmpty"

/**
 * 极简线框叠层空状态插画：双层对话卡片剪影 + 留白裁切放大镜与高光弧线。
 */
@Composable
private fun PreviewEmptyIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val isDark = LocalDarkMode.current
    val frontCardFill = if (isDark) colors.surfaceContainer else colors.surfaceContainerLowest
    val backCardFill = colors.surfaceContainerHigh.copy(alpha = 0.55f)
    val backCardStroke = colors.outlineVariant.copy(alpha = 0.65f)
    val frontCardStroke = colors.onSurfaceVariant.copy(alpha = 0.42f)
    val linePrimary = colors.onSurfaceVariant.copy(alpha = 0.34f)
    val lineSecondary = colors.onSurfaceVariant.copy(alpha = 0.2f)
    val lensTint = colors.primary.copy(alpha = 0.08f)
    val lensStroke = colors.onSurfaceVariant.copy(alpha = 0.85f)
    val glintColor = colors.primary.copy(alpha = 0.72f)

    Canvas(
        modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val backRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        val frontRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

        // 后层卡片轮廓
        val backTopLeft = Offset(20.dp.toPx(), 10.dp.toPx())
        val backSize = Size(36.dp.toPx(), 38.dp.toPx())
        drawRoundRect(
            color = backCardFill,
            topLeft = backTopLeft,
            size = backSize,
            cornerRadius = backRadius,
        )
        drawRoundRect(
            color = backCardStroke,
            topLeft = backTopLeft,
            size = backSize,
            cornerRadius = backRadius,
            style = Stroke(width = 1.25.dp.toPx()),
        )

        // 前层主卡片轮廓
        val frontTopLeft = Offset(12.dp.toPx(), 16.dp.toPx())
        val frontSize = Size(38.dp.toPx(), 40.dp.toPx())
        drawRoundRect(
            color = frontCardFill,
            topLeft = frontTopLeft,
            size = frontSize,
            cornerRadius = frontRadius,
        )
        drawRoundRect(
            color = frontCardStroke,
            topLeft = frontTopLeft,
            size = frontSize,
            cornerRadius = frontRadius,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 卡片内极简占位横线
        drawLine(
            color = linePrimary,
            start = Offset(20.dp.toPx(), 27.dp.toPx()),
            end = Offset(41.dp.toPx(), 27.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = lineSecondary,
            start = Offset(20.dp.toPx(), 35.dp.toPx()),
            end = Offset(34.dp.toPx(), 35.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 前景放大镜（外围描边裁切卡片交界处）
        val lensCenter = Offset(46.dp.toPx(), 46.dp.toPx())
        val lensRadius = 10.dp.toPx()
        val handleStart = Offset(46.dp.toPx() + 7.1.dp.toPx(), 46.dp.toPx() + 7.1.dp.toPx())
        val handleEnd = Offset(60.5.dp.toPx(), 60.5.dp.toPx())

        drawCircle(
            color = Color.Transparent,
            radius = lensRadius + 2.5.dp.toPx(),
            center = lensCenter,
            blendMode = BlendMode.Clear,
        )
        drawLine(
            color = Color.Transparent,
            start = handleStart,
            end = handleEnd,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
            blendMode = BlendMode.Clear,
        )

        drawCircle(
            color = lensTint,
            radius = lensRadius,
            center = lensCenter,
        )
        drawCircle(
            color = lensStroke,
            radius = lensRadius,
            center = lensCenter,
            style = Stroke(width = 1.75.dp.toPx()),
        )
        val glintRadius = 6.dp.toPx()
        drawArc(
            color = glintColor,
            startAngle = 200f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(lensCenter.x - glintRadius, lensCenter.y - glintRadius),
            size = Size(glintRadius * 2f, glintRadius * 2f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = lensStroke,
            start = handleStart,
            end = handleEnd,
            strokeWidth = 2.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PreviewSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(PreviewSearchBarHeight)
            // 整条胶囊都可点聚焦，且不产生 clickable 的语义合并
            .pointerInput(Unit) {
                detectTapGestures {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            },
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Search01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_page_search_messages),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.onSurface,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            Surface(
                shape = CircleShape,
                color = if (query.isNotBlank()) colors.secondaryContainer else colors.surfaceContainerHighest,
                modifier = Modifier.padding(end = if (query.isEmpty()) 8.dp else 0.dp),
            ) {
                Text(
                    text = if (query.isBlank()) "$totalCount 轮" else "$resultCount / $totalCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = if (query.isNotBlank()) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.chat_page_cancel),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRoundCard(
    round: PreviewRound,
    query: String,
    assistant: Assistant?,
    model: Model?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrent) {
            colors.secondaryContainer.copy(alpha = 0.55f)
        } else {
            colors.surfaceContainer
        },
        border = if (isCurrent) BorderStroke(1.5.dp, colors.primary.copy(alpha = 0.75f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 用户消息标题行：轮次序号徽章 + 提问文本 + 固定/收藏标记 + 时间
            val user = round.user
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isCurrent) colors.primary else colors.surfaceContainerHighest,
                ) {
                    Text(
                        text = "#${round.roundIndex}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) colors.onPrimary else colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        user != null && round.userText.isNotEmpty() -> {
                            HighlightedSnippet(
                                text = round.userText,
                                query = query,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = colors.onSurface,
                            )
                        }
                        user != null -> {
                            PreviewAttachmentSummary(parts = user.parts)
                        }
                        else -> {
                            Text(
                                text = "开场白",
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (round.pinned) {
                    Icon(
                        imageVector = HugeIcons.Pin02,
                        contentDescription = "固定到上下文",
                        modifier = Modifier.size(14.dp),
                        tint = colors.tertiary,
                    )
                }
                if (round.favorite) {
                    Icon(
                        imageVector = HugeIcons.FavouriteCircle,
                        contentDescription = "已收藏",
                        modifier = Modifier.size(14.dp),
                        tint = colors.primary,
                    )
                }
                if (user != null) {
                    Text(
                        text = remember(user.createdAt) {
                            user.createdAt.toJavaLocalDateTime().toPreviewTimeString()
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }

            // 助手回复：置于柔和内层容器中，前面带模型/助手头像区分角色
            if (round.reply != null && (round.replyText.isNotEmpty() || round.replyParts.hasAttachmentSummary())) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = if (isCurrent) {
                        colors.surface.copy(alpha = 0.72f)
                    } else {
                        colors.surfaceContainerHigh.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PreviewReplyAvatar(assistant = assistant, model = model)
                        Box(modifier = Modifier.weight(1f)) {
                            if (round.replyText.isNotEmpty()) {
                                HighlightedSnippet(
                                    text = round.replyText,
                                    query = query,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            } else {
                                PreviewAttachmentSummary(parts = round.replyParts)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 压缩检查点：用色调容器和图标和普通对话区分开，一眼能看出上下文从哪里开始被摘要替代。
 */
@Composable
private fun PreviewCheckpointCard(
    round: PreviewRound,
    query: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        border = if (isCurrent) BorderStroke(1.5.dp, colors.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Package01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.chat_page_compress_checkpoint_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                round.user?.let { user ->
                    Text(
                        text = remember(user.createdAt) {
                            user.createdAt.toJavaLocalDateTime().toPreviewTimeString()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            if (round.userText.isNotEmpty()) {
                HighlightedSnippet(
                    text = round.userText,
                    query = query,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HighlightedSnippet(
    text: String,
    query: String,
    maxLines: Int,
    style: TextStyle,
    color: Color,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated = remember(text, query, highlightColor, highlightTextColor) {
        buildHighlightedText(
            // Text 会把整段文字都排版一遍才截到 maxLines，长回复必须先截短
            text = extractMatchingSnippet(text, query).take(SnippetMaxLength),
            query = query,
            highlightColor = highlightColor,
            highlightTextColor = highlightTextColor,
        )
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreviewReplyAvatar(
    assistant: Assistant?,
    model: Model?,
) {
    // 和两行正文的首行对齐
    val avatarModifier = Modifier
        .padding(top = 1.dp)
        .size(18.dp)
    when {
        assistant?.useAssistantAvatar == true -> UIAvatar(
            name = assistant.name,
            value = assistant.avatar,
            modifier = avatarModifier,
        )

        model != null -> AutoAIIcon(name = model.modelId, modifier = avatarModifier)
    }
}

/**
 * 图片、文件、工具调用没有文字，用图标 + 数量概括，避免整条只剩空白。
 */
@Composable
private fun PreviewAttachmentSummary(parts: List<UIMessagePart>) {
    val images = parts.count { it is UIMessagePart.Image }
    val files = parts.count { it is UIMessagePart.Document || it is UIMessagePart.Video || it is UIMessagePart.Audio }
    val tools = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Tool>().map { it.toolName }.distinct()
    }
    if (images == 0 && files == 0 && tools.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (images > 0) AttachmentChip(icon = HugeIcons.Image01, label = images.toString())
        if (files > 0) AttachmentChip(icon = HugeIcons.Attachment01, label = files.toString())
        if (tools.isNotEmpty()) {
            AttachmentChip(
                icon = HugeIcons.Wrench01,
                label = tools.joinToString(", "),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private fun List<UIMessagePart>.hasAttachmentSummary(): Boolean = any {
    it is UIMessagePart.Image || it is UIMessagePart.Document || it is UIMessagePart.Video ||
        it is UIMessagePart.Audio || it is UIMessagePart.Tool
}

@Composable
private fun AttachmentChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// 概览里空间有限：当天只显示时间，其他日子用短日期 + 时间，不带秒
private fun LocalDateTime.toPreviewTimeString(): String {
    val style = if (toLocalDate() == LocalDate.now()) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    }
    return style.withLocale(Locale.getDefault()).format(this)
}

private val CodeFenceRegex = Regex("```[\\w-]*")
private val HeadingRegex = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
private val QuoteRegex = Regex("(?m)^\\s{0,3}>\\s?")
private val EmphasisRegex = Regex("\\*\\*|__|~~")
private val WhitespaceRegex = Regex("\\s+")

private fun UIMessage.toPreviewText(): String = toText()
    .replace(CodeFenceRegex, " ")
    .replace(HeadingRegex, "")
    .replace(QuoteRegex, "")
    .replace(EmphasisRegex, "")
    .replace(WhitespaceRegex, " ")
    .trim()

/**
 * 截取包含搜索词的片段：匹配词前保留一小段上下文，保证三行内能看到匹配位置。
 */
private fun extractMatchingSnippet(text: String, query: String): String {
    if (query.isBlank()) return text
    val matchIndex = text.indexOf(query, ignoreCase = true)
    if (matchIndex <= SnippetContextLength) return text
    // 尽量从词边界开始截，免得开头是半个单词
    val start = text.indexOf(' ', matchIndex - SnippetContextLength).let { space ->
        if (space in 0 until matchIndex) space + 1 else matchIndex - SnippetContextLength
    }
    return "…" + text.substring(start)
}

private const val SnippetContextLength = 24

// 卡片最多显示两行，留足宽屏和小字号的余量
private const val SnippetMaxLength = 300

private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color,
    highlightTextColor: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var startIndex = 0
        var index = text.indexOf(query, startIndex, ignoreCase = true)
        while (index >= 0) {
            append(text.substring(startIndex, index))
            withStyle(
                SpanStyle(
                    background = highlightColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                append(text.substring(index, index + query.length))
            }
            startIndex = index + query.length
            index = text.indexOf(query, startIndex, ignoreCase = true)
        }
        if (startIndex < text.length) append(text.substring(startIndex))
    }
}
