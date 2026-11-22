package me.rerere.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

open class HttpException(
    message: String,
    val statusCode: Int? = null,
    val retryAfterMs: Long? = null,
) : RuntimeException(message) {
    val isRetryableStatus: Boolean
        get() = statusCode == 429 || statusCode == 408 || (statusCode != null && statusCode in 500..599)
}

fun Response.parseRetryAfterMs(): Long? {
    val raw = header("Retry-After")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val seconds = raw.toDoubleOrNull() ?: return null
    return (seconds * 1000.0).toLong().coerceIn(0L, 30_000L)
}

fun JsonElement.parseErrorDetail(
    statusCode: Int? = null,
    retryAfterMs: Long? = null,
): HttpException {
    return when (this) {
        is JsonObject -> {
            // 尝试获取常见的错误字段
            val errorFields = listOf("error", "detail", "message", "description")

            // 查找第一个存在的错误字段
            val foundField = errorFields.firstOrNull { this[it] != null }

            if (foundField != null) {
                // 递归解析找到的字段值
                this[foundField]!!.parseErrorDetail(statusCode = statusCode, retryAfterMs = retryAfterMs)
            } else {
                // 如果没有找到任何错误字段，序列化整个对象
                HttpException(
                    message = Json.encodeToString(JsonElement.serializer(), this),
                    statusCode = statusCode,
                    retryAfterMs = retryAfterMs,
                )
            }
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException(
                    message = "Unknown error: Empty JSON array",
                    statusCode = statusCode,
                    retryAfterMs = retryAfterMs,
                )
            } else {
                // 递归解析数组的第一个元素
                this.first().parseErrorDetail(statusCode = statusCode, retryAfterMs = retryAfterMs)
            }
        }

        is JsonPrimitive -> {
            // 对于基本类型，直接使用其内容
            HttpException(
                message = this.jsonPrimitive.content,
                statusCode = statusCode,
                retryAfterMs = retryAfterMs,
            )
        }

        else -> {
            // 其他情况，序列化整个元素
            HttpException(
                message = Json.encodeToString(JsonElement.serializer(), this),
                statusCode = statusCode,
                retryAfterMs = retryAfterMs,
            )
        }
    }
}
