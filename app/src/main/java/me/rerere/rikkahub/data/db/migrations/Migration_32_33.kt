package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_32_33"

// [FORK] 同时补齐两条 lineage 的字段，并把 message_node 索引升级为
// (conversation_id, node_index)，避免长会话分页读取时重复排序。
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 32 to 33 (merged schema and message_node index)")
        DatabaseMigrationTracker.onMigrationStart(32, 33)
        try {
            db.ensureMergedSchema()
            Log.i(TAG, "migrate: migrate from 32 to 33 success")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
