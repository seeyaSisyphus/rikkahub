# Repository Guidelines

本文档面向贡献者，概述本仓库的模块结构、开发流程，便于快速上手并保持一致的协作质量。

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material 3 Expressive (MD3 Expressive) design language.

## Design Language

The UI follows Material 3 Expressive. New and reworked screens should match it rather than the older baseline
Material 3 look (cards with dividers, plain forms).

- The app theme is `MaterialExpressiveTheme` (app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt); prefer the
  expressive variants of components, shapes (`MaterialShapes`), emphasized typography (e.g. `titleSmallEmphasized`)
  and motion from `androidx.compose.material3` over hand-rolled equivalents.
- Settings-style and form pages use segmented groups via `CardGroup` (
  app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt) instead of a card with dividers.
- Favor larger corner radii, filled/tonal containers, and whole-row click targets.

## Build, Test, and Development Commands

```bash
./gradlew assembleGithubDebug    # 构建 GitHub 渠道的 Debug APK
./gradlew assembleGithubRelease  # 构建 GitHub Release / 每日构建用的 APK
./gradlew bundlePlayRelease      # 构建上架 Google Play 的 AAB
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
./gradlew spotlessApply          # 用 ktlint 格式化相对 origin/master 改动过的 Kotlin 文件
./gradlew spotlessCheck          # 只检查格式不改文件
```

构建应用需要在 `app/` 下提供 `google-services.json`（用于 Firebase）
`web` 模块会在 `preBuild` 阶段构建 `web-ui/` 并复制静态资源，需要本地可用 `pnpm`。

App 有一个 `distribution` 维度的 product flavor：`github`（默认）和 `play`，共用同一个 applicationId。
渠道差异用 `BuildConfig.IS_PLAY_STORE` 判断（如 Play 版不检查更新、不显示更新提醒）。

## Import 规则

编写 Kotlin 代码时，**每次引用一个新的类、函数或扩展函数，必须同时添加对应的 `import` 语句**。不要使用完全限定名（fully qualified name）直接写在代码中，例如：

```kotlin
// ❌ 错误：缺少 import，且使用完全限定名
sh.calvin.reorderable.ReorderableItem(reorderState, key = tag.id.toString()) { ... }

// ✅ 正确：先 import，再直接使用
import sh.calvin.reorderable.ReorderableItem
ReorderableItem(reorderState, key = tag.id.toString()) { ... }
```

常见的容易遗漏 import 的场景：

- `ColumnScope` / `RowScope` — `import androidx.compose.foundation.layout.ColumnScope`
- `ReorderableItem` / `rememberReorderableLazyListState` — `import sh.calvin.reorderable.XXX`
- `longPressDraggableHandle` — reorderable 库的扩展函数
- `HugeIcons` 及各类图标 — `import me.rerere.hugeicons.HugeIcons` / `import me.rerere.hugeicons.stroke.XXX`
- `LocalToaster.current` — `import me.rerere.rikkahub.ui.components.ui.LocalToaster`
- `rememberLazyListState` — `import androidx.compose.foundation.lazy.rememberLazyListState`（与 `LazyListState` 是两个独立的 import，缺一个就会报错）
- `rememberScrollState` / `verticalScroll` / `horizontalScroll` — `import androidx.compose.foundation.rememberScrollState` / `import androidx.compose.foundation.verticalScroll`
- `collectAsStateWithLifecycle` — `import androidx.lifecycle.compose.collectAsStateWithLifecycle`
- `toPaddingValues` / `asPaddingValues` 等扩展函数 — 注意对应包路径

> ❗️ **重要提示（AI 助手必读）**：每次向现有文件新增一个类、函数、扩展函数的引用，必须先检查文件头部是否已有对应 `import`。
> 就算同个包下的不同类（如 `LazyListState` 和 `rememberLazyListState`）也各自需要独立的 import，不要假定已包含。

**在提交代码前，确保编译通过**（`./gradlew assembleGithubDebug`），避免因遗漏 import 导致构建失败。

## Coding Style & Naming Conventions

本仓库使用 `.editorconfig` 统一格式：

- Kotlin/Gradle 脚本：4 空格缩进，最大行长 120。
- XML/JSON：2 空格缩进。
- Markdown/YAML：2 空格缩进，允许尾随空格（用于对齐）。

