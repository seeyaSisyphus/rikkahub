package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ModelListTest {
    @Test
    fun `findSelectedModelTarget returns null when currentModel is null or absent`() {
        val m1 = Model(modelId = "m1", displayName = "m1")
        val p1 = ProviderSetting.OpenAI(name = "P1", models = listOf(m1))

        assertNull(
            findSelectedModelTarget(
                currentModel = null,
                favoriteModelIds = emptyList(),
                providers = listOf(p1),
                typeFilteredModelsByProvider = mapOf(p1.id to p1.models),
                collapsedProviderIds = emptySet(),
            )
        )
        assertNull(
            findSelectedModelTarget(
                currentModel = Uuid.random(),
                favoriteModelIds = emptyList(),
                providers = listOf(p1),
                typeFilteredModelsByProvider = mapOf(p1.id to p1.models),
                collapsedProviderIds = emptySet(),
            )
        )
    }

    @Test
    fun `findSelectedModelTarget prioritizes favorites and skips collapsed providers`() {
        val fav = Model(modelId = "fav", displayName = "fav")
        val m1 = Model(modelId = "m1", displayName = "m1")
        val m2 = Model(modelId = "m2", displayName = "m2")
        val m3 = Model(modelId = "m3", displayName = "m3")

        val p1 = ProviderSetting.OpenAI(name = "P1", models = listOf(m1, m2))
        val p2 = ProviderSetting.OpenAI(name = "P2", models = listOf(m3, fav))
        val byProvider = mapOf(p1.id to p1.models, p2.id to p2.models)

        // 收藏项优先定位到收藏区（index 0 是 favorite header，index 1 是第一个收藏项）
        val favTarget = findSelectedModelTarget(
            currentModel = fav.id,
            favoriteModelIds = listOf(fav.id),
            providers = listOf(p1, p2),
            typeFilteredModelsByProvider = byProvider,
            collapsedProviderIds = emptySet(),
        )
        assertEquals(SelectedModelTarget(itemIndex = 1, headerKey = "header:favorite"), favTarget)

        // P1 折叠时，P2 的首个模型位于：favorite header(1) + fav(1) + P1 header(1) + P2 header(1) = 4
        val p2FirstTarget = findSelectedModelTarget(
            currentModel = m3.id,
            favoriteModelIds = listOf(fav.id),
            providers = listOf(p1, p2),
            typeFilteredModelsByProvider = byProvider,
            collapsedProviderIds = setOf(p1.id.toString()),
        )
        assertEquals(SelectedModelTarget(itemIndex = 4, headerKey = "header:${p2.id}"), p2FirstTarget)

        // 若选中模型位于被折叠的 P1 内部，目标索引回落到 P1 header（index 2）
        val collapsedTarget = findSelectedModelTarget(
            currentModel = m2.id,
            favoriteModelIds = listOf(fav.id),
            providers = listOf(p1, p2),
            typeFilteredModelsByProvider = byProvider,
            collapsedProviderIds = setOf(p1.id.toString()),
        )
        assertEquals(SelectedModelTarget(itemIndex = 2, headerKey = "header:${p1.id}"), collapsedTarget)
    }

    @Test
    fun `isItemFullyVisibleInInitialViewport avoids scrolling when item fits first screen`() {
        // 视口总高 1000（viewportEndOffset=988, afterContentPadding=12 -> visibleBottom=976）
        assertTrue(
            isItemFullyVisibleInInitialViewport(
                itemOffset = 300,
                itemSize = 180,
                viewportEndOffset = 988,
                afterContentPadding = 12,
            )
        )
        // 刚好贴合 bottom 边界时仍算完整可见
        assertTrue(
            isItemFullyVisibleInInitialViewport(
                itemOffset = 796,
                itemSize = 180,
                viewportEndOffset = 988,
                afterContentPadding = 12,
            )
        )
        // 底部被截断 1px 时需要滚动
        assertFalse(
            isItemFullyVisibleInInitialViewport(
                itemOffset = 797,
                itemSize = 180,
                viewportEndOffset = 988,
                afterContentPadding = 12,
            )
        )
        // 顶部被裁切时也需要滚动
        assertFalse(
            isItemFullyVisibleInInitialViewport(
                itemOffset = -1,
                itemSize = 180,
                viewportEndOffset = 988,
                afterContentPadding = 12,
            )
        )
    }

    @Test
    fun `computeContextScrollDelta places item in upper-middle viewport preserving context above`() {
        // contentHeight = 1000 - 0 = 1000, targetSize = 200 -> available = 800
        // desiredTop = (800 * 0.42).roundToInt() = 336
        // 若目标原本在 900（底部被截断），需要向下滚动 900 - 336 = 564px
        val deltaFromBottom = computeContextScrollDelta(
            targetOffset = 900,
            targetSize = 200,
            viewportEndOffset = 1000,
            afterContentPadding = 0,
            headerClearancePx = 100,
        )
        assertEquals(564f, deltaFromBottom, 0.001f)

        // 若 pre-scroll 把目标放在靠近顶部的 150，返回负增量（-186px）将其下移到 336，露出上方 1~2 个模型
        val deltaFromTop = computeContextScrollDelta(
            targetOffset = 150,
            targetSize = 200,
            viewportEndOffset = 1000,
            afterContentPadding = 0,
            headerClearancePx = 100,
        )
        assertEquals(-186f, deltaFromTop, 0.001f)
    }

    @Test
    fun `computeContextScrollDelta respects sticky header clearance on compact viewports`() {
        // 小视口 contentHeight = 320, targetSize = 180 -> available = 140
        // 比例位置 140 * 0.42 = 59 < headerClearancePx(100)，因此被抬到至少 100 避开吸顶标题
        val delta = computeContextScrollDelta(
            targetOffset = 60,
            targetSize = 180,
            viewportEndOffset = 320,
            afterContentPadding = 0,
            headerClearancePx = 100,
        )
        assertEquals(-40f, delta, 0.001f)
    }
}
