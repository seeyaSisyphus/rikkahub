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
