package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_34_35"

// v35 是两条 lineage 的统一终点，尤其负责升级已经处于上游 v34 的数据库。
val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 34 to 35 (merged schema)")
        DatabaseMigrationTracker.onMigrationStart(34, 35)
        try {
            db.ensureMergedSchema()
            Log.i(TAG, "migrate: migrate from 34 to 35 success")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
