package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import me.rerere.common.android.Logging
import java.util.concurrent.ConcurrentHashMap

/**
 * 隐式缓存排查日志：写入应用内“日志”页（文本日志，标签 CacheDiag）。
 *
 * - 请求侧：对比同一模型相邻两次请求，看请求头部（系统提示/工具/配置）是否变化，
 *   以及历史条目从第几条开始与上一次不一致（前缀不一致就拿不到缓存）。
 * - 响应侧：打印服务端返回的原始 usage，区分“字段没返回”和“返回了 0”。
 */
internal object CacheDiagnostics {
    private const val TAG = "CacheDiag"
    private const val SNIPPET_CHARS = 120

    private class Snapshot(val headerHash: String, val items: List<String>, val itemTexts: List<String>)

    private val lastRequests = ConcurrentHashMap<String, Snapshot>()

    /**
     * @param itemsKey 历史条目所在字段：generateContent 为 `contents`，Interactions 为 `input`
     * @param endpoint 不含 query 参数的目标域名与路径，方便确认是否命中正确区域端点
     */
    fun logRequest(
        api: String,
        model: String,
        requestBody: JsonObject,
        itemsKey: String,
        endpoint: String? = null,
    ) {
        if (!Logging.isRequestLoggingEnabled()) return
        runCatching {
            val itemTexts = (requestBody[itemsKey] as? JsonArray).orEmpty().map { it.toString() }
            val items = itemTexts.map { hash(it) }
            val headerHash = hash(JsonObject(requestBody.filterKeys { it != itemsKey }).toString())
            val previous = lastRequests.put(model, Snapshot(headerHash, items, itemTexts))

            val message = buildString {
                append("[$api]")
                if (!endpoint.isNullOrBlank()) append(" endpoint=$endpoint")
                append(" model=$model items=${items.size} header=$headerHash")
                if (previous == null) {
                    append(" (首个请求，无对比)")
                    return@buildString
                }
                append(if (previous.headerHash == headerHash) " (头部与上次相同)" else " (头部已变化: 系统提示/工具/配置有改动)")
                var common = 0
                while (common < items.size && common < previous.items.size && items[common] == previous.items[common]) {
                    common++
                }
                append(" 与上次相同的前缀条目=$common/${previous.items.size}")
                if (common < previous.items.size) {
                    append(" ；第 ${common + 1} 条开始不一致")
                    append("\n上次: ${previous.itemTexts[common].snippet()}")
                    append("\n本次: ${itemTexts.getOrNull(common)?.snippet() ?: "(无)"}")
                } else {
                    append(" ；上次的全部历史都是本次前缀（仍受模型缓存门槛与新增 token 规则影响）")
                }
            }
            Logging.log(TAG, message)
        }
    }

    fun logUsage(api: String, usage: JsonObject?, maxCachedSeen: Int = 0) {
        if (!Logging.isRequestLoggingEnabled()) return
        val cachedKeys = listOf("cachedContentTokenCount", "total_cached_tokens")
        val hasCachedField = usage?.keys?.any { it in cachedKeys } == true || maxCachedSeen > 0
        val extra = if (maxCachedSeen > 0 && usage?.keys?.any { it in cachedKeys } != true) {
            " maxCachedSeenInChunks=$maxCachedSeen"
        } else {
            ""
        }
        Logging.log(
            TAG,
            "[$api] usage=${usage?.toString() ?: "(服务端未返回 usage)"}$extra " +
                if (hasCachedField) "(含缓存字段)" else "(无缓存字段：服务端未报告缓存命中)"
        )
    }

    private fun String.snippet(): String =
        if (length <= SNIPPET_CHARS) this else take(SNIPPET_CHARS) + "…"

    private fun hash(text: String): String = Integer.toHexString(text.hashCode()).padStart(8, '0')
}
