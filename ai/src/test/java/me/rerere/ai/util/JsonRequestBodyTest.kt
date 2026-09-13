package me.rerere.ai.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRequestBodyTest {

    @Test
    fun `estimated size should count nested string content`() {
        val element = buildJsonObject {
            put("model", "gemini-3-flash")
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("data", "a".repeat(100))
                })
            })
        }

        // 结构字符只做粗略估算，这里主要保证字符串内容都被统计到
        assertTrue(element.estimatedJsonSize() >= 115L)
        assertEquals(3L, JsonPrimitive("abc").estimatedJsonSize())
        assertEquals(0L, JsonArray(emptyList()).estimatedJsonSize())
    }

    @Test
    fun `small body should keep in-memory encoding and known content length`() {
        val element = buildJsonObject {
            put("model", "gemini-3-flash")
            put("text", "hello")
        }

        val body = element.toJsonRequestBody()
        val expected = json.encodeToString(JsonElement.serializer(), element)

        assertEquals(expected.toByteArray().size.toLong(), body.contentLength())
        assertTrue(body.contentType().toString().startsWith("application/json"))
        val buffer = Buffer()
        body.writeTo(buffer)
        assertEquals(expected, buffer.readUtf8())
    }

    @Test
    fun `large body should stream json identical to in-memory encoding`() {
        // 模拟带图片 base64 的请求体：几十 MB 的 body 在 Android 上会把堆撑爆
        val element = buildJsonObject {
            put("model", "gemini-3-flash")
            put("data", "A".repeat(2_000_000))
            put("text", "中文 \"quoted\" \n \u0001 emoji 🚀")
        }

        val body = element.toJsonRequestBody()
        val expected = json.encodeToString(JsonElement.serializer(), element)

        // 流式写入也要能提前算出 Content-Length，避免依赖 chunked 传输
        assertEquals(expected.toByteArray().size.toLong(), body.contentLength())
        // writeTo 可能被重试/重定向调用多次，必须每次都产生同样的内容
        repeat(2) {
            val buffer = Buffer()
            body.writeTo(buffer)
            assertEquals(expected, buffer.readUtf8())
        }
    }
}
