package me.rerere.rikkahub.data.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

internal val partTypeMapping = mapOf(
    "Text" to "text",
    "UIMessagePart.Text" to "text",
    "me.rerere.ai.ui.UIMessagePart.Text" to "text",
    "Image" to "image",
    "UIMessagePart.Image" to "image",
    "me.rerere.ai.ui.UIMessagePart.Image" to "image",
    "Video" to "video",
    "UIMessagePart.Video" to "video",
    "me.rerere.ai.ui.UIMessagePart.Video" to "video",
    "Audio" to "audio",
    "UIMessagePart.Audio" to "audio",
    "me.rerere.ai.ui.UIMessagePart.Audio" to "audio",
    "Document" to "document",
    "UIMessagePart.Document" to "document",
    "me.rerere.ai.ui.UIMessagePart.Document" to "document",
    "Reasoning" to "reasoning",
    "UIMessagePart.Reasoning" to "reasoning",
    "me.rerere.ai.ui.UIMessagePart.Reasoning" to "reasoning",
    "Search" to "search",
    "UIMessagePart.Search" to "search",
    "me.rerere.ai.ui.UIMessagePart.Search" to "search",
    "ToolCall" to "tool_call",
    "UIMessagePart.ToolCall" to "tool_call",
    "me.rerere.ai.ui.UIMessagePart.ToolCall" to "tool_call",
    "ToolResult" to "tool_result",
    "UIMessagePart.ToolResult" to "tool_result",
    "me.rerere.ai.ui.UIMessagePart.ToolResult" to "tool_result",
    "Tool" to "tool",
    "UIMessagePart.Tool" to "tool",
    "me.rerere.ai.ui.UIMessagePart.Tool" to "tool",
)

internal fun migrateMessagesJson(messagesJson: String): String {
    return runCatching {
        val element = JsonInstant.parseToJsonElement(messagesJson)
        val migrated = migrateMessagesElement(element)
        if (migrated == element) messagesJson else JsonInstant.encodeToString(migrated)
    }.getOrElse { messagesJson }
}

internal fun migrateMessagesElement(element: JsonElement): JsonElement {
    val rootArray = element as? JsonArray ?: return element
    val migratedArray = JsonArray(
        rootArray.map { message ->
            val messageObject = message as? JsonObject ?: return@map message
            val partsElement = messageObject["parts"] as? JsonArray ?: return@map message
            val migratedParts = migratePartsArray(partsElement)
            if (migratedParts == partsElement) {
                message
            } else {
                JsonObject(messageObject.toMutableMap().apply {
                    put("parts", migratedParts)
                })
            }
        }
    )
    return if (migratedArray == rootArray) element else migratedArray
}

internal fun migratePartsArray(partsElement: JsonArray): JsonArray {
    return JsonArray(
        partsElement.map { part ->
            val partObject = part as? JsonObject ?: return@map part
            val typeValue = partObject["type"]?.jsonPrimitiveOrNull?.contentOrNull
            val mappedType = typeValue?.let { partTypeMapping[it] } ?: typeValue

            var updatedPart: JsonElement = part
            if (mappedType != null && mappedType != typeValue) {
                updatedPart = JsonObject(partObject.toMutableMap().apply {
                    put("type", JsonPrimitive(mappedType))
                })
            }

            val updatedObject = updatedPart as? JsonObject ?: return@map updatedPart
            val outputElement = updatedObject["output"] as? JsonArray ?: return@map updatedPart
            val migratedOutput = migratePartsArray(outputElement)
            if (migratedOutput == outputElement) {
                updatedPart
            } else {
                JsonObject(updatedObject.toMutableMap().apply {
                    put("output", migratedOutput)
                })
            }
        }
    )
}

/**
 * [FORK] fork 与上游在 v25 之后版本号分叉（fork 25→26→27，上游 25→…→29），
 * 合并后统一推进到 v32。以下操作全部为幂等实现（IF NOT EXISTS / try-catch 忽略 duplicate），
 * 在 25→26 到 31→32 的每一步中都会执行，保证无论用户来自哪条 lineage、哪个版本，
 * 抵达 v32 时 schema 完整一致。
 */
