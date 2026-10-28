package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v31→v32：上游 v31→v32 只新增 workspaces.type / workspaces.ssh_config 两列
// （上游用 AutoMigration 实现，fork 统一改为手动迁移）。
// 这里再跑一次幂等的 ensureMergedSchema()，让来自任一分叉 lineage 的旧库都补齐完整 schema。
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureMergedSchema()
    }
}
