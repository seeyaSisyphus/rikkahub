package me.rerere.ai.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import me.rerere.ai.util.HttpException
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 请求模型失败时的重试策略：最多重试 [maxRetries] 次，等待时间从 [initialDelay] 开始每次翻倍。
 *
 * 默认重试 [IOException] 以及可恢复的限流/服务端状态码（429 / 408 / 5xx）。
 */
data class RetryPolicy(
    val maxRetries: Int,
    val initialDelay: Duration = 1.seconds,
    val retryOn: (Throwable) -> Boolean = {
        it is IOException || (it is HttpException && it.isRetryableStatus)
    },
) {
    companion object {
        val NONE = RetryPolicy(maxRetries = 0)

        /** 网络自动重试：最多 3 次，间隔 1s、2s、4s */
        val NETWORK = RetryPolicy(maxRetries = 3)
    }
}

/** 第 [retry] 次重试（共 [maxRetries] 次）将在 [delay] 后开始，[cause] 是上一次失败的原因 */
data class RetryAttempt(
    val cause: Throwable,
    val retry: Int,
    val maxRetries: Int,
    val delay: Duration,
)

/**
 * 按 [policy] 执行 [block]：失败且允许重试时先调用 [onRetry]，等待后再执行一次。
 */
suspend fun <T> withRetry(
    policy: RetryPolicy,
    onRetry: suspend (RetryAttempt) -> Unit = {},
    block: suspend () -> T,
): T {
    var retries = 0
    while (true) {
        try {
            return block()
        } catch (error: Throwable) {
            // 用户主动停止生成时，底层连接也可能以 IOException("canceled") 收尾；
            // 先检查协程状态，确保取消不会被当作网络波动重新拉起。
            currentCoroutineContext().ensureActive()
            if (error is CancellationException || retries >= policy.maxRetries || !policy.retryOn(error)) {
                throw error
            }
            retries++
            val expBackoff = policy.initialDelay * (1 shl (retries - 1))
            val backoff = if (error is HttpException) {
                val minFor429 = if (error.statusCode == 429) maxOf(expBackoff, 2.seconds) else expBackoff
                val headerDelay = error.retryAfterMs?.milliseconds ?: Duration.ZERO
                maxOf(minFor429, headerDelay).coerceAtMost(30.seconds)
            } else {
                expBackoff
            }
            onRetry(RetryAttempt(error, retries, policy.maxRetries, backoff))
            delay(backoff)
        }
    }
}
