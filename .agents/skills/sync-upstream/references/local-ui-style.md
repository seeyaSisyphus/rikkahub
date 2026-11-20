# Local Fork UI Style Invariants (本地 UI 样式不变量清单)

本 fork 对全应用视觉语言做了**极简编辑阅读风 + iOS 轻量质感**大整改，区别于上游默认的 Material 3 Expressive（粗重胶囊、大标题折叠顶栏、厚重 Switch、默认高对比阴影与水波纹）。

**在 `sync-upstream` 合并上游代码时，本文档中的所有设计约束为高优先级不变量：任何冲突或上游新代码都必须向以下规范对齐，不得回退为上游默认 MD3 Expressive 样式。**

---

## 1. 全局强制替换组件（零容忍规则）

在 `app/src/main/java/me/rerere/rikkahub/` 下禁止直接调用原生 Material 3 组件（仅允许对应封装文件自身调用）：

| 上游原生组件 / 写法 | Fork 强制替代组件 | 封装位置 & 核心视觉参数 |
|---|---|---|
| `ModalBottomSheet` | `AppModalBottomSheet` | `app/.../ui/components/ui/AppOverlays.kt`：`24.dp` 顶部圆角、`surfaceContainerLow`、`tonalElevation = 0.dp`、`AppSheetDragHandle`（`36.dp × 4.dp`） |
| `AlertDialog` | `AppAlertDialog` | `app/.../ui/components/ui/AppOverlays.kt`：`24.dp` 圆角、`surfaceContainerHigh`、`tonalElevation = 0.dp` |
| `DropdownMenu` / `DropdownMenuItem` / `DropdownMenuPopup` / `DropdownMenuGroup` | `AppDropdownMenu` / `AppDropdownMenuItem` / `AppMenuDivider` | `app/.../ui/components/ui/AppDropdownMenu.kt`：`20.dp` 圆角、浅色 `surfaceBright` / 深色 `surfaceContainerHigh`、`tonalElevation = 0.dp`、`shadowElevation = 8.dp`、`0.5.dp` 发丝边框 |
| `androidx.compose.material3.Switch` | `AppSwitch`（常规）或 `me.rerere.ui.components.Switch`（仅需 `SwitchSize.Small` 的紧凑行） | `app/.../ui/components/ui/AppSwitch.kt`：`46.dp × 26.dp` iOS 胶囊开关、按压拉伸微动效 |
| `androidx.compose.material3.Slider` | `AppSlider` / `IntSlider` | `app/.../ui/components/ui/SliderItem.kt`：细轨道胶囊滑块与带环滑块头 |
| `androidx.compose.material3.OutlinedTextField` | `AppOutlinedTextField` | `app/.../ui/components/ui/AppTextField.kt`：`14.dp` 圆角、柔和边框与微弱填充底色 |
| `LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar` / `LargeTopAppBar` / `MediumTopAppBar` / `exitUntilCollapsedScrollBehavior` | 紧凑 `TopAppBar` | 去掉折叠大标题与对应 `nestedScroll`，使用 `CustomColors.topBarColors` |
| `SingleChoiceSegmentedButtonRow` / `MultiChoiceSegmentedButtonRow` / `SegmentedButton` | `ConnectedChoiceButtons` / `ConnectedMultiChoiceButtons` | `app/.../ui/components/ui/ConnectedChoiceButtons.kt`：滑动胶囊轨道单选 / 独立圆角卡片多选 |
| `PrimaryTabRow` / `SecondaryTabRow` / `TabRow` / `ScrollableTabRow` | `PagerTabs` | `app/.../ui/components/ui/PagerTabs.kt`：与 `PagerState` 进度实时同步的滑动胶囊选项卡 |
| `ExposedDropdownMenuBox` / `ExposedDropdownMenu` | 文本按钮 + `AppDropdownMenu` 或自定义选择项 | 禁止引入未统一样式的原生下拉框 |

> 说明：如使用 `BasicAlertDialog`（如 `Markdown.kt` 导出表格对话框），内层 `Surface` 必须显式设置 `RoundedCornerShape(24.dp)`、`surfaceContainerHigh`、`tonalElevation = 0.dp`。

---

## 2. 全局主题、字体与基础组件基线

1. **主题与柔和水波纹**（[Theme.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt)）
   - 保留 `SoftRippleConfiguration`（`pressedAlpha = 0.06f` 等）并通过 `LocalRippleConfiguration provides SoftRippleConfiguration` 注入。
2. **字体与排版体系**（[Type.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/theme/Type.kt)）
   - 保留 `AppFontFamily`（`Noto Sans SC` + `DM Sans`）及定制后的 `Typography` 字重与 `lineHeight`。
3. **设置分组卡片**（[CardGroup.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt)）
   - `CardGroupIcon` **不带圆形底色背景**（`24.dp` 容器 + `22.dp` 图标）。
   - `switchItem` 使用 `AppSwitch`。
4. **左滑删除**（[SwipeToDelete.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/ui/SwipeToDelete.kt)）
   - 保留两段式左滑交互（短滑停留露出圆形操作钮 + 深滑越过 `DeepCommitThreshold` 震动直接提交）。
