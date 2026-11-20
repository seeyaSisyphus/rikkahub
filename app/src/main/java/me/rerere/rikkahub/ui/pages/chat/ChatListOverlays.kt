package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowDownDouble
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowUpDouble
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation

// 消息列表上浮着的小组件：底部的建议回复、侧边的快速跳转

@Composable
internal fun ChatSuggestionsRow(
    modifier: Modifier = Modifier,
    conversation: Conversation,
    onClickSuggestion: (String) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(conversation.chatSuggestions) { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        onClickSuggestion(suggestion)
                    }
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.MessageJumper(
    show: Boolean,
    onLeft: Boolean,
    scope: CoroutineScope,
    state: LazyListState,
    // 提问消息的下标，上下按钮在提问之间跳转
    userMessageIndices: List<Int> = emptyList(),
    onDismissJumper: () -> Unit = {},
    onDisableAutoFollow: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
        enter = slideInHorizontally(
            initialOffsetX = { if (onLeft) -it * 2 else it * 2 },
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { if (onLeft) -it * 2 else it * 2 },
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 去顶部（点击后自动关闭导航栏）
            Surface(
                onClick = {
                    scope.launch {
                        state.scrollToItem(0)
                    }
                    onDismissJumper()
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowUpDouble,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            // 上一个提问（不关闭导航栏）
            Surface(
                onClick = {
                    // 手动跳转时停止自动贴底，防止被 auto-follow 立即拉回底部
                    onDisableAutoFollow()
                    scope.launch {
                        val current = state.firstVisibleItemIndex
                        // 找小于当前位置的最大 USER index
                        val target = userMessageIndices.lastOrNull { it < current }
                            ?: userMessageIndices.firstOrNull()
                            ?: (current - 1).fastCoerceAtLeast(0)
                        state.animateScrollToItem(target)
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowUp01,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            // 下一个提问（不关闭导航栏）
            Surface(
                onClick = {
                    // 手动跳转时停止自动贴底，防止被 auto-follow 立即拉回底部
                    onDisableAutoFollow()
                    scope.launch {
                        val current = state.firstVisibleItemIndex
                        // 找大于当前位置的最小 USER index
                        val nextUserIndex = userMessageIndices.firstOrNull { it > current }
                        if (nextUserIndex != null) {
                            // 还有更靠下的提问，跳过去
                            state.animateScrollToItem(nextUserIndex)
                        } else {
                            // 已经跨过最后一条提问，直接滚到底部
                            state.animateScrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                        }
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            // 去底部（点击后自动关闭导航栏）
            Surface(
                onClick = {
                    scope.launch {
                        state.scrollToItem(state.layoutInfo.totalItemsCount - 1)
                    }
                    onDismissJumper()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f),
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowDownDouble,
                    contentDescription = stringResource(R.string.chat_page_scroll_to_bottom),
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
        }
    }
}
