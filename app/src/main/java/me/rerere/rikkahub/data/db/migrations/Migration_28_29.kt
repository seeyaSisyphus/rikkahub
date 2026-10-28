package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v28→v29：上游新增 memory_consolidation 表并回填已有对话，
//   避免升级后第一次整理把整段历史都交给模型。
// 上游原为 AutoMigration(spec=Migration_28_29)，fork 合并后统一改为手动 Migration，
// 由幂等的 ensureMergedSchema() 建表与回填，兼容两条 lineage。
val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureMergedSchema()
    }
}
