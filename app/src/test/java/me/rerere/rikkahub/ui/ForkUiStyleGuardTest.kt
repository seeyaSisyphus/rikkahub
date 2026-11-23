package me.rerere.rikkahub.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守护 fork 的极简 UI 样式规范，防止在 sync-upstream 同步上游或新增页面时
 * 静默引入原生 Material 3 组件或回退核心样式锚点。
 */
class ForkUiStyleGuardTest {
    private val repoRoot: File by lazy { locateRepoRoot() }

    @Test
    fun `app sources should use fork wrappers instead of raw Material3 components`() {
        val appUiRoot = repoRoot.resolve("app/src/main/java/me/rerere/rikkahub")
        val ktFiles = appUiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected Kotlin source files under $appUiRoot", ktFiles.isNotEmpty())

        val violations = mutableListOf<String>()

        val forbiddenImportRules = listOf(
            ImportRule(
                prefix = "androidx.compose.material3.ModalBottomSheet",
                allowedRelativePaths = setOf("ui/components/ui/AppOverlays.kt"),
                replacement = "AppModalBottomSheet",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.AlertDialog",
                allowedRelativePaths = setOf("ui/components/ui/AppOverlays.kt"),
                replacement = "AppAlertDialog",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.DropdownMenu",
                allowedRelativePaths = setOf("ui/components/ui/AppDropdownMenu.kt"),
                replacement = "AppDropdownMenu / AppDropdownMenuItem",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.Switch",
                allowedRelativePaths = emptySet(),
                replacement = "AppSwitch",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.Slider",
                allowedRelativePaths = setOf("ui/components/ui/SliderItem.kt"),
                replacement = "AppSlider / IntSlider",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.OutlinedTextField",
                allowedRelativePaths = setOf("ui/components/ui/AppTextField.kt"),
                replacement = "AppOutlinedTextField",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.LargeFlexibleTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.MediumFlexibleTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.LargeTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.MediumTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.SingleChoiceSegmentedButtonRow",
                allowedRelativePaths = emptySet(),
                replacement = "ConnectedChoiceButtons",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.MultiChoiceSegmentedButtonRow",
                allowedRelativePaths = emptySet(),
                replacement = "ConnectedMultiChoiceButtons",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.SegmentedButton",
                allowedRelativePaths = emptySet(),
                replacement = "ConnectedChoiceButtons / ConnectedMultiChoiceButtons",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.PrimaryTabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.SecondaryTabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.TabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.ScrollableTabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.ExposedDropdownMenuBox",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenu",
            ),
            ImportRule(
                prefix = "androidx.compose.material3.ExposedDropdownMenu",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenu",
            ),
        )

        val forbiddenCallRules = listOf(
            CallRule(
                symbol = "ModalBottomSheet",
                allowedRelativePaths = setOf("ui/components/ui/AppOverlays.kt"),
                replacement = "AppModalBottomSheet",
            ),
            CallRule(
                symbol = "AlertDialog",
                allowedRelativePaths = setOf("ui/components/ui/AppOverlays.kt"),
                replacement = "AppAlertDialog",
            ),
            CallRule(
                symbol = "DropdownMenu",
                allowedRelativePaths = setOf("ui/components/ui/AppDropdownMenu.kt"),
                replacement = "AppDropdownMenu",
            ),
            CallRule(
                symbol = "DropdownMenuItem",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenuItem",
            ),
            CallRule(
                symbol = "DropdownMenuPopup",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenu",
            ),
            CallRule(
                symbol = "DropdownMenuGroup",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenu",
            ),
            CallRule(
                symbol = "Slider",
                allowedRelativePaths = setOf("ui/components/ui/SliderItem.kt"),
                replacement = "AppSlider",
            ),
            CallRule(
                symbol = "OutlinedTextField",
                allowedRelativePaths = setOf("ui/components/ui/AppTextField.kt"),
                replacement = "AppOutlinedTextField",
            ),
            CallRule(
                symbol = "LargeFlexibleTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            CallRule(
                symbol = "MediumFlexibleTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            CallRule(
                symbol = "LargeTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            CallRule(
                symbol = "MediumTopAppBar",
                allowedRelativePaths = emptySet(),
                replacement = "TopAppBar",
            ),
            CallRule(
                symbol = "exitUntilCollapsedScrollBehavior",
                allowedRelativePaths = emptySet(),
                replacement = "fixed TopAppBar",
            ),
            CallRule(
                symbol = "SingleChoiceSegmentedButtonRow",
                allowedRelativePaths = emptySet(),
                replacement = "ConnectedChoiceButtons",
            ),
            CallRule(
                symbol = "MultiChoiceSegmentedButtonRow",
                allowedRelativePaths = emptySet(),
                replacement = "ConnectedMultiChoiceButtons",
            ),
            CallRule(
                symbol = "SegmentedButton",
                allowedRelativePaths = emptySet(),
                replacement = "ConnectedChoiceButtons / ConnectedMultiChoiceButtons",
            ),
            CallRule(
                symbol = "PrimaryTabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            CallRule(
                symbol = "SecondaryTabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            CallRule(
                symbol = "TabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            CallRule(
                symbol = "ScrollableTabRow",
                allowedRelativePaths = emptySet(),
                replacement = "PagerTabs",
            ),
            CallRule(
                symbol = "ExposedDropdownMenuBox",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenu",
            ),
            CallRule(
                symbol = "ExposedDropdownMenu",
                allowedRelativePaths = emptySet(),
                replacement = "AppDropdownMenu",
            ),
        )

        val material3WildcardImport = Regex(
            """^\s*import\s+androidx\.compose\.material3\.\*\s*$""",
            RegexOption.MULTILINE,
        )
        val fqnMaterial3Rules = listOf(
            FqnRule("ModalBottomSheet", setOf("ui/components/ui/AppOverlays.kt"), "AppModalBottomSheet"),
            FqnRule("AlertDialog", setOf("ui/components/ui/AppOverlays.kt"), "AppAlertDialog"),
            FqnRule("DropdownMenu", setOf("ui/components/ui/AppDropdownMenu.kt"), "AppDropdownMenu"),
            FqnRule("DropdownMenuItem", emptySet(), "AppDropdownMenuItem"),
            FqnRule("DropdownMenuPopup", emptySet(), "AppDropdownMenu"),
            FqnRule("DropdownMenuGroup", emptySet(), "AppDropdownMenu"),
            FqnRule("Switch", emptySet(), "AppSwitch"),
            FqnRule("Slider", setOf("ui/components/ui/SliderItem.kt"), "AppSlider"),
            FqnRule("OutlinedTextField", setOf("ui/components/ui/AppTextField.kt"), "AppOutlinedTextField"),
            FqnRule("LargeFlexibleTopAppBar", emptySet(), "TopAppBar"),
            FqnRule("MediumFlexibleTopAppBar", emptySet(), "TopAppBar"),
            FqnRule("LargeTopAppBar", emptySet(), "TopAppBar"),
            FqnRule("MediumTopAppBar", emptySet(), "TopAppBar"),
            FqnRule("SingleChoiceSegmentedButtonRow", emptySet(), "ConnectedChoiceButtons"),
            FqnRule("MultiChoiceSegmentedButtonRow", emptySet(), "ConnectedMultiChoiceButtons"),
            FqnRule("SegmentedButton", emptySet(), "ConnectedChoiceButtons / ConnectedMultiChoiceButtons"),
            FqnRule("PrimaryTabRow", emptySet(), "PagerTabs"),
            FqnRule("SecondaryTabRow", emptySet(), "PagerTabs"),
            FqnRule("TabRow", emptySet(), "PagerTabs"),
            FqnRule("ScrollableTabRow", emptySet(), "PagerTabs"),
            FqnRule("ExposedDropdownMenuBox", emptySet(), "AppDropdownMenu"),
            FqnRule("ExposedDropdownMenu", emptySet(), "AppDropdownMenu"),
        )

        for (file in ktFiles) {
            val relPath = file.relativeTo(appUiRoot).invariantSeparatorsPath
            val raw = file.readText()
            val imports = raw.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .toList()

            if (material3WildcardImport.containsMatchIn(raw)) {
                violations += "$relPath: wildcard import androidx.compose.material3.* is disallowed"
            }

            for (rule in forbiddenImportRules) {
                if (relPath in rule.allowedRelativePaths) continue
                val matched = imports.firstOrNull { line ->
                    val imported = line.removePrefix("import ").trim().substringBefore(" as ").trim()
                    imported == rule.prefix || imported.startsWith("${rule.prefix}.")
                }
                if (matched != null) {
                    violations += "$relPath: forbidden import `$matched` -> use ${rule.replacement}"
                }
            }

            val strippedBody = stripCommentsAndStrings(raw)
                .lineSequence()
                .filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("package ") }
                .joinToString("\n")

            for (rule in fqnMaterial3Rules) {
                if (relPath in rule.allowedRelativePaths) continue
                if (rule.pattern.containsMatchIn(strippedBody)) {
                    violations += "$relPath: fully qualified Material3 call `${rule.symbol}` -> use ${rule.replacement}"
                }
            }

            for (rule in forbiddenCallRules) {
                if (relPath in rule.allowedRelativePaths) continue
                if (rule.pattern.containsMatchIn(strippedBody)) {
                    violations += "$relPath: raw `${rule.symbol}(...)` call -> use ${rule.replacement}"
                }
            }

            // Switch(...) 只允许通过 me.rerere.ui.components.Switch（小尺寸开关），禁止未声明或 M3 Switch
            if (SWITCH_CALL_REGEX.containsMatchIn(strippedBody)) {
                val hasUiSwitchImport = imports.any {
                    it.removePrefix("import ").trim().substringBefore(" as ").trim() ==
                        "me.rerere.ui.components.Switch"
                }
                if (!hasUiSwitchImport) {
                    violations += "$relPath: Switch(...) without me.rerere.ui.components.Switch import -> use AppSwitch"
                }
            }
        }

