package me.rerere.rikkahub.utils

import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownInline
import me.rerere.bettertext.markdown.parseMarkdown

/**
 * 移除字符串中的 Markdown 格式，得到用于朗读的纯文本
 *
 * 复用渲染用的 [parseMarkdown]，哪些内容算 HTML 标签、链接或代码都和界面上显示的一致，
 * 不会把 `x<y && y>0` 这种正文当成标签删掉。代码、公式和图片不朗读。
 * 输出是已解码的纯文本，不要再对结果调用一次，否则 `&lt;tag&gt;` 解码出的文字会被当成标签。
 * @return 移除Markdown格式后的纯文本
 */
fun String.stripMarkdown(): String {
    return buildString { appendBlocks(parseMarkdown(this@stripMarkdown).blocks) }
        // 去掉代码等内容后会留下空行和连续空格
        .replace(Regex("[ \t]*\n\\s*"), "\n")
        .replace(Regex("[ \t]{2,}"), " ")
        .trim()
}

/**
 * 复制消息时使用的纯文本转换：去掉 Markdown 标记，
 * 但保留段落、换行、列表和代码内容。
 *
 * 与 [stripMarkdown] 的差异：
 * - 段落之间保留空行，而不是压成单行；
 * - 代码块和行内代码保留原文（只去掉围栏和反引号），复制出来仍然是可运行的代码；
 * - 列表保留为普通项目符号/编号，不保留 `-`、`*` 等 Markdown 标记。
 */
fun String.stripMarkdownForCopy(): String {
    return renderCopyBlocks(parseMarkdown(this).blocks).trim()
}

