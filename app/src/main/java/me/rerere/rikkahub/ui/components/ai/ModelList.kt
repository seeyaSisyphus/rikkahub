package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.filterByKeywords
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DragDropHorizontal
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceString
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.toDp
import me.rerere.ui.icons.HeartIcon
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.rikkahub.ui.components.ui.AppModalBottomSheet

class ModelListState internal constructor(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
) {
    var modelId by mutableStateOf(modelId)
        private set

    var providers by mutableStateOf(providers)
        private set

    var type by mutableStateOf(type)
        private set

    var visible by mutableStateOf(false)
        private set

    val currentModel: Model?
        get() = modelId?.let { providers.findModelById(it) }

    val filteredProviders: List<ProviderSetting>
        get() = providers.fastFilter { provider ->
            provider.enabled && provider.models.fastAny { model -> model.type == type }
        }

    fun open() {
        visible = true
    }

    fun close() {
        visible = false
    }

    internal fun update(
        modelId: Uuid?,
        providers: List<ProviderSetting>,
        type: ModelType,
    ) {
        this.modelId = modelId
        this.providers = providers
        this.type = type
    }
}

@Composable
fun rememberModelListState(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
): ModelListState {
    return remember {
        ModelListState(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }.also {
        it.update(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }
}

@Composable
fun ModelSelector(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onSelect: (Model) -> Unit
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = type,
    )

    ModelSelectorButton(
        state = state,
        modifier = modifier,
        onlyIcon = onlyIcon,
        allowClear = allowClear,
        onClear = { onSelect(Model()) },
    )

    ModelListSheet(
        state = state,
        onSelect = onSelect,
    )
}

@Composable
internal fun ModelSelectorButton(
    state: ModelListState,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onClear: () -> Unit = {},
) {
    val model = state.currentModel

    if (!onlyIcon) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    state.open()
                },
                modifier = modifier
            ) {
                model?.modelId?.let {
                    AutoAIIcon(
                        it, Modifier
                            .padding(end = 4.dp)
                            .size(36.dp),
                        color = Color.Transparent
                    )
                }
                Text(
                    text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (allowClear && model != null) {
                IconButton(
                    onClick = onClear,
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = "Clear"
                    )
                }
            }
        }
    } else {
        IconButton(
            onClick = {
                state.open()
            },
            modifier = modifier.size(36.dp),
        ) {
            if (model != null) {
                AutoAIIcon(
                    modifier = Modifier.size(28.dp),
                    name = model.modelId,
                    color = Color.Transparent
                )
            } else {
                Icon(
                    imageVector = HugeIcons.Brain02,
                    contentDescription = stringResource(R.string.setting_model_page_chat_model),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 显示当前模型的胶囊按钮，样式和 Select 保持一致，点击后由调用方打开模型列表
// 给了 onClear 时，选了模型后右侧显示清除按钮；placeholder 是没选模型时显示的文字
@Composable
internal fun ModelField(
    model: Model?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.model_list_select_model),
    onClear: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (model != null) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier.size(24.dp),
                    color = Color.Transparent,
                )
            }
            Text(
                text = model?.displayName ?: placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onClear != null && model != null) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = "Clear",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                )
            } else {
                Icon(
                    imageVector = HugeIcons.ArrowDown01,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
fun ModelListSheet(
    state: ModelListState,
    onSelect: (Model) -> Unit,
) {
    if (!state.visible) return

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    fun dismiss() {
        coroutineScope.launch {
            sheetState.hide()
            state.close()
        }
    }

    AppModalBottomSheet(
        onDismissRequest = {
            state.close()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModelList(
                currentModel = state.modelId,
                providers = state.filteredProviders,
                modelType = state.type,
                onSelect = {
                    onSelect(it)
                    dismiss()
                },
                onDismiss = {
                    dismiss()
                }
            )
        }
    }
}

private val MODEL_LIST_ITEM_SPACING = 8.dp
private const val INITIAL_CONTEXT_ITEMS_ABOVE = 2
private const val TARGET_VIEWPORT_CONTEXT_FRACTION = 0.42f

internal data class SelectedModelTarget(
    val itemIndex: Int,
    val headerKey: String? = null,
)

/**
 * 计算当前模型在打开列表（无搜索过滤）时对应的 LazyColumn 索引与所属分组标题 key。
 */
internal fun findSelectedModelTarget(
    currentModel: Uuid?,
    favoriteModelIds: List<Uuid>,
    providers: List<ProviderSetting>,
    typeFilteredModelsByProvider: Map<Uuid, List<Model>>,
    collapsedProviderIds: Set<String>,
): SelectedModelTarget? {
    if (currentModel == null) return null

    var position = if (providers.isEmpty()) 1 else 0

    val favoriteIndex = favoriteModelIds.indexOf(currentModel)
    if (favoriteIndex >= 0) {
        return SelectedModelTarget(
            itemIndex = position + 1 + favoriteIndex,
            headerKey = "header:favorite",
        )
    }

    if (favoriteModelIds.isNotEmpty()) {
        position += 1 // favorite header
        position += favoriteModelIds.size
    }

    for (provider in providers) {
        val headerIndex = position
        position += 1 // provider header
        val models = typeFilteredModelsByProvider[provider.id].orEmpty()
        val modelIndex = models.indexOfFirst { it.id == currentModel }
        val collapsed = provider.id.toString() in collapsedProviderIds
        if (modelIndex >= 0) {
            return if (collapsed) {
                SelectedModelTarget(
                    itemIndex = headerIndex,
                    headerKey = "header:${provider.id}",
                )
            } else {
                SelectedModelTarget(
                    itemIndex = headerIndex + 1 + modelIndex,
                    headerKey = "header:${provider.id}",
                )
            }
        }
        if (!collapsed) position += models.size
    }

    return null
}

/**
 * 判断列表位于顶部时，目标项是否已完整落在可视区域内（无需任何初始滚动）。
 */
internal fun isItemFullyVisibleInInitialViewport(
    itemOffset: Int,
    itemSize: Int,
    viewportEndOffset: Int,
    afterContentPadding: Int,
): Boolean {
    val visibleBottom = (viewportEndOffset - afterContentPadding).coerceAtLeast(0)
    return itemOffset >= 0 && itemOffset + itemSize <= visibleBottom
}

/**
 * 计算将目标项滚动到视口偏中上方位置所需的像素增量（为上方邻近模型/分组保留上下文，不顶格吸顶）。
 */
internal fun computeContextScrollDelta(
    targetOffset: Int,
    targetSize: Int,
    viewportEndOffset: Int,
    afterContentPadding: Int,
    headerClearancePx: Int = 0,
    contextFraction: Float = TARGET_VIEWPORT_CONTEXT_FRACTION,
): Float {
    val contentHeight = (viewportEndOffset - afterContentPadding).coerceAtLeast(targetSize)
    val availableSpace = (contentHeight - targetSize).coerceAtLeast(0)
    val safeMinTop = headerClearancePx.coerceIn(0, availableSpace)
    val desiredTop = (availableSpace * contextFraction).roundToInt().coerceAtLeast(safeMinTop)
    return (targetOffset - desiredTop).toFloat()
}

private fun computeHeaderClearancePx(
    visibleItems: List<LazyListItemInfo>,
    targetIndex: Int,
    headerKey: String?,
    itemSpacingPx: Int,
): Int {
    val headerInfo = headerKey?.let { key ->
        visibleItems.firstOrNull { it.key == key && it.index != targetIndex }
    } ?: visibleItems.firstOrNull {
        it.index < targetIndex && it.key.toString().startsWith("header:")
    } ?: return 0
    return headerInfo.size + itemSpacingPx
}

@Composable
private fun ColumnScope.ModelList(
    currentModel: Uuid? = null,
    providers: List<ProviderSetting>,
    modelType: ModelType,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val settings = settingsStore.settingsFlow
        .collectAsStateWithLifecycle()

    // 拖动排序时先改这份本地顺序，松手再写回设置。设置是异步回到界面的，拖到边缘自动滚动时拖拽库每帧都会按
    // 旧布局再算一次移动，直接改设置会让顺序被反复改回去，看起来就是来回抽搐
    var draggingFavoriteOrder by remember { mutableStateOf<List<Uuid>?>(null) }
    val favoriteModels = (draggingFavoriteOrder ?: settings.value.favoriteModels).mapNotNull { modelId ->
        val model = settings.value.providers.findModelById(modelId) ?: return@mapNotNull null
        if (model.type != modelType) return@mapNotNull null
        val provider = model.findProvider(providers = settings.value.providers, checkOverwrite = false) ?: return@mapNotNull null
        model to provider
    }

    var searchKeywords by remember { mutableStateOf("") }

    // 折叠的供应商（持久化），搜索时全部展开
    var collapsedProvidersPref by rememberSharedPreferenceString("model_list_collapsed_providers", "")
    val collapsedProviders = remember(collapsedProvidersPref) {
        collapsedProvidersPref.orEmpty().split(",").filter { it.isNotBlank() }.toSet()
    }
    fun isCollapsed(provider: ProviderSetting): Boolean =
        searchKeywords.isBlank() && provider.id.toString() in collapsedProviders

    fun toggleCollapsed(provider: ProviderSetting) {
        val id = provider.id.toString()
        collapsedProvidersPref = (if (id in collapsedProviders) collapsedProviders - id else collapsedProviders + id)
            .joinToString(",")
    }

    val typeFilteredModelsByProvider = remember(providers, modelType) {
        providers.associate { provider ->
            provider.id to provider.models.fastFilter { it.type == modelType }
        }
    }

    val searchFilteredModelsByProvider = remember(typeFilteredModelsByProvider, searchKeywords) {
        typeFilteredModelsByProvider.mapValues { (_, models) -> models.filterByKeywords(searchKeywords) }
    }

    val visibleModelsByProvider = remember(providers, searchFilteredModelsByProvider, collapsedProviders, searchKeywords) {
        providers.associate { provider ->
            provider.id to if (isCollapsed(provider)) emptyList() else searchFilteredModelsByProvider[provider.id].orEmpty()
        }
    }

    val selectedTarget = remember(
        currentModel,
        favoriteModels,
        providers,
        typeFilteredModelsByProvider,
        collapsedProviders,
    ) {
        findSelectedModelTarget(
            currentModel = currentModel,
            favoriteModelIds = favoriteModels.map { it.first.id },
            providers = providers,
            typeFilteredModelsByProvider = typeFilteredModelsByProvider,
            collapsedProviderIds = collapsedProviders,
        )
    }

    val density = LocalDensity.current
    val itemSpacingPx = remember(density) { with(density) { MODEL_LIST_ITEM_SPACING.roundToPx() } }
    val lazyListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val target = selectedTarget ?: return@LaunchedEffect
        val initialLayout = snapshotFlow { lazyListState.layoutInfo }
            .filter { it.visibleItemsInfo.isNotEmpty() }
            .first()

        val initialItem = initialLayout.visibleItemsInfo.firstOrNull { it.index == target.itemIndex }
        if (initialItem != null && isItemFullyVisibleInInitialViewport(
                itemOffset = initialItem.offset,
                itemSize = initialItem.size,
                viewportEndOffset = initialLayout.viewportEndOffset,
                afterContentPadding = initialLayout.afterContentPadding,
            )
        ) {
            return@LaunchedEffect
        }

        // 目标不在首屏时先跳到上方保留上下文的位置，并在同一帧内完成二次测量与微调
        if (initialItem == null) {
            lazyListState.scrollToItem((target.itemIndex - INITIAL_CONTEXT_ITEMS_ABOVE).coerceAtLeast(0))
            if (lazyListState.layoutInfo.visibleItemsInfo.none { it.index == target.itemIndex }) {
                lazyListState.scrollToItem((target.itemIndex - 1).coerceAtLeast(0))
            }
            if (lazyListState.layoutInfo.visibleItemsInfo.none { it.index == target.itemIndex }) {
                lazyListState.scrollToItem(target.itemIndex)
            }
        }

        val layoutInfo = lazyListState.layoutInfo
        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.itemIndex }
            ?: return@LaunchedEffect
        val delta = computeContextScrollDelta(
            targetOffset = targetItem.offset,
            targetSize = targetItem.size,
            viewportEndOffset = layoutInfo.viewportEndOffset,
            afterContentPadding = layoutInfo.afterContentPadding,
            headerClearancePx = computeHeaderClearancePx(
                visibleItems = layoutInfo.visibleItemsInfo,
                targetIndex = target.itemIndex,
                headerKey = target.headerKey,
                itemSpacingPx = itemSpacingPx,
            ),
        )
        if (delta != 0f) {
            lazyListState.scrollBy(delta)
        }
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 列表里只显示当前类型的收藏，下标和完整的收藏列表对不上，按 key 找到对应的模型再移动
        val ids = (draggingFavoriteOrder ?: settings.value.favoriteModels).toMutableList()
        val fromPos = ids.indexOfFirst { "favorite:$it" == from.key }
        val toPos = ids.indexOfFirst { "favorite:$it" == to.key }
        if (fromPos >= 0 && toPos >= 0) {
            ids.add(toPos, ids.removeAt(fromPos))
            draggingFavoriteOrder = ids
        }
    }
    val haptic = LocalHapticFeedback.current

    val providerPositions = remember(providers, favoriteModels, visibleModelsByProvider) {
        var currentIndex = 0
        if (providers.isEmpty()) {
            currentIndex = 1 // no providers item
        }
        if (favoriteModels.isNotEmpty()) {
            currentIndex += 1 // favorite header
            currentIndex += favoriteModels.size // favorite models
        }

        providers.map { provider ->
            val position = currentIndex
            currentIndex += 1 // provider header
            currentIndex += visibleModelsByProvider[provider.id].orEmpty().size
            provider.id to position
        }.toMap()
    }

    ModelSearchBar(
        query = searchKeywords,
        onQueryChange = { searchKeywords = it },
    )

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(MODEL_LIST_ITEM_SPACING),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        if (providers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.model_list_no_providers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendColors.gray6,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (favoriteModels.isNotEmpty()) {
            stickyHeader(key = "header:favorite") {
                ModelFavoriteSectionHeader()
            }

            items(
                items = favoriteModels,
                key = { "favorite:" + it.first.id.toString() }
            ) { (model, provider) ->
                ReorderableItem(
                    state = reorderableState,
                    key = "favorite:" + model.id.toString()
                ) { isDragging ->
                    ModelItem(
                        model = model,
                        onSelect = onSelect,
                        modifier = Modifier
                            .scale(if (isDragging) 0.96f else 1f)
                            .animateItem(),
                        providerSetting = provider,
                        select = model.id == currentModel,
                        onDismiss = {
                            onDismiss()
                        },
                        tail = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.update { settings ->
                                            settings.copy(
                                                favoriteModels = settings.favoriteModels.filter { it != model.id }
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    HeartIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        dragHandle = {
                            Icon(
                                imageVector = HugeIcons.DragDropHorizontal,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            val order = draggingFavoriteOrder ?: return@longPressDraggableHandle
                                            coroutineScope.launch {
                                                settingsStore.update { it.copy(favoriteModels = order) }
                                                // 写盘时界面已经收到新设置，这时再撤掉本地顺序不会闪回旧顺序。
                                                // 写盘期间可能已经开始下一次拖动，那时的本地顺序要留给下一次松手保存
                                                if (draggingFavoriteOrder === order) draggingFavoriteOrder = null
                                            }
                                        }
                                    ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    )
                }
            }
        }

        providers.fastForEach { providerSetting ->
            val collapsed = isCollapsed(providerSetting)
            stickyHeader(key = "header:${providerSetting.id}") {
                ModelProviderSectionHeader(
                    providerSetting = providerSetting,
                    collapsible = true,
                    collapsed = collapsed,
                    onToggleCollapse = { toggleCollapsed(providerSetting) },
                )
            }

            items(
                items = visibleModelsByProvider[providerSetting.id].orEmpty(),
                key = { it.id }
            ) { model ->
                val favorite = settings.value.favoriteModels.contains(model.id)
                ModelItem(
                    model = model,
                    onSelect = onSelect,
                    modifier = Modifier.animateItem(),
                    providerSetting = providerSetting,
                    select = currentModel == model.id,
                    onDismiss = {
                        onDismiss()
                    },
                    tail = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    // 按最新设置判断是否已收藏，连点时界面上的 favorite 还没刷新，会重复追加
                                    settingsStore.update { settings ->
                                        if (model.id in settings.favoriteModels) {
                                            settings.copy(
                                                favoriteModels = settings.favoriteModels.filter { it != model.id }
                                            )
                                        } else {
                                            settings.copy(
                                                favoriteModels = settings.favoriteModels + model.id
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp),
                        ) {
                            if (favorite) {
                                Icon(
                                    HeartIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    imageVector = HugeIcons.Favourite,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    ProviderQuickJumpBar(
        providers = providers,
        lazyListState = lazyListState,
        providerPositions = providerPositions,
        onJumpTo = { position ->
            coroutineScope.launch {
                lazyListState.animateScrollToItem(position)
            }
        },
    )
}

@Composable
private fun ModelSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            // 整条胶囊都可点聚焦，且不产生 clickable 的语义合并
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
                        text = stringResource(R.string.model_list_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.75f),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.chat_page_cancel),
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelFavoriteSectionHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = HeartIcon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.model_list_favorite),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ModelProviderSectionHeader(
    providerSetting: ProviderSetting,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (onToggleCollapse != null) {
                        Modifier.clickable(onClick = onToggleCollapse)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (collapsible) {
                Icon(
                    imageVector = if (collapsed) HugeIcons.ArrowRight01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
            Text(
                text = providerSetting.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            ProviderQuotaText(
                providerSetting = providerSetting,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderQuickJumpBar(
    providers: List<ProviderSetting>,
    lazyListState: LazyListState,
    providerPositions: Map<Uuid, Int>,
    onJumpTo: (Int) -> Unit,
) {
    if (providers.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val providerBadgeListState = rememberLazyListState()
    var activeProviderId by remember(providers) { mutableStateOf(providers.firstOrNull()?.id) }

    LaunchedEffect(lazyListState, providerPositions) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(80)
            .collect { index ->
                val currentEntry = providerPositions.entries.findLast { index >= it.value }
                activeProviderId = currentEntry?.key ?: providers.firstOrNull()?.id
                val targetIndex = providers.indexOfFirst { it.id == activeProviderId }
                if (targetIndex >= 0) {
                    providerBadgeListState.animateScrollToItem(targetIndex)
                }
            }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        state = providerBadgeListState,
    ) {
        items(providers, key = { it.id }) { provider ->
            val selected = provider.id == activeProviderId
            Surface(
                onClick = {
                    val position = providerPositions[provider.id] ?: 0
                    onJumpTo(position)
                },
                shape = CircleShape,
                color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                border = if (selected) {
                    BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
                } else {
                    BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.4f))
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AutoAIIcon(
                        name = provider.name,
                        modifier = Modifier.size(15.dp),
                        color = Color.Transparent,
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: Model,
    providerSetting: ProviderSetting,
    select: Boolean,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tail: @Composable RowScope.() -> Unit = {},
    dragHandle: @Composable (RowScope.() -> Unit)? = null
) {
    val navController = LocalNavController.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val isDark = LocalDarkMode.current
    val cardColor = when {
        select -> colors.primaryContainer.copy(alpha = 0.65f)
        isDark -> colors.surfaceContainerHigh
        else -> colors.surfaceBright
    }
    val cardBorder = if (select) {
        BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f))
    } else {
        BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        contentColor = if (select) colors.onPrimaryContainer else colors.onSurface,
        border = cardBorder,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = true,
                    onLongClick = {
                        onDismiss()
                        navController.navigate(
                            Screen.SettingProviderDetail(
                                providerSetting.id.toString()
                            )
                        )
                    },
                    onClick = { onSelect(model) },
                    interactionSource = interactionSource,
                    indication = LocalIndication.current
                )
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            AutoAIIcon(
                name = model.modelId,
                modifier = Modifier.size(28.dp),
                color = Color.Transparent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (select) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                ModelCompactMetadataRow(model = model, selected = select)
            }
            tail()
            dragHandle?.let { it() }
        }
    }
}

@Composable
private fun ModelCompactMetadataRow(
    model: Model,
    selected: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val badgeColor = if (selected) {
        colors.onPrimaryContainer.copy(alpha = 0.1f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.75f)
    }
    val badgeContentColor = if (selected) {
        colors.onPrimaryContainer.copy(alpha = 0.85f)
    } else {
        colors.onSurfaceVariant
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 输入 -> 输出模态紧凑标识
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor,
            contentColor = badgeContentColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                model.inputModalities.fastForEach { modality ->
                    Icon(
                        imageVector = if (modality == Modality.IMAGE) HugeIcons.Image03 else HugeIcons.Text,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                )
                model.outputModalities.fastForEach { modality ->
                    Icon(
                        imageVector = if (modality == Modality.IMAGE) HugeIcons.Image03 else HugeIcons.Text,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        model.abilities.fastForEach { ability ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor,
                contentColor = badgeContentColor,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (ability) {
                        ModelAbility.TOOL -> Icon(
                            imageVector = HugeIcons.Tools,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                        ModelAbility.REASONING -> Icon(
                            painter = painterResource(R.drawable.deepthink),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelTypeTag(model: Model) {
    Tag(
        type = TagType.INFO
    ) {
        Text(
            text = stringResource(
                when (model.type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            )
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    Tag(
        type = TagType.SUCCESS
    ) {
        model.inputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
        )
        model.outputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
    }
}

/**
 * Battle Mode 专用模型列表。
 *
 * 与 [ModelList] 的区别：
 * - 点击模型为 toggle（已选则取消，未选则添加），不关闭底部弹框
 * - [selectedModelIds] 中的模型以高亮状态显示
 */
@Composable
fun ColumnScope.BattleModelList(
    providers: List<ProviderSetting>,
    selectedModelIds: List<kotlin.uuid.Uuid>,
    onToggle: (Model) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val settings = settingsStore.settingsFlow.collectAsStateWithLifecycle()

    val favoriteModels = settings.value.favoriteModels.mapNotNull { modelId ->
        val model = settings.value.providers.findModelById(modelId) ?: return@mapNotNull null
        if (model.type != ModelType.CHAT) return@mapNotNull null
        val provider = model.findProvider(providers = settings.value.providers, checkOverwrite = false)
            ?: return@mapNotNull null
        model to provider
    }

    var searchKeywords by remember { mutableStateOf("") }

    val typeFilteredModelsByProvider = remember(providers) {
        providers.associate { provider ->
            provider.id to provider.models.fastFilter { it.type == ModelType.CHAT }
        }
    }

    val searchFilteredModelsByProvider = remember(typeFilteredModelsByProvider, searchKeywords) {
        typeFilteredModelsByProvider.mapValues { (_, models) -> models.filterByKeywords(searchKeywords) }
    }

    val providerPositions = remember(providers, favoriteModels, searchFilteredModelsByProvider) {
        var currentIndex = 0
        if (providers.isEmpty()) currentIndex = 1
        if (favoriteModels.isNotEmpty()) {
            currentIndex += 1
            currentIndex += favoriteModels.size
        }
        providers.map { provider ->
            val position = currentIndex
            currentIndex += 1
            currentIndex += searchFilteredModelsByProvider[provider.id].orEmpty().size
            provider.id to position
        }.toMap()
    }

    val lazyListState = rememberLazyListState()

    ModelSearchBar(
        query = searchKeywords,
        onQueryChange = { searchKeywords = it },
    )

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(MODEL_LIST_ITEM_SPACING),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        if (providers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.model_list_no_providers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendColors.gray6,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        if (favoriteModels.isNotEmpty()) {
            stickyHeader(key = "header:favorite") {
                ModelFavoriteSectionHeader()
            }
            items(
                items = favoriteModels,
                key = { "favorite:" + it.first.id.toString() },
            ) { (model, provider) ->
                ModelItem(
                    model = model,
                    onSelect = { onToggle(it) },
                    modifier = Modifier.animateItem(),
                    providerSetting = provider,
                    select = selectedModelIds.contains(model.id),
                    onDismiss = {},
                )
            }
        }

        providers.fastForEach { providerSetting ->
            stickyHeader(key = "header:${providerSetting.id}") {
                ModelProviderSectionHeader(providerSetting = providerSetting)
            }
            items(
                items = searchFilteredModelsByProvider[providerSetting.id].orEmpty(),
                key = { it.id },
            ) { model ->
                ModelItem(
                    model = model,
                    onSelect = { onToggle(it) },
                    modifier = Modifier.animateItem(),
                    providerSetting = providerSetting,
                    select = selectedModelIds.contains(model.id),
                    onDismiss = {},
                )
            }
        }
    }

    ProviderQuickJumpBar(
        providers = providers,
        lazyListState = lazyListState,
        providerPositions = providerPositions,
        onJumpTo = { position ->
            coroutineScope.launch {
                lazyListState.animateScrollToItem(position)
            }
        },
    )
}

@Composable
fun ModelAbilityTag(model: Model) {
    model.abilities.fastForEach { ability ->
        when (ability) {
            ModelAbility.TOOL -> {
                Tag(
                    type = TagType.WARNING
                ) {
                    Icon(
                        imageVector = HugeIcons.Tools,
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
                    )
                }
            }

            ModelAbility.REASONING -> {
                Tag(
                    type = TagType.INFO
                ) {
                    Icon(
                        painter = painterResource(R.drawable.deepthink),
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                    )
                }
            }
        }
    }
}
