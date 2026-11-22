package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_33_34"

// 上游新增 workspaces.storage_mounts；同时补齐上游 v33 数据库缺少的 fork 字段。
val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 33 to 34 (merged schema and storage mounts)")
        DatabaseMigrationTracker.onMigrationStart(33, 34)
        try {
            db.ensureMergedSchema()
            Log.i(TAG, "migrate: migrate from 33 to 34 success")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
