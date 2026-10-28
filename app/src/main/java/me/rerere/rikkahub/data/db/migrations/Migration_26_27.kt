package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v26→v27：fork 与上游在此版本号分叉，合并后统一承接双方变更：
//   fork：承接上游同名版本号上的 workspaces.shell_compatibility_mode 列（Shell 兼容模式）
//   上游：ConversationEntity 新增 config 列（会话级 ConversationConfig 快照）
// 全部通过幂等的 ensureMergedSchema() 落地，兼容两类旧库：fork v26 与上游 v26。
val Migration_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureMergedSchema()
    }
}
