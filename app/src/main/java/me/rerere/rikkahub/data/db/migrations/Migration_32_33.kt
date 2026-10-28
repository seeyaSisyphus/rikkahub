package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v32→v33：合并后的统一版本终点。
//   上游 lineage 停在 v32 的用户缺少 fork 侧列/表（会话标签、对话参数、固定到上下文、Battle 等），
//   全部由幂等的 ensureMergedSchema() 补齐；fork lineage 的旧库在这一步已经齐全，等于空操作。
//   合并后的版本号必须高于上游，否则上游 v32 的库会因版本相同而绕过迁移、schema 校验失败。
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureMergedSchema()
    }
}
