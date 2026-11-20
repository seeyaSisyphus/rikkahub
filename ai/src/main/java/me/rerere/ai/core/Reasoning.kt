package me.rerere.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReasoningLevel(
    val budgetTokens: Int,
    val effort: String
) {
    @SerialName("off")
    OFF(0, "none"),

    @SerialName("auto")
    AUTO(-1, "auto"),

    @SerialName("low")
    LOW(1_000, "low"),

    @SerialName("medium")
    MEDIUM(2_000, "medium"),

    @SerialName("high")
    HIGH(8_000, "high"),

    @SerialName("xhigh")
    XHIGH(16_000, "xhigh"),

    @SerialName("max")
    MAX(32_000, "max");

    val isEnabled: Boolean
        get() = this != OFF

    /** 原始档位名称，不做本地化映射 */
    val rawLabel: String
        get() = name.lowercase()

    companion object {
        fun fromBudgetTokens(budgetTokens: Int?): ReasoningLevel {
            return entries.minByOrNull {
                kotlin.math.abs(
                    it.budgetTokens - (budgetTokens ?: AUTO.budgetTokens)
                )
            } ?: AUTO
        }

        fun fromEffort(effort: String): ReasoningLevel? = when (effort.trim().lowercase()) {
            "off", "none", "minimal", "disabled" -> OFF
            "auto" -> AUTO
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            "xhigh" -> XHIGH
            "max" -> MAX
            else -> null
        }
    }
}
