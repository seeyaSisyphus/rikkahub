package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// [FORK] v25→v26：fork 与上游在此版本号分叉，合并后统一承接双方变更：
//   fork：message_node 新增 pinned_message_id 列（固定到上下文锚定的具体分支消息 id），
//         旧数据 is_pinned=1 但 pinned_message_id 为 NULL 时，
//         由 ConversationRepository.loadMessageNodes 在读取时回退到 selectIndex 对应消息（向后兼容）
//   上游：新增 media_creation_session / node / record 三张媒体生成表
// 全部通过幂等的 ensureMergedSchema() 落地，兼容两条 lineage 的旧库。
val Migration_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureMergedSchema()
    }
}
