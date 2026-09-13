package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/** 超过该大小的请求体不再记录：带图片的请求动辄几十 MB，复制一份就够把堆撑爆 */
private const val MAX_LOGGED_REQUEST_BODY_BYTES = 64 * 1024L

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val requestHeaders = request.headers.toMap()
        val requestBody = request.body?.let { body ->
            val contentLength = body.contentLength()
            if (contentLength < 0 || contentLength > MAX_LOGGED_REQUEST_BODY_BYTES) {
                // 日志列表会长期持有记录，大 body 只留个提示，避免 OOM
                val size = if (contentLength < 0) "unknown" else "$contentLength bytes"
                "<request body skipped ($size)>"
            } else {
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = request.url.toString(),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error
                )
            )
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        val responseHeaders = response.headers.toMap()

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = request.url.toString(),
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = responseHeaders,
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { name ->
            if (name.equals("Proxy-Authorization", ignoreCase = true)) {
                "██"
            } else {
                get(name) ?: ""
            }
        }
    }
}