命名习惯：模块名为小写目录（如 `ai/`、`speech/`），Kotlin 类遵循 PascalCase，测试类以 `*Test` 结尾。

## Testing Guidelines

测试框架以 JUnit/AndroidX Test 为主。未设定强制覆盖率门槛，但新逻辑应配套新增/更新测试。测试文件命名建议：

- 单元测试：`FooTest.kt`
- 仪器测试：`FooInstrumentedTest.kt` 或 `*Test.kt`

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **material3**: Material color utility extensions used by the app UI
- **ui**: Reusable Compose UI components that do not depend on app logic
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.
- **terminal**: Terminal emulator core taken from termux-app's `terminal-emulator` (package `com.termux.terminal`, kept
  binary compatible with the `terminal-view` aar). `TerminalSession` runs over a `TerminalTransport`: local PRoot
  terminals use `LocalPtyTransport`, SSH workspaces use the app's `SshTerminalTransport`. Origin and local changes are
  listed in terminal/README.md.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings.
  Once a conversation is persisted it also holds a `ConversationConfig` snapshot (chat model, reasoning level, search,
  MCP servers, workspace, skills) taken from the assistant; from then on chat-page changes to those settings stay on
  the conversation, and code should read them through `Settings.getAssistantOf(conversation)` /
  `Settings.getChatModelOf(conversation)` instead of the assistant directly. Once loaded, the in-memory
  `ConversationSession` is the single source of truth: change a conversation through `ChatService` (which applies
  `session.update { latest -> ... }` and then `flush()`), never by saving a whole `Conversation` snapshot or one read
  back from the database; deleting also goes through `ChatService` so a running generation cannot re-insert it. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt,
  app/src/main/java/me/rerere/rikkahub/data/model/ConversationConfig.kt,
  app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- Add, translate or clean up strings with the `tools/i18n` CLI (Bun); see the `i18n-localization` skill.

## Database Migration

数据库迁移统一使用**手动 Migration**（不使用 AutoMigration），相关文件：

- 数据库定义：`app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
- 手动迁移：`app/src/main/java/me/rerere/rikkahub/data/db/migrations/`，在 `AppDatabaseFactory.kt` 的 `addMigrations()` 中注册
- 备份/恢复：`app/src/main/java/me/rerere/rikkahub/data/sync/BackupManager.kt` + `DatabaseBackup.kt`

### 每次新增 schema 变更时必须做

1. **递增 `version`**（`@Database` 注解）
2. **同步更新 `AppDatabase.VERSION` 常量**（`companion object` 中），供备份恢复逻辑动态引用
3. **添加对应手动 Migration** 并注册到 `AppDatabaseFactory`

> 本 fork 不使用 `AutoMigration`：fork 与上游的版本号在 v25 后分叉（fork 25→26→27，上游 25→…→32），
> 合并后统一推进到 v33（始终比上游最新版本高一级，避免同版本不同 schema 的库绕过迁移）。
> v25→v33 的每一步迁移都会调用幂等的 `ensureMergedSchema()`
> （`MigrationUtils.kt`），以 IF NOT EXISTS / try-catch 方式兼容两条 lineage 的旧库。
> 上游 v30→v31 的 `usage_record` 回填保留在 `Migration_30_31.kt` 中，随后再补齐 fork 侧 schema。

### 手动 Migration 编写规范

- **新增列**：使用 `ALTER TABLE ... ADD COLUMN`，用 `try-catch` 忽略 duplicate 错误（兼容旧备份恢复场景）
- **修改列约束**（如 nullable → NOT NULL）：必须用**重建表**方式，不能只用 `ALTER TABLE`
  - `CREATE TABLE new_table`（含正确约束）→ `INSERT ... SELECT` → `DROP TABLE old` → `RENAME`
  - 用 `COALESCE(col, default)` 处理旧数据中可能的 NULL 值

### 备份/恢复注意事项

- **备份时**：`DatabaseBackup.createSnapshot` 通过 `VACUUM INTO` 生成一致快照（WAL 内容已包含）
- **恢复时**：`BackupManager` 先用 `DatabaseBackup.normalize` 校验备份完整性，再以
  `AppDatabaseFactory.create()` 打开暂存副本——Room 会按备份的 `user_version` 自动执行
  对应的迁移链升到当前版本，校验通过后才发布替换正式库。**无需手动维护版本号**，
  迁移必须保持幂等（旧备份可能来自分叉 lineage 的任一版本）
