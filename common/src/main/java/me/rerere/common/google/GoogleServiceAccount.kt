package me.rerere.common.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Google Cloud 服务账号（Service Account）凭据。
 *
 * 模型配置里的 Vertex AI、Gemini Vertex TTS / Vertex Cloud TTS、Chirp 3 ASR
 * 都对接 Google Cloud，鉴权方式完全一致：用服务账号 JSON 里的 client_email +
 * private_key 现场签 JWT 换 access token，project_id 用来拼 Vertex 端点。
 *
 * 所以“导入服务账号 JSON 并自动解析出配置”的能力集中在这里，各页面只负责把解析结果
 * 填回自己的 ProviderSetting 字段，避免每接一个 Vertex 服务就重新写一遍解析逻辑。
 */
data class GoogleServiceAccount(
    /** 服务账号所属的 Google Cloud 项目 ID，JSON 里缺省时由 client_email 推导。 */
    val projectId: String,
    /** 形如 xxx@project-id.iam.gserviceaccount.com，JWT 的 issuer。 */
    val clientEmail: String,
    /** PKCS#8 PEM 私钥（已按 JSON 转义还原成真实换行）。 */
    val privateKey: String,
    val clientId: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val SERVICE_ACCOUNT_EMAIL_SUFFIX = ".iam.gserviceaccount.com"

        /**
         * 解析服务账号 JSON 文本（Google Cloud 控制台下载的 key 文件内容）。
         *
         * 只做字段提取与必要的校验，返回的 [privateKey] 保持 JSON 里的真实换行，
         * 下游签名实现会再统一去掉 PEM 头尾与空白字符。
         *
         * @throws IllegalArgumentException 不是 JSON、缺少 client_email / private_key，
         *         或凭据类型不是 service_account 时抛出，消息可直接展示给用户。
         */
        fun parse(rawText: String): GoogleServiceAccount {
            // 部分文件管理器导出的文本会带 UTF-8 BOM，先去掉再解析
            val text = rawText.removePrefix("\uFEFF").trim()
            require(text.isNotEmpty()) { "the file is empty" }

            val obj: JsonObject = try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("not a valid service account JSON (${e.message})", e)
            }

            // 只接受 JsonString，避免把数字/布尔值误当配置
            fun stringField(key: String): String? = (obj[key] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val type = stringField("type")
            if (type != null && type != "service_account") {
                throw IllegalArgumentException(
                    "unsupported credential type \"$type\", please import a service account key JSON"
                )
            }

            val clientEmail = stringField("client_email")
                ?: throw IllegalArgumentException("missing client_email in service account JSON")
            val privateKey = stringField("private_key")
                ?: throw IllegalArgumentException("missing private_key in service account JSON")

            return GoogleServiceAccount(
                // project_id 缺省时用 client_email 的域名推导，Chirp 3 / Gemini Vertex 都需要它来拼端点
                projectId = stringField("project_id")
                    ?: projectIdFromClientEmail(clientEmail)
                    ?: "",
                clientEmail = clientEmail,
                privateKey = privateKey,
                clientId = stringField("client_id") ?: "",
            )
        }

        /**
         * 从 `xxx@project-id.iam.gserviceaccount.com` 推导项目 ID，不是标准服务账号邮箱时返回 null。
         */
        fun projectIdFromClientEmail(clientEmail: String): String? {
            val domain = clientEmail.substringAfter('@', missingDelimiterValue = "")
            if (!domain.endsWith(SERVICE_ACCOUNT_EMAIL_SUFFIX)) return null
            return domain.removeSuffix(SERVICE_ACCOUNT_EMAIL_SUFFIX).ifEmpty { null }
        }
    }
}