internal fun SupportSQLiteDatabase.ensureMergedSchema() {
    // ---- 分叉列（双方各自新增，互相缺失）----
    // 上游会话级系统提示词（fork 曾在 v22→v23 删除该列，合并后恢复）
    addColumnIfMissing("ALTER TABLE ConversationEntity ADD COLUMN `custom_system_prompt` TEXT NOT NULL DEFAULT ''")
    // fork 会话标签分组
    addColumnIfMissing("ALTER TABLE ConversationEntity ADD COLUMN `conversation_tag_id` TEXT DEFAULT ''")
    // fork 会话级参数快照
    addColumnIfMissing("ALTER TABLE ConversationEntity ADD COLUMN `conversation_params` TEXT NOT NULL DEFAULT '{}'")
    // 上游 v26→v27 会话配置快照（ConversationConfig）
    addColumnIfMissing("ALTER TABLE ConversationEntity ADD COLUMN `config` TEXT NOT NULL DEFAULT ''")
    // fork 固定到上下文
    addColumnIfMissing("ALTER TABLE message_node ADD COLUMN `is_pinned` INTEGER NOT NULL DEFAULT 0")
    addColumnIfMissing("ALTER TABLE message_node ADD COLUMN `pinned_message_id` TEXT")
    // 上游 workspaces Shell 兼容模式（fork 在 26→27 承接过）
    addColumnIfMissing("ALTER TABLE workspaces ADD COLUMN `shell_compatibility_mode` INTEGER NOT NULL DEFAULT 0")
    // 上游 v31→v32 工作区类型（LOCAL / SSH）与 SSH 连接配置
    addColumnIfMissing("ALTER TABLE workspaces ADD COLUMN `type` TEXT NOT NULL DEFAULT 'LOCAL'")
    addColumnIfMissing("ALTER TABLE workspaces ADD COLUMN `ssh_config` TEXT")

    // ---- 上游 v25→v26 媒体生成表 ----
    execSQL(
        "CREATE TABLE IF NOT EXISTS `media_creation_session` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`draft` TEXT NOT NULL, `create_at` INTEGER NOT NULL, `update_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    )
    execSQL(
        "CREATE TABLE IF NOT EXISTS `media_creation_node` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, " +
            "`selected_record_id` TEXT NOT NULL, `create_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`session_id`) REFERENCES `media_creation_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    execSQL("CREATE INDEX IF NOT EXISTS `index_media_creation_node_session_id` ON `media_creation_node` (`session_id`)")
    execSQL(
        "CREATE TABLE IF NOT EXISTS `media_creation_record` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, " +
            "`node_id` TEXT NOT NULL, `provider_id` TEXT NOT NULL, `provider_name` TEXT NOT NULL, " +
            "`model_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `prompt` TEXT NOT NULL, `params` TEXT NOT NULL, " +
            "`inputs` TEXT NOT NULL, `status` TEXT NOT NULL, `task_id` TEXT, `error` TEXT, `outputs` TEXT NOT NULL, " +
            "`create_at` INTEGER NOT NULL, `update_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`session_id`) REFERENCES `media_creation_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`node_id`) REFERENCES `media_creation_node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    execSQL("CREATE INDEX IF NOT EXISTS `index_media_creation_record_session_id` ON `media_creation_record` (`session_id`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_media_creation_record_node_id` ON `media_creation_record` (`node_id`)")

    // ---- 上游 v27→v28 记忆文件表（数据迁移由 Migration_27_28 处理）----
    execSQL(
        "CREATE TABLE IF NOT EXISTS `memory_file` (`assistant_id` TEXT NOT NULL, `path` TEXT NOT NULL, " +
            "`content` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`assistant_id`, `path`))"
    )

    // ---- 上游 v28→v29 记忆整理表 + 回填（INSERT OR IGNORE 幂等）----
    execSQL(
        "CREATE TABLE IF NOT EXISTS `memory_consolidation` (`conversation_id` TEXT NOT NULL, " +
            "`consolidated_at` INTEGER NOT NULL, PRIMARY KEY(`conversation_id`), " +
            "FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    execSQL(
        "INSERT OR IGNORE INTO memory_consolidation (conversation_id, consolidated_at) " +
            "SELECT id, update_at FROM ConversationEntity"
    )

    // ---- 上游 v29→v30 翻译历史表（fork 合并后统一到 v32 时补齐）----
    execSQL(
        "CREATE TABLE IF NOT EXISTS `translation_history` (`id` TEXT NOT NULL, `source_text` TEXT NOT NULL, " +
            "`translated_text` TEXT NOT NULL, `target_language` TEXT NOT NULL, `model_id` TEXT NOT NULL, " +
            "`create_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    )
    execSQL(
        "CREATE INDEX IF NOT EXISTS `index_translation_history_create_at` " +
            "ON `translation_history` (`create_at`)"
    )

    // ---- 上游 v30→v31 用量流水表（回填由 Migration_30_31 处理）----
    execSQL(
        "CREATE TABLE IF NOT EXISTS `usage_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`created_at` INTEGER NOT NULL, `source` TEXT NOT NULL, `provider_id` TEXT, `model_id` TEXT, " +
            "`model_name` TEXT, `assistant_id` TEXT, `conversation_id` TEXT, " +
            "`prompt_tokens` INTEGER NOT NULL, `completion_tokens` INTEGER NOT NULL, " +
            "`cached_tokens` INTEGER NOT NULL)"
    )
    execSQL("CREATE INDEX IF NOT EXISTS `index_usage_record_created_at` ON `usage_record` (`created_at`)")
}

private fun SupportSQLiteDatabase.addColumnIfMissing(sql: String) {
    try {
        execSQL(sql)
    } catch (_: Exception) {
        // 列已存在（另一条 lineage 的用户），忽略
    }
}