        assertTrue(
            "Found raw Material3 UI violations (sync-upstream drift?):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `core fork theme, wrappers, and chat anchors remain intact`() {
        val requiredSnippets = mapOf(
            "app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt" to listOf(
                "SoftRippleConfiguration",
                "LocalRippleConfiguration provides SoftRippleConfiguration",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/theme/Type.kt" to listOf(
                "AppFontFamily",
                "Noto Sans SC",
                "DM Sans",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ui/AppOverlays.kt" to listOf(
                "fun AppModalBottomSheet(",
                "fun AppAlertDialog(",
                "fun AppSheetDragHandle(",
                "tonalElevation: Dp = 0.dp",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ui/AppDropdownMenu.kt" to listOf(
                "fun AppDropdownMenu(",
                "fun AppDropdownMenuItem(",
                "tonalElevation = 0.dp",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ui/AppSwitch.kt" to listOf(
                "fun AppSwitch(",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ui/AppTextField.kt" to listOf(
                "fun AppOutlinedTextField(",
                "RoundedCornerShape(14.dp)",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt" to listOf(
                "AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt" to listOf(
                "TopAppBar(",
                "TopAppBarDefaults.topAppBarColors(",
                "stabilizedInnerPadding",
                "suppressBottomPaddingAutoScroll =",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt" to listOf(
                "listContentPadding",
                "topInsetPadding",
                "LazyListLookaheadScrollBackSanitizer(lazyListState = state)",
                "canScrollForward",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatListPreview.kt" to listOf(
                "previewTopPadding",
                "previewContentPadding",
                "LazyListLookaheadScrollBackSanitizer(lazyListState = listState)",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatListOverlays.kt" to listOf(
                "if (state.canScrollForward)",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt" to listOf(
                "HugeIcons.ArrowExpand01",
                "HugeIcons.ArrowShrink02",
                "SideEffect {",
                "BorderStroke(1.dp, borderColor)",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/hooks/ImeAutoScroller.kt" to listOf(
                "fun computeImeAutoScrollDelta(",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/hooks/LazyListLookaheadSanitizer.kt" to listOf(
                "fun LazyListLookaheadScrollBackSanitizer(",
                "internal class LookaheadScrollBackSanitizerTracker",
            ),
            "app/src/main/java/me/rerere/rikkahub/RouteActivity.kt" to listOf(
                "PAGE_PUSH_DURATION_MS",
                "PAGE_POP_DURATION_MS",
                "PagePushEasing",
                "PagePopEasing",
            ),
            "ui/src/main/java/me/rerere/ui/components/ConfirmDialog.kt" to listOf(
                "RoundedCornerShape(24.dp)",
                "surfaceContainerHigh",
            ),
            "ui/src/main/java/me/rerere/ui/components/Select.kt" to listOf(
                "RoundedCornerShape(20.dp)",
                "tonalElevation = 0.dp",
            ),
            "ui/src/main/java/me/rerere/ui/sketch/SketchBrushMenu.kt" to listOf(
                "RoundedCornerShape(20.dp)",
                "tonalElevation = 0.dp",
            ),
            "ui/src/main/java/me/rerere/ui/sketch/SketchPaperMenu.kt" to listOf(
                "RoundedCornerShape(20.dp)",
                "tonalElevation = 0.dp",
            ),
            "ui/src/main/java/me/rerere/ui/sketch/SketchDialog.kt" to listOf(
                "RoundedCornerShape(24.dp)",
                "tonalElevation = 0.dp",
            ),
            "ui/src/main/java/me/rerere/ui/sketch/SketchTextInput.kt" to listOf(
                "RoundedCornerShape(24.dp)",
                "tonalElevation = 0.dp",
            ),
            "ui/src/main/java/me/rerere/ui/table/DataTable.kt" to listOf(
                "horizontalDividersOnly",
            ),
        )

        val forbiddenSnippets = mapOf(
            "app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt" to listOf(
                "ArrowExpand02",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt" to listOf(
                ".background(animatedContainerColor, CircleShape)",
            ),
            "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt" to listOf(
                "topFadingEdge",
            ),
        )

        val failures = mutableListOf<String>()

        requiredSnippets.forEach { (relPath, snippets) ->
            val file = repoRoot.resolve(relPath)
            if (!file.exists()) {
                failures += "Missing required file: $relPath"
                return@forEach
            }
            val text = file.readText()
            snippets.forEach { snippet ->
                if (snippet !in text) {
                    failures += "$relPath: missing required style anchor `$snippet`"
                }
            }
        }

        forbiddenSnippets.forEach { (relPath, snippets) ->
            val file = repoRoot.resolve(relPath)
            if (!file.exists()) {
                failures += "Missing file for forbidden check: $relPath"
                return@forEach
            }
            val text = file.readText()
            snippets.forEach { snippet ->
                if (snippet in text) {
                    failures += "$relPath: forbidden legacy snippet `$snippet` reappeared"
                }
            }
        }

        assertTrue(
            "Fork UI style anchor regressions detected:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private fun locateRepoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (dir != null) {
            if (
                dir.resolve("settings.gradle.kts").isFile &&
                dir.resolve("app/src/main/java/me/rerere/rikkahub/ui").isDirectory
            ) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not locate repository root from user.dir=${System.getProperty("user.dir")}")
    }

    private data class ImportRule(
        val prefix: String,
        val allowedRelativePaths: Set<String>,
        val replacement: String,
    )

    private class FqnRule(
        val symbol: String,
        val allowedRelativePaths: Set<String>,
        val replacement: String,
    ) {
        val pattern: Regex = Regex("""\bandroidx\.compose\.material3\.$symbol\b""")
    }

    private class CallRule(
        val symbol: String,
        val allowedRelativePaths: Set<String>,
        val replacement: String,
    ) {
        val pattern: Regex = Regex("""(?<![.\w])$symbol\s*\(""")
    }

    private companion object {
        val SWITCH_CALL_REGEX = Regex("""(?<![.\w])Switch\s*\(""")
    }
}

/**
 * 去除 Kotlin 源码中的注释与字符串字面量，避免注释说明或字符串内容触发误报。
 */
private fun stripCommentsAndStrings(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0
    val n = source.length
    while (i < n) {
        val c = source[i]
        val next = if (i + 1 < n) source[i + 1] else '\u0000'
        when {
            // 单行注释
            c == '/' && next == '/' -> {
                i += 2
                while (i < n && source[i] != '\n') i++
            }
            // 块注释（Kotlin 支持嵌套）
            c == '/' && next == '*' -> {
                i += 2
                var depth = 1
                while (i < n && depth > 0) {
                    if (source[i] == '/' && i + 1 < n && source[i + 1] == '*') {
                        depth++
                        i += 2
                    } else if (source[i] == '*' && i + 1 < n && source[i + 1] == '/') {
                        depth--
                        i += 2
                    } else {
                        if (source[i] == '\n') out.append('\n')
                        i++
                    }
                }
            }
            // 三引号字符串
            c == '"' && i + 2 < n && source[i + 1] == '"' && source[i + 2] == '"' -> {
                out.append("\"\"")
                i += 3
                while (i < n) {
                    if (source[i] == '"' && i + 2 < n && source[i + 1] == '"' && source[i + 2] == '"') {
                        i += 3
                        break
                    }
                    if (source[i] == '\n') out.append('\n')
                    i++
                }
            }
            // 普通字符串
            c == '"' -> {
                out.append("\"\"")
                i++
                while (i < n && source[i] != '"') {
                    if (source[i] == '\\' && i + 1 < n) {
                        i += 2
                    } else {
                        i++
                    }
                }
                if (i < n) i++
            }
            // 字符字面量
            c == '\'' -> {
                out.append("' '")
                i++
                while (i < n && source[i] != '\'') {
                    if (source[i] == '\\' && i + 1 < n) {
                        i += 2
                    } else {
                        i++
                    }
                }
                if (i < n) i++
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}
