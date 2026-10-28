package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v29→v30：合并后的统一版本终点。
//   上游 lineage 的用户（v29）缺少 fork 侧的列（message_node.is_pinned / pinned_message_id、
//   ConversationEntity.conversation_tag_id / conversation_params 等），
//   fork lineage 的用户缺少上游侧的表/列，全部由幂等的 ensureMergedSchema() 补齐。
val Migration_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureMergedSchema()
    }
}
