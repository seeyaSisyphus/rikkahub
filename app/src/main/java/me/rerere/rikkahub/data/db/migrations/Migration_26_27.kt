package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v26→v27：承接上游 v24→v25 的 workspaces.shell_compatibility_mode 列
//   上游在同名版本号上新增了该列（Shell 兼容模式），fork 的版本号已推进到 26，
//   因此以 26→27 的手动 Migration 承接同一变更。
//   兼容两类旧库：fork v26（无该列）与上游 v25（已有该列），列已存在时忽略 duplicate 错误。
val Migration_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE workspaces ADD COLUMN `shell_compatibility_mode` INTEGER NOT NULL DEFAULT 0"
            )
        } catch (_: Exception) {}
    }
}