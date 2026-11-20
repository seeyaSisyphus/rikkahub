package me.rerere.rikkahub.ui.pages.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.SwipeToDelete
import me.rerere.rikkahub.ui.components.ui.rememberAutoFocusRequester
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FavoritePage(vm: FavoriteVM = koinViewModel()) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val favorites = vm.nodeFavorites.collectAsStateWithLifecycle().value

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 只在"用户点开搜索"时聚焦；搜索条因其他原因重新进入组合（返回页面等）不应弹键盘
    var searchFocusPending by remember { mutableStateOf(false) }

    // 搜索态下返回键先收起搜索，而不是直接退出页面
    BackHandler(enabled = isSearchVisible) {
        isSearchVisible = false
        searchQuery = ""
        searchFocusPending = false
    }

    val removedMessage = stringResource(R.string.favorite_page_removed)
    val undoLabel = stringResource(R.string.history_page_undo)

    val filteredFavorites = remember(favorites, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            favorites
        } else {
            favorites.filter { item ->
                item.conversationTitle.contains(q, ignoreCase = true) ||
                    item.questionPreview?.contains(q, ignoreCase = true) == true ||
                    item.preview.contains(q, ignoreCase = true)
            }
        }
    }

    val groupedFavorites = remember(filteredFavorites) {
        val zone = ZoneId.systemDefault()
        filteredFavorites.groupBy { item ->
            YearMonth.from(Instant.ofEpochMilli(item.createdAt).atZone(zone))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.favorite_page_title))
                        if (favorites.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = "${favorites.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (favorites.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (isSearchVisible) {
                                    searchFocusPending = true
                                } else {
                                    searchQuery = ""
                                    searchFocusPending = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) HugeIcons.Cancel01 else HugeIcons.Search01,
                                contentDescription = stringResource(
                                    if (isSearchVisible) R.string.history_page_cancel
                                    else R.string.history_page_search
                                ),
                            )
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.InLove,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                    Text(
                        text = stringResource(R.string.favorite_page_no_favorites),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                FavoriteSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    autoFocus = searchFocusPending,
                    onAutoFocusHandled = { searchFocusPending = false },
                    modifier = Modifier
                        .padding(
                            top = innerPadding.calculateTopPadding() + 4.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 8.dp,
                        ),
                )
            }

            // Column 的 imePadding 已经包含导航栏区域，键盘弹出时不要再叠加一份底部 inset
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            val bottomInset = if (imeVisible) 0.dp else innerPadding.calculateBottomPadding()
            val listTopPadding = if (isSearchVisible) {
                PaddingValues(horizontal = 16.dp, vertical = 4.dp) +
                    PaddingValues(bottom = bottomInset)
            } else {
                innerPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            }

            if (filteredFavorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomInset),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "没有匹配的收藏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                var activeSwipeId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress && activeSwipeId != null) {
                        activeSwipeId = null
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = listTopPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    groupedFavorites.entries.forEachIndexed { groupIndex, (yearMonth, groupItems) ->
                        item(key = "month:$yearMonth") {
                            FavoriteMonthHeader(
                                yearMonth = yearMonth,
                                modifier = Modifier.padding(
                                    top = if (groupIndex == 0) 2.dp else 10.dp,
                                    start = 4.dp,
                                    end = 4.dp,
                                ),
                            )
                        }
                        items(groupItems, key = { it.id }) { item ->
                            SwipeableFavoriteCard(
                                item = item,
                                expanded = activeSwipeId == item.id,
                                onExpandChange = { open ->
                                    activeSwipeId = when {
                                        open -> item.id
                                        activeSwipeId == item.id -> null
                                        else -> activeSwipeId
                                    }
                                },
                                onClick = { navigateToChatPage(navController, item.conversationId, nodeId = item.nodeId) },
                                onDelete = {
                                    if (activeSwipeId == item.id) {
                                        activeSwipeId = null
                                    }
                                    scope.launch {
                                        val backup = vm.getEntityByRefKey(item.refKey)
                                        vm.removeFavorite(item.refKey)
                                        if (backup != null) {
                                            val result = snackbarHostState.showSnackbar(
                                                message = removedMessage,
                                                actionLabel = undoLabel,
                                                withDismissAction = true,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                vm.restoreFavorite(backup)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    autoFocus: Boolean,
    onAutoFocusHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 搜索条由按钮点开才出现，必须显式聚焦，否则输入法不会弹出
    val focusRequester = rememberAutoFocusRequester(
        requestKey = autoFocus.takeIf { it },
        onRequestHandled = onAutoFocusHandled,
    )
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            // 整条胶囊都可点聚焦，且不产生 clickable 的语义合并（避免吞掉输入框/清除按钮的无障碍节点）
            .pointerInput(Unit) {
                detectTapGestures {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
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
                        text = stringResource(R.string.history_page_search),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteMonthHeader(
    yearMonth: YearMonth,
    modifier: Modifier = Modifier,
) {
    val currentYear = remember { LocalDate.now().year }
    val label = remember(yearMonth, currentYear) {
        if (yearMonth.year == currentYear) {
            yearMonth.format(FavoriteSameYearMonthFormatter)
        } else {
            yearMonth.format(FavoriteOtherYearMonthFormatter)
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        modifier = modifier,
    )
}

@Composable
private fun SwipeableFavoriteCard(
    item: NodeFavoriteListItem,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDelete(
        onDelete = onDelete,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        actionLabel = "移除",
        releaseLabel = "松手移除",
        expanded = expanded,
        onExpandChange = onExpandChange,
    ) {
        FavoriteCard(
            item = item,
            onClick = onClick,
        )
    }
}

@Composable
private fun FavoriteCard(
    item: NodeFavoriteListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = LocalDarkMode.current
    val cardColor = if (isDark) colors.surfaceContainerHigh else colors.surfaceBright
    val chipColor = if (isDark) colors.surfaceContainer else colors.surfaceContainerLow
    val cleanQuestion = remember(item.questionPreview) {
        item.questionPreview?.let(::cleanFavoritePreview)?.takeIf { it.isNotEmpty() }
    }
    val cleanAnswer = remember(item.preview) {
        cleanFavoritePreview(item.preview)
    }
    val dateText = remember(item.createdAt) {
        formatFavoriteDate(item.createdAt)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        contentColor = colors.onSurface,
        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶栏元数据：左侧轻量会话胶囊，右侧日期与跳转箭头
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipColor,
                        contentColor = colors.onSurfaceVariant,
                    ) {
                        Text(
                            text = item.conversationTitle.ifBlank {
                                stringResource(R.string.favorite_page_untitled_conversation)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = dateText,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }

            // 有提问时以单行提问作主题锚点，回答退为 2 行轻量摘要，减少视觉拥挤
            if (cleanQuestion != null) {
                Text(
                    text = cleanQuestion,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
            }

            if (cleanAnswer.isNotEmpty()) {
                Text(
                    text = cleanAnswer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = if (cleanQuestion != null) {
                        colors.onSurfaceVariant.copy(alpha = 0.85f)
                    } else {
                        colors.onSurface.copy(alpha = 0.88f)
                    },
                )
            }
        }
    }
}

private val FavCodeFenceRegex = Regex("```[\\w-]*")
private val FavHeadingRegex = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
private val FavQuoteRegex = Regex("(?m)^\\s{0,3}>\\s?")
private val FavEmphasisRegex = Regex("\\*\\*|__|~~|`")
private val FavWhitespaceRegex = Regex("\\s+")

private fun cleanFavoritePreview(raw: String): String = raw
    .replace(FavCodeFenceRegex, " ")
    .replace(FavHeadingRegex, "")
    .replace(FavQuoteRegex, "")
    .replace(FavEmphasisRegex, "")
    .replace(FavWhitespaceRegex, " ")
    .trim()

private val FavoriteSameYearFormatter = DateTimeFormatter.ofPattern("MM/dd")
private val FavoriteOtherYearFormatter = DateTimeFormatter.ofPattern("yy/MM/dd")
private val FavoriteSameYearMonthFormatter = DateTimeFormatter.ofPattern("MMM")
private val FavoriteOtherYearMonthFormatter = DateTimeFormatter.ofPattern("yyyy · MMM")

private fun formatFavoriteDate(epochMillis: Long): String {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    val formatter = if (dateTime.year == LocalDate.now().year) {
        FavoriteSameYearFormatter
    } else {
        FavoriteOtherYearFormatter
    }
    return dateTime.format(formatter)
}
