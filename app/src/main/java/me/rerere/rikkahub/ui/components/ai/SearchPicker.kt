package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.supportsBuiltInSearch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiSearch02
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.SearchRemove
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.SearchAbilityTagLine

enum class SearchMode {
    OFF,
    LOCAL,
    BUILT_IN,
}

private val modeShapes = SearchMode.entries.map { it.shape() }

internal fun searchModeOf(enableSearch: Boolean, model: Model?): SearchMode = when {
    // 模型已开启内置搜索（可能是不支持的模型残留的孤儿状态）
    model?.tools?.contains(BuiltInTools.Search) == true -> SearchMode.BUILT_IN
    enableSearch -> SearchMode.LOCAL
    else -> SearchMode.OFF
}

// 加号 sheet 里的联网搜索入口
@Composable
internal fun SearchPickerListItem(
    enableSearch: Boolean,
    settings: Settings,
    model: Model?,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val mode = searchModeOf(enableSearch, model)
    val currentService = settings.searchServices.getOrNull(settings.searchServiceSelected)

    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = {
            if (mode == SearchMode.LOCAL && currentService != null) {
                AutoAIIcon(
                    name = currentService.displayName,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                val active = mode != SearchMode.OFF
                CardGroupIcon(
                    icon = mode.icon(),
                    containerColor = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
        },
        supportingContent = {
            Text(
                text = when (mode) {
                    SearchMode.OFF -> stringResource(R.string.search_picker_status_off)
                    SearchMode.LOCAL -> currentService?.displayName
                        ?: stringResource(R.string.search_picker_local_title)

                    SearchMode.BUILT_IN -> stringResource(R.string.search_picker_model_title)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
    ) {
        Text(
            text = stringResource(R.string.use_web_search),
            fontWeight = FontWeight.Medium,
        )
    }
}

// 联网搜索页，作为子页面嵌在加号 sheet 里
@Composable
internal fun SearchPickerPage(
    enableSearch: Boolean,
    settings: Settings,
    model: Model?,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onSelectProvider: () -> Unit,
    onBack: () -> Unit,
    onDismissAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.use_web_search),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.back))
                }
            },
        )
        SearchPicker(
            enableSearch = enableSearch,
            settings = settings,
            model = model,
            onUpdateSearchMode = onUpdateSearchMode,
            onSelectProvider = onSelectProvider,
            onDismiss = onDismissAll,
        )
    }
}

@Composable
private fun SearchPicker(
    enableSearch: Boolean,
    settings: Settings,
    model: Model?,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onSelectProvider: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navBackStack = LocalNavController.current

    val provider = model?.findProvider(settings.providers)
    val supportsBuiltInSearch = provider?.supportsBuiltInSearch == true
    val currentMode = searchModeOf(enableSearch, model)
    // 模型支持内置搜索，或已开启内置搜索（后者保证残留状态也能被关闭）时显示模型搜索选项
    val showModelSearch = model != null && (supportsBuiltInSearch || currentMode == SearchMode.BUILT_IN)
    val modes = buildList {
        add(SearchMode.OFF)
        add(SearchMode.LOCAL)
        if (showModelSearch) add(SearchMode.BUILT_IN)
    }

    val currentService = settings.searchServices.getOrNull(settings.searchServiceSelected)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        val enabled = currentMode != SearchMode.OFF
        // 子页面头部已经有标题，这里不再重复
        PickerValueHeader(
            title = null,
            value = currentMode,
            hint = when (currentMode) {
                SearchMode.OFF -> stringResource(R.string.search_picker_off_description)
                SearchMode.LOCAL -> stringResource(R.string.search_picker_local_description)
                SearchMode.BUILT_IN -> stringResource(R.string.search_picker_model_description)
            },
            modifier = Modifier.padding(bottom = 16.dp),
            label = {
                when (it) {
                    SearchMode.OFF -> stringResource(R.string.search_picker_status_off)
                    SearchMode.LOCAL -> stringResource(R.string.search_picker_local_title)
                    SearchMode.BUILT_IN -> stringResource(R.string.search_picker_model_title)
                }
            },
        ) {
            PickerHero(
                shapes = modeShapes,
                index = currentMode.ordinal,
                icon = currentMode.icon(),
                containerColor = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // 滑动胶囊选择器
        ConnectedChoiceButtons(
            options = modes,
            selected = currentMode,
            onSelect = onUpdateSearchMode,
            minHeight = 46.dp,
        ) { mode, selected ->
            ConnectedChoiceLabel(
                text = when (mode) {
                    SearchMode.OFF -> stringResource(R.string.search_picker_off)
                    SearchMode.LOCAL -> stringResource(R.string.search_picker_local_title)
                    SearchMode.BUILT_IN -> stringResource(R.string.search_picker_model_title)
                },
                selected = selected,
                icon = mode.icon(),
            )
        }

        AnimatedVisibility(
            visible = currentMode == SearchMode.LOCAL,
            enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            SegmentedListItem(
                onClick = onSelectProvider,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                modifier = Modifier.padding(top = 12.dp),
                leadingContent = {
                    if (currentService != null) {
                        AutoAIIcon(
                            name = currentService.displayName,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(HugeIcons.GlobalSearch, contentDescription = null)
                    }
                },
                supportingContent = currentService?.let {
                    { SearchAbilityTagLine(options = it) }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 搜索设置只和本地搜索的服务商有关，跟着这一行一起出现
                        IconButton(
                            onClick = {
                                onDismiss()
                                navBackStack.navigate(Screen.SettingSearch)
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Settings03,
                                contentDescription = stringResource(R.string.search_picker_title),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                },
            ) {
                Text(
                    text = currentService?.displayName
                        ?: stringResource(R.string.search_picker_select_provider)
                )
            }
        }
    }
}

private fun SearchMode.icon(): ImageVector = when (this) {
    SearchMode.OFF -> HugeIcons.SearchRemove
    SearchMode.LOCAL -> HugeIcons.GlobalSearch
    SearchMode.BUILT_IN -> HugeIcons.AiSearch02
}

private fun SearchMode.shape(): RoundedPolygon = when (this) {
    SearchMode.OFF -> MaterialShapes.Circle
    SearchMode.LOCAL -> MaterialShapes.Cookie6Sided
    SearchMode.BUILT_IN -> MaterialShapes.Cookie9Sided
}

@Composable
internal fun SearchProviderPicker(
    settings: Settings,
    onUpdateSearchService: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val services = settings.searchServices
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(horizontal = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.search_picker_select_provider),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.back))
                }
            },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(services) { index, service ->
                val selected = settings.searchServiceSelected == index
                SegmentedListItem(
                    selected = selected,
                    onClick = { onUpdateSearchService(index) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = services.size),
                    leadingContent = {
                        AutoAIIcon(
                            name = service.displayName,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    supportingContent = {
                        SearchAbilityTagLine(options = service)
                    },
                    trailingContent = {
                        RadioButton(selected = selected, onClick = null)
                    },
                ) {
                    Text(service.displayName)
                }
            }
        }
    }
}
