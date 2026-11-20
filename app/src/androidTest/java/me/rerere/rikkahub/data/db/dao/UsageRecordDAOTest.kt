package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.UsageRecordEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class UsageRecordDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: UsageRecordDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).openHelperFactory(RequerySQLiteOpenHelperFactory()).build()
        dao = database.usageRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun requestsAreCountedPerLocalDayForTheSourceSinceTheStart() = runBlocking {
        insert("2026-08-11T23:00:00", "CHAT")
        insert("2026-08-12T00:30:00", "CHAT")
        insert("2026-08-12T23:59:00", "CHAT")
        insert("2026-08-12T12:00:00", "TITLE")
        insert("2026-08-13T08:00:00", "CHAT")

        val counts = dao.getRequestCountPerDay("CHAT", epochMillis("2026-08-12T00:00:00"))

        assertEquals(mapOf("2026-08-12" to 2, "2026-08-13" to 1), counts.associate { it.day to it.count })
    }

    @Test
    fun totalsCountRequestsAndDistinctConversations() = runBlocking {
        insert("2026-08-12T10:00:00", "CHAT", conversationId = "a", promptTokens = 100, cachedTokens = 50)
        insert("2026-08-12T11:00:00", "CHAT", conversationId = "a", promptTokens = 200)
        insert("2026-08-12T12:00:00", "TITLE", conversationId = "b", promptTokens = 300)
        // 标题、翻译这类请求没有会话，计次数但不计会话数
        insert("2026-08-12T13:00:00", "TRANSLATION", conversationId = null, promptTokens = 400)
        insert("2026-08-11T23:00:00", "CHAT", conversationId = "c", promptTokens = 500)

        val totals = dao.getTotals(epochMillis("2026-08-12T00:00:00"))

        assertEquals(1000, totals.promptTokens)
        assertEquals(4, totals.completionTokens)
        assertEquals(50, totals.cachedTokens)
        assertEquals(4, totals.requests)
        assertEquals(2, totals.conversations)
    }

    @Test
    fun modelsAreRolledUpByIdWithRequestCounts() = runBlocking {
        insert(
            "2026-08-12T10:00:00",
            "CHAT",
            modelId = "m1",
            modelName = "model-one",
            promptTokens = 100,
            completionTokens = 10,
        )
        insert(
            "2026-08-12T11:00:00",
            "CHAT",
            modelId = "m1",
            modelName = "model-one",
            promptTokens = 200,
            completionTokens = 20,
        )
        insert(
            "2026-08-12T12:00:00",
            "CHAT",
            modelId = "m2",
            modelName = "model-two",
            promptTokens = 500,
            completionTokens = 50,
        )

        val models = dao.getTotalsByModel(0)

        assertEquals(listOf("m2", "m1"), models.map { it.modelId })
        assertEquals(listOf(1, 2), models.map { it.requests })
        assertEquals(listOf(550L, 330L), models.map { it.promptTokens + it.completionTokens })
    }

    private suspend fun insert(
        localDateTime: String,
        source: String,
        conversationId: String? = null,
        modelId: String? = null,
        modelName: String? = null,
        promptTokens: Int = 10,
        completionTokens: Int = 1,
        cachedTokens: Int = 0,
    ) {
        dao.insert(
            UsageRecordEntity(
                createdAt = epochMillis(localDateTime),
                source = source,
                providerId = null,
                modelId = modelId,
                modelName = modelName,
                assistantId = null,
                conversationId = conversationId,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                cachedTokens = cachedTokens,
            )
        )
    }

    private fun epochMillis(localDateTime: String): Long =
        LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
