package me.rerere.common.android

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingTruncationTest {

    @After
    fun tearDown() {
        Logging.clear()
    }

    @Test
    fun `huge request body should be truncated before being kept in memory`() {
        val hugeBody = "A".repeat(2 * 1024 * 1024)
        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = "https://example.com",
                method = "POST",
                requestBody = hugeBody,
            )
        )

        val logged = Logging.getRequestLogs().single().requestBody.orEmpty()
        assertTrue("logged body should be truncated", logged.length < hugeBody.length)
        assertTrue("logged body should keep a readable prefix", logged.startsWith("A".repeat(64)))
    }

    @Test
    fun `small request body should be kept as is`() {
        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = "https://example.com",
                method = "POST",
                requestBody = "hello",
            )
        )

        assertEquals("hello", Logging.getRequestLogs().single().requestBody)
    }
}

