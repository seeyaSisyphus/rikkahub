package me.rerere.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadyStateHitRateTest {
    @Test
    fun `ultra and high cache policies reach about 90 percent`() {
        val ultra = estimateSteadyStateHitRate(64_000.0, 12_800.0, 2_000.0)
        val high = estimateSteadyStateHitRate(64_000.0, 22_400.0, 2_000.0)
        assertTrue("ultra=$ultra", ultra >= 0.9)
        assertTrue("high=$high", high >= 0.9)
    }

    @Test
    fun `lower keep ratio gives higher hit rate`() {
        val balanced = estimateSteadyStateHitRate(64_000.0, 32_000.0, 2_000.0)
        val memory = estimateSteadyStateHitRate(64_000.0, 44_800.0, 2_000.0)
        assertTrue(balanced > memory)
    }

    @Test
    fun `invalid inputs return zero`() {
        assertEquals(0.0, estimateSteadyStateHitRate(0.0, 0.0, 2_000.0), 0.0)
        assertEquals(0.0, estimateSteadyStateHitRate(10.0, 20.0, 1.0), 0.0)
    }
}
