package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal interface PagerTab {
    val icon: ImageVector
    val label: String
        @Composable get
}

// 分页切换：可横向滚动的滑动胶囊选项卡，胶囊位置与 Pager 滚动进度实时同步
@Composable
internal fun PagerTabs(
    pagerState: PagerState,
    tabs: List<PagerTab>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val trackColor = segmentedTrackColor()
    val pillColor = segmentedPillColor()
    val selectedIndex = pagerState.targetPage
    val requesters = remember(tabs) { tabs.map { BringIntoViewRequester() } }
    val tabBounds = remember(tabs.size) { mutableStateMapOf<Int, Pair<Int, Int>>() }

    // 跟随 Pager 当前进度实时插值（滑动翻页贴手同步，点击切换与内容零延迟同步到位）
    val lastIndex = (tabs.size - 1).coerceAtLeast(0)
    val pagePosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, lastIndex.toFloat())
    val lowerIndex = floor(pagePosition).toInt().coerceIn(0, lastIndex)
    val upperIndex = ceil(pagePosition).toInt().coerceIn(0, lastIndex)
    val fraction = pagePosition - lowerIndex
    val lowerBounds = tabBounds[lowerIndex]
    val upperBounds = tabBounds[upperIndex] ?: lowerBounds
    val interpolatedLeft = if (lowerBounds != null && upperBounds != null) {
        lerp(lowerBounds.first.toFloat(), upperBounds.first.toFloat(), fraction).roundToInt()
    } else null
    val interpolatedWidth = if (lowerBounds != null && upperBounds != null) {
        lerp(lowerBounds.second.toFloat(), upperBounds.second.toFloat(), fraction).roundToInt()
    } else null

    // 滑动翻页后，把选中的按钮滚进可视范围
    LaunchedEffect(selectedIndex) {
        requesters[selectedIndex].bringIntoView()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Surface(
            shape = CircleShape,
            color = trackColor,
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .height(IntrinsicSize.Min),
            ) {
                if (interpolatedLeft != null && interpolatedWidth != null && interpolatedWidth > 0) {
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(interpolatedLeft, 0) }
                            .width(with(density) { interpolatedWidth.toDp() })
                            .fillMaxHeight(),
                        shape = CircleShape,
                        color = pillColor,
                        shadowElevation = 2.dp,
                        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f)),
                        content = {},
                    )
                }

                Row(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.fastForEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val contentColor = if (selected) {
                            colors.onSurface
                        } else {
                            colors.onSurfaceVariant.copy(alpha = 0.85f)
                        }
                        val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium

                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .onGloballyPositioned { coords ->
                                    val bounds = coords.positionInParent().x.roundToInt() to coords.size.width
                                    if (tabBounds[index] != bounds) {
                                        tabBounds[index] = bounds
                                    }
                                }
                                .bringIntoViewRequester(requesters[index])
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!selected) scope.launch { pagerState.animateScrollToPage(index) }
                                }
                                .semantics { role = Role.Tab }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                ProvideTextStyle(
                                    MaterialTheme.typography.labelLarge.copy(fontWeight = fontWeight),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selected) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                                        }
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