private fun renderCopyBlocks(blocks: List<MarkdownBlock>): String {
    return blocks.mapNotNull { block -> renderCopyBlock(block) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun renderCopyBlock(block: MarkdownBlock): String? = when (block) {
    is MarkdownBlock.Paragraph -> renderCopyInlines(block.content)
    is MarkdownBlock.Heading -> renderCopyInlines(block.content)
    is MarkdownBlock.Quote -> renderCopyBlocks(block.children)
    is MarkdownBlock.ListBlock -> renderCopyList(block)
    is MarkdownBlock.Table -> renderCopyTable(block)
    is MarkdownBlock.Details -> listOf(
        renderCopyInlines(block.summary),
        renderCopyBlocks(block.children),
    ).filter { it.isNotBlank() }.joinToString("\n\n")
    is MarkdownBlock.Container -> renderCopyBlocks(block.children)
    // 复制时保留代码正文，只去掉 ``` 围栏和语言标记
    is MarkdownBlock.CodeBlock -> block.code.trim('\n')
    is MarkdownBlock.Divider -> null
}

private fun renderCopyList(block: MarkdownBlock.ListBlock): String {
    return block.items.mapIndexed { index, item ->
        val marker = if (block.ordered) "${block.start + index}. " else "• "
        val body = renderCopyBlocks(item.children).trim()
        buildString {
            body.lineSequence().forEachIndexed { lineIndex, line ->
                if (lineIndex == 0) {
                    append(marker)
                } else {
                    append('\n')
                    // 多行列表项按标记宽度对齐，嵌套列表也保持缩进
                    if (line.isNotEmpty()) append(" ".repeat(marker.length))
                }
                append(line)
            }
        }
    }.joinToString("\n")
}

private fun renderCopyTable(block: MarkdownBlock.Table): String {
    return (listOf(block.header) + block.rows).joinToString("\n") { row ->
        row.joinToString(" | ") { cell -> renderCopyInlines(cell.content).trim() }
    }
}

private fun renderCopyInlines(nodes: List<MarkdownInline>): String = buildString {
    for (node in nodes) {
        when (node) {
            is MarkdownInline.Text -> append(node.text)
            is MarkdownInline.Styled -> append(renderCopyInlines(node.children))
            is MarkdownInline.Link -> {
                val text = renderCopyInlines(node.children)
                // 搜索引用在正文里写作 [citation,域名](引用 id)，复制时只保留域名
                append(
                    when {
                        text.startsWith("citation,") -> text.substringAfter(',')
                        text.isNotBlank() -> text
                        else -> node.destination
                    }
                )
            }

            is MarkdownInline.Code -> append(node.code)
            is MarkdownInline.Image -> append(node.alt.orEmpty())
            is MarkdownInline.Math -> append(node.formula)
        }
    }
}

/**
 * 为 TTS 朗读场景剥离 Markdown。
 *
 * 与 stripMarkdown() 的差异:
 * - 围栏代码块: 替换为"包含一段 {语言} 代码,约 N 行"提示,而非空字符串,
 *   避免 AI 回复中的高亮代码块在 TTS 时被完全略过。
 * - 行内代码:  去掉反引号,保留原字符,让 TTS 按词读出。
 * - 其他剥离规则与 stripMarkdown() 保持一致。
 */
fun String.stripMarkdownForTts(): String {
    var result = this
    // 1) 围栏代码块: 替换为语言提示
    result = Regex("```(\\w*)\\n?([\\s\\S]*?)```").replace(result) { match ->
        val lang = match.groupValues[1].ifBlank { "plaintext" }
        val body = match.groupValues[2]
        val lineCount = body.lines().count { it.isNotBlank() }.coerceAtLeast(1)
        "\n[包含一段 $lang 代码,约 $lineCount 行]\n"
    }
    // 2) 行内代码: 去掉反引号,保留内容
    result = result.replace(Regex("`([^`\\n]+?)`"), "$1")
    // 3) 复用现有剥离规则
    return result
        // 移除图片和链接,但保留其文本内容
        .replace(Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)"), "$1")
        // 移除加粗和斜体 (先处理两个星号的)
        .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+?)\\*"), "$1")
        // 移除下划线
        .replace(Regex("__([^_]+?)__"), "$1")
        .replace(Regex("_([^_]+?)_"), "$1")
        // 移除删除线
        .replace(Regex("~~([^~]+?)~~"), "$1")
        // 移除标题标记 (多行模式)
        .replace(Regex("(?m)^#+\\s*"), "")
        // 移除列表标记 (多行模式)
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        // 移除引用标记 (多行模式)
        .replace(Regex("(?m)^>\\s*"), "")
        // 移除水平分割线
        .replace(Regex("(?m)^(\\s*[-*_]){3,}\\s*$"), "")
        // 将多个换行符压缩,以保留段落
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        // 4) 转义 XML/SSML 保留字符，防止 Google Cloud TTS 将其当 SSML 标签解析触发 400
        // 必须先转义 & 再转义其余字符，否则会对已转义内容二次转义
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun StringBuilder.appendBlocks(blocks: List<MarkdownBlock>) {
    for (block in blocks) {
        when (block) {
            is MarkdownBlock.Paragraph -> appendInlines(block.content)
            is MarkdownBlock.Heading -> appendInlines(block.content)
            is MarkdownBlock.Quote -> appendBlocks(block.children)
            is MarkdownBlock.ListBlock -> block.items.forEach { appendBlocks(it.children) }
            is MarkdownBlock.Table -> (listOf(block.header) + block.rows).forEach { row ->
                row.forEach { cell ->
                    appendInlines(cell.content)
                    append(' ')
                }
                append('\n')
            }

            is MarkdownBlock.Details -> {
                appendInlines(block.summary)
                append('\n')
                appendBlocks(block.children)
            }

            is MarkdownBlock.Container -> appendBlocks(block.children)
            is MarkdownBlock.CodeBlock, MarkdownBlock.Divider -> Unit
        }
        append('\n')
    }
}

private fun StringBuilder.appendInlines(nodes: List<MarkdownInline>) {
    for (node in nodes) {
        when (node) {
            is MarkdownInline.Text -> append(node.text)
            is MarkdownInline.Styled -> appendInlines(node.children)
            is MarkdownInline.Link -> appendInlines(node.children)
            is MarkdownInline.Code, is MarkdownInline.Image, is MarkdownInline.Math -> Unit
        }
    }
}

fun String.extractThinkingTitle(): String? {
    // 匹配行内任意 **...** 片段（非贪婪，不跨行）
    val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
    val lines = this.lines()

    // 从后往前查找最后一个包含加粗文本的行，取该行最后一个 **...** 段作为标题
    // 不使用行首行尾锚点，避免同一行多个 **段** 被全部捕获成一个超长标题
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()
        val matches = boldPattern.findAll(line).toList()
        if (matches.isNotEmpty()) {
            return matches.last().groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }

    return null
}
