package me.rerere.ai.util

import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

private val JSON_MEDIA_TYPE: MediaType = "application/json".toMediaType()

/**
 * 请求体超过该长度（字符数）时不再拼成完整 String，而是直接流式写进连接。
 *
 * 带图片的请求动辄几十 MB：Android 上 String 是 UTF-16，
 * 再叠加 toRequestBody 的 UTF-8 副本，同时存在 2~3 份完整数据，
 * 图片一多就会 "Failed to allocate ... byte allocation" OOM。
 */
private const val IN_MEMORY_BODY_LIMIT = 1_000_000L

/** 流式编码时写入连接的缓冲区大小 */
private const val STREAM_BUFFER_SIZE = 64 * 1024

/**
 * 粗略估算 JSON 序列化后的长度。
 *
 * 只统计字符串内容，忽略转义与结构字符，因此估算值偏小，
 * 用于「是否需要流式编码」这类阈值判断已经足够。
 */
fun JsonElement.estimatedJsonSize(): Long = when (this) {
    is JsonObject -> entries.sumOf { (key, value) -> key.length + value.estimatedJsonSize() + 4L }
    is JsonArray -> sumOf { it.estimatedJsonSize() + 1L }
    is JsonPrimitive -> content.length.toLong()
}

/**
 * 构建 JSON 请求体。
 *
 * - 小请求体：照旧序列化成 String（保留 Content-Length，兼容性最好）
 * - 大请求体（例如带多张图片 base64 的长对话）：流式写入 socket（保留 Content-Length），
 *   避免在堆上同时保留 String 和 ByteArray 两份完整副本
 */
fun JsonElement.toJsonRequestBody(contentType: MediaType = JSON_MEDIA_TYPE): RequestBody =
    if (estimatedJsonSize() <= IN_MEMORY_BODY_LIMIT) {
        json.encodeToString(JsonElement.serializer(), this).toRequestBody(contentType)
    } else {
        JsonStreamingRequestBody(this, contentType)
    }

private class JsonStreamingRequestBody(
    private val element: JsonElement,
    private val mediaType: MediaType,
) : RequestBody() {
    /**
     * 先空跑一遍序列化数出字节数：这样仍然可以带上 Content-Length，
     * 避免个别第三方网关不认 chunked 传输，同时又不会把完整 body 放进内存。
     */
    private val encodedLength: Long by lazy { countEncodedBytes(element) }

    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = encodedLength

    @OptIn(ExperimentalSerializationApi::class)
    override fun writeTo(sink: BufferedSink) {
        val output = SinkOutputStream(sink)
        json.encodeToStream(JsonElement.serializer(), element, output)
        output.flush()
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun countEncodedBytes(element: JsonElement): Long {
    val counter = CountingOutputStream()
    json.encodeToStream(JsonElement.serializer(), element, counter)
    return counter.count
}

/** 只统计字节数、不保存内容，用于提前算出 Content-Length */
private class CountingOutputStream : OutputStream() {
    var count: Long = 0L
        private set

    override fun write(b: Int) {
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        count += len
    }
}

/** 把 BufferedSink 适配成 OutputStream，并做一层缓冲避免逐字节写入 */
private class SinkOutputStream(private val sink: BufferedSink) : OutputStream() {
    private val buffer = ByteArray(STREAM_BUFFER_SIZE)
    private var size = 0

    override fun write(b: Int) {
        if (size == buffer.size) flushBuffer()
        buffer[size++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len >= buffer.size) {
            flushBuffer()
            sink.write(b, off, len)
            return
        }
        if (size + len > buffer.size) flushBuffer()
        b.copyInto(destination = buffer, destinationOffset = size, startIndex = off, endIndex = off + len)
        size += len
    }

    override fun flush() {
        flushBuffer()
        sink.flush()
    }

    private fun flushBuffer() {
        if (size > 0) {
            sink.write(buffer, 0, size)
            size = 0
        }
    }
}