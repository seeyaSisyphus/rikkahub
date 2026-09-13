package me.rerere.ai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片体积预算的纯逻辑测试。
 * 真正的压缩依赖 Android Bitmap，这里只约束压缩阶梯的兜底行为：
 * 先降质量、后降分辨率，且小图不再降分辨率。
 */
class FileEncoderCompressionTest {

    @Test
    fun `encode attempts should start with original size and end with downscale fallback`() {
        assertTrue(IMAGE_ENCODE_ATTEMPTS.isNotEmpty())

        val first = IMAGE_ENCODE_ATTEMPTS.first()
        assertEquals(1, first.scale)
        assertTrue("first attempt should keep high quality", first.quality >= 80)

        val last = IMAGE_ENCODE_ATTEMPTS.last()
        assertTrue("last attempt must reduce resolution", last.scale > 1)
    }

    @Test
    fun `encode attempts should never magnify image or use invalid quality`() {
        IMAGE_ENCODE_ATTEMPTS.forEach { attempt ->
            assertTrue("scale must be >= 1", attempt.scale >= 1)
            assertTrue("quality must be in 0..100", attempt.quality in 0..100)
        }
        // 分辨率只会逐级下降，避免出现「重新放大」这种既费内存又无意义的操作
        IMAGE_ENCODE_ATTEMPTS
            .map { it.scale }
            .zipWithNext()
            .forEach { (previous, next) -> assertTrue(next >= previous) }
    }

    @Test
    fun `quality reductions should all come before resolution reductions`() {
        val firstDownscale = IMAGE_ENCODE_ATTEMPTS.indexOfFirst { it.scale > 1 }
        assertTrue("ladder must contain a downscale fallback", firstDownscale > 0)

        val qualityOnly = IMAGE_ENCODE_ATTEMPTS.take(firstDownscale)
        assertTrue(qualityOnly.all { it.scale == 1 })
        // 长截图等文字图片要能靠降质量塞进预算，因此质量档位要够深
        assertTrue("lowest quality-only rung is ${qualityOnly.minOf { it.quality }}",
            qualityOnly.minOf { it.quality } <= 45)
    }

    @Test
    fun `small images should not be downscaled`() {
        val attempts = encodeAttemptsFor(width = 400, height = 300)

        assertTrue("small images must keep their resolution", attempts.all { it.scale == 1 })
    }

    @Test
    fun `large images should keep every attempt including downscale`() {
        val attempts = encodeAttemptsFor(width = 4000, height = 3000)

        assertEquals(IMAGE_ENCODE_ATTEMPTS, attempts)
    }

    @Test
    fun `long screenshots should keep multiple quality-only rungs`() {
        // 1080x20000 的长截图经过 inSampleSize = 2 解码后是 540x10000
        val attempts = encodeAttemptsFor(width = 540, height = 10000)

        val qualityOnly = attempts.takeWhile { it.scale == 1 }
        assertTrue("quality-only rungs: ${qualityOnly.size}", qualityOnly.size >= 4)
    }
}
