package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.MessageNode
import kotlin.uuid.Uuid

/**
 * 从某条消息的「分享」入口进入多选模式时，默认勾选的节点 id：只选中该轮问答。
 *
 * - 从提问（USER）进入：该提问 + 紧随其后的回答（若是 ASSISTANT）；
 * - 从回答（ASSISTANT）进入：前一条提问（若是 USER）+ 该回答；
 * - 其它角色、会话首尾等无法配对的场景：只选中当前节点。
 *
 * 用户仍可在多选工具栏里「全选」或手动增删。
 */
internal fun defaultShareNodeIds(nodes: List<MessageNode>, index: Int): List<Uuid> {
    val node = nodes.getOrNull(index) ?: return emptyList()
    if (node.messages.isEmpty()) return emptyList()
    return when (node.currentMessage.role) {
        MessageRole.USER -> buildList {
            add(node.id)
            nodes.getOrNull(index + 1)
                ?.takeIf { it.messages.isNotEmpty() && it.currentMessage.role == MessageRole.ASSISTANT }
                ?.let { add(it.id) }
        }

        MessageRole.ASSISTANT -> buildList {
            nodes.getOrNull(index - 1)
                ?.takeIf { it.messages.isNotEmpty() && it.currentMessage.role == MessageRole.USER }
                ?.let { add(it.id) }
            add(node.id)
        }

        else -> listOf(node.id)
    }
}
