---
name: sync-upstream
description: Use this skill when the user wants to pull/sync upstream changes into their local fork, resolve merge conflicts, and preserve locally added features. Triggered by phrases like "拉取upstream代码", "同步上游", "sync upstream", "pull upstream", "merge upstream", or "rebase upstream".
---

# Sync Upstream (同步上游代码)

本 Skill 用于将 upstream 的最新代码合并到本地 fork，并在出现冲突时**优先保留本地 fork 添加的功能与本地 UI 样式改版**。

> 在解决任何 UI 冲突或审查上游 UI 改动前，**必须先阅读** [local-ui-style.md](references/local-ui-style.md)。

---

## 冲突分析原则（重要）

| 冲突类型 | 处理策略 |
|---|---|
| 上游修改了本地**未改动**的代码 | 直接采用上游版本（若是 UI 文件，同步后仍需执行下方「UI 零冲突漂移审计」） |
| 上游和本地**都修改**了同一处（逻辑 vs 逻辑） | 语义分析，融合两者，优先保留本地功能 |
| 上游改了业务逻辑/状态，本地改了**UI 样式**（高频场景） | **只移植上游的新增参数/状态/回调逻辑，严格保留本地 UI 壳、组件封装与视觉参数** |
| 上游**删除**了本地新增的代码块 | 保留本地新增代码 |
| 上游新增了全新代码，本地无对应 | 采用上游新增；若属于 UI 页面/弹窗/表单，立即按 `local-ui-style.md` 替换为 `App*` 组件 |
| 本地新增了全新代码，上游无对应 | 保留本地新增 |

---

## 标准同步流程

1. **合并前状态检查（防止工作区污染）**
   - 先检查 `git status` 和 `.git/MERGE_HEAD`：若工作区存在未提交改动或未完成的 merge，**先停下来提示用户或先处理干净再合并**，不要把新功能/样式修改直接混进上游 merge commit 里。
   - 先 `git fetch upstream`，确认将要合入的提交范围。

2. **逐文件语义解决冲突**
   - **永远不要**在没有分析的情况下直接 `git checkout --theirs` 或 `git checkout --ours` 覆盖整个文件。
   - 对于复杂冲突，逐段阅读冲突块，理解上下游双方意图。
   - 涉及 `ChatPage.kt`、`ChatInput.kt`、`ChatList.kt`、`ChatDrawer.kt`、`ModelList.kt`、`BattleModeSection.kt`、`ReasoningPicker.kt`、`FavoritePage.kt`、`SearchPage.kt`、`SettingDisplayPage.kt`、`RouteActivity.kt` 等深改 UI 文件时，对照 `references/local-ui-style.md` 确保所有视觉与布局不变量不被覆盖。
   - 涉及数据库 schema（`AppDatabase`、`migrations/`、`BackupManager`）、API 接口定义、`ai/` 模块推理档位（`resolveReasoningLevels` / `supportedReasoningLevels` / `rawLabel`）时仔细合并。

3. **同步后「UI 零冲突漂移审计」（必做）**
   - 即使 `git merge` 对某些上游 UI 文件**没有报冲突**（例如上游新加页面、新加弹窗、修改未冲突设置页），也必须在合并后排查上游在 `app/src/main/java/me/rerere/rikkahub/ui/` 和 `ui/src/main/java/` 修改/新增的所有文件：
     - 把新增的 `ModalBottomSheet` → `AppModalBottomSheet`
     - 把新增的 `AlertDialog` → `AppAlertDialog`
     - 把新增的 `DropdownMenu` / `DropdownMenuItem` → `AppDropdownMenu` / `AppDropdownMenuItem`
     - 把新增的 `androidx.compose.material3.Switch` → `AppSwitch`
     - 把新增的 `Slider` → `AppSlider`
     - 把新增的 `LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior` → 紧凑 `TopAppBar`
     - 把新增的 `SegmentedButton` / `*TabRow` → `ConnectedChoiceButtons` / `PagerTabs`
     - 检查 `ui/` 控件与 `Theme.kt` / `Type.kt` / `CardGroup.kt` 是否被无冲突覆盖或引入新分支。

4. **自动化校验（必做）**
   - 运行单元测试（包含 `ForkUiStyleGuardTest` 样式守卫测试与 `ImeAutoScrollerTest` 等）与 Debug 编译：
     ```bash
     ./gradlew :app:testGithubDebugUnitTest :ai:test :app:assembleGithubDebug
     ```
   - 如 `ForkUiStyleGuardTest` 报错，按报错清单将所有违规文件和锚点修复通过为止。
   - 检查数据导出/导入和 Room migration 逻辑（遵循 `AGENTS.md` 中的手动迁移与版本号规则）是否受本次 upstream 同步影响。

5. **汇报结果**
   - 如果冲突过多，先向用户列出冲突文件清单再逐一解决。
   - 汇报合入的 upstream 提交范围、解决的冲突、UI 漂移审计修复项以及编译测试结果。

---

## 注意事项
- **重要**: 除非用户明确要求提交/推送，否则**不要自动 commit 和 push 代码**。
- 编写或合入 Kotlin 代码时严格遵守 `AGENTS.md` 中的 Import 规则（禁止在代码正文使用完全限定名）。