5. **`ui` 模块基础控件**（`ui/src/main/java/me/rerere/ui/`）
   - `ConfirmDialog.kt`、`Select.kt`、`Switch.kt`、`DataTable.kt`（支持 `horizontalDividersOnly`）、`sketch/SketchBrushMenu.kt`、`SketchDialog.kt`、`SketchPaperMenu.kt`、`SketchTextInput.kt` 已统一样式（`20.dp`/`24.dp` 圆角、`tonalElevation = 0.dp`、发丝边框、iOS 开关），合并上游时不能回退。
6. **页面转场动效**（[RouteActivity.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/RouteActivity.kt)）
   - 保留 `PAGE_PUSH_DURATION_MS` / `PAGE_POP_DURATION_MS` 与 `PagePushEasing` / `PagePopEasing`（`0.94f` 缩放 + `-it / 4` 视差推入弹出），不要被上游默认 `0.7f` 缩放和 `-it / 2` 覆盖。

---

## 3. 高频冲突页面的核心视觉与交互不变量

上游更新最容易在以下高频文件触发冲突；合并时**只移植上游的新业务逻辑/状态管理，保留本地 UI 结构与布局参数**：

1. **聊天页顶栏与列表边界**（[ChatPage.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt)、[ChatList.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt)）
   - 顶栏使用无底部 `HorizontalDivider` 的干净 `TopAppBar`；列表顶部使用柔和 `topFadingEdge(18.dp)`（不与硬分隔线混用）。
   - `ChatPage` / `ChatList` / `ChatListPreview` 对 `Scaffold.innerPadding` 统一采用**布局期代理 `PaddingValues`**（如 `stabilizedInnerPadding`、`listContentPadding`、`topInsetPadding`），并由 `ChatPage` 向 `ChatList` 传入 `suppressBottomPaddingAutoScroll: () -> Boolean`，禁止在组合期快照 `innerPadding.calculateBottomPadding()`。
2. **输入法跟滚补偿**（[ImeAutoScroller.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/hooks/ImeAutoScroller.kt)）
   - 保留 `computeImeAutoScrollDelta` 与 `scrollBy` 期间不吞连续键盘帧的逻辑，配套单元测试 `ImeAutoScrollerTest`。
3. **聊天输入框**（[ChatInput.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt)）
   - 展开图标为左下到右上 `HugeIcons.ArrowExpand01`（与缩小图标 `HugeIcons.ArrowShrink02` 对称配对，不要改回 `ArrowExpand02`）。
   - 卡片边框为 `BorderStroke(1.dp, borderColor)`（浅色 `0.90f` / 深色 `0.78f`），非模糊模式下轻阴影 `elevation = 5.dp`（深色 `2.dp`），外边距 `horizontal = 14.dp`、内边距 `(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)`。
   - 仅在 `isFloating` 时读取窗口 insets，并通过 `SideEffect` 同步 `onFloatingChange` / `onExpandedHeightDeltaChange`。
4. **富文本与消息动作**（[Markdown.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt)、[HighlightCodeBlock.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/HighlightCodeBlock.kt)、[ChatMessage.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt)）
   - 代码块/表格卡片保持 `16.dp` 圆角、`0.5.dp` 细边框及 `0.5.dp` 头部分隔线；历史消息操作栏淡出透明度 `0.55f`。
5. **模型推理档位与选择器**（[ReasoningPicker.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/components/ai/ReasoningPicker.kt)、[BattleModeSection.kt](file:///home/mytit/ai-agent/rikkahub/app/src/main/java/me/rerere/rikkahub/ui/pages/chat/BattleModeSection.kt)）
   - 依赖 `ai/` 模块中的 `ModelRegistry.resolveReasoningLevels` / `closestReasoningLevel` / `ReasoningLevel.rawLabel` / `Model.supportedReasoningLevels`，同步上游模型注册表时勿删掉这些能力。
6. **深改页面清单**
   - `ChatDrawer`、`ConversationList`、`BattleModeSection`、`ModelList`、`ReasoningPicker`、`AssistantPicker`、`FilesPicker`、`McpPicker`、`SearchPicker`、`FavoritePage`、`SearchPage`、`HistoryPage`、`TagView`、`SettingDisplayPage`、`SettingThemePage` 等所有二级/设置页面均已去 `LargeFlexibleTopAppBar` 并迁至上述全局封装组件。

---

## 4. 自动化样式守卫

`app/src/test/java/me/rerere/rikkahub/ui/ForkUiStyleGuardTest.kt` 在 JVM 单测中静态校验：
1. `app/` 中不存在绕过白名单的原生 `ModalBottomSheet` / `AlertDialog` / `DropdownMenu*` / `Switch` / `Slider` / `LargeFlexibleTopAppBar` / `SegmentedButton` / `TabRow` / `ExposedDropdownMenu*` 或 `androidx.compose.material3.*` 通配符导入；
2. `Theme.kt`、`Type.kt`、`CardGroup.kt`、`AppOverlays.kt`、`AppDropdownMenu.kt`、`AppSwitch.kt`、`ChatPage.kt`、`ChatList.kt`、`ChatListPreview.kt`、`ChatInput.kt`、`ImeAutoScroller.kt` 及 `ui/` 模块控件的关键样式锚点未被静默回退。
