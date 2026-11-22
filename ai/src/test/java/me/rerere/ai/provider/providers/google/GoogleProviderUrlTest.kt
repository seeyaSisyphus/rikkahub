package me.rerere.ai.provider.providers.google

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.HttpException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class GoogleProviderUrlTest {
    @Test
    fun `default vertex location matches upstream`() {
        assertEquals("us-central1", ProviderSetting.Google().location)
    }

    @Test
    fun `buildGoogleApiUrl keeps upstream host and v1 for every vertex location`() {
        val standard = ProviderSetting.Google(
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            vertexAI = false,
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:streamGenerateContent",
            standard.buildGoogleApiUrl("models/gemini-3.1-pro-preview:streamGenerateContent").toString(),
        )

        val express = ProviderSetting.Google(vertexAI = true, useServiceAccount = false)
        assertEquals(
            "https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-4-hydrogen:streamGenerateContent",
            express.buildGoogleApiUrl("publishers/google/models/gemini-4-hydrogen:streamGenerateContent").toString(),
        )

        val vertexBase = ProviderSetting.Google(
            vertexAI = true,
            useServiceAccount = true,
            projectId = "my-proj",
        )
        listOf("us-central1", "us", "eu", "global").forEach { location ->
            assertEquals(
                "https://aiplatform.googleapis.com/v1/projects/my-proj/locations/$location/" +
                    "publishers/google/models/gemini-4-hydrogen:streamGenerateContent",
                vertexBase.copy(location = location)
                    .buildGoogleApiUrl("publishers/google/models/gemini-4-hydrogen:streamGenerateContent")
                    .toString(),
            )
        }
    }

    @Test
    fun `generateText and streamText use upstream url and retain cached tokens`() = runTest {
        val seenUrls = mutableListOf<String>()
        val successBody = successBody("ok-sync")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                seenUrls += url
                when {
                    url == "https://oauth2.googleapis.com/token" -> jsonResponse(
                        req,
                        200,
                        """{"access_token":"tok-1","expires_in":3600}""",
                    )

                    url.contains(":streamGenerateContent") -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            ("data: ${successBody("ok-stream")}\n\ndata: [DONE]\n\n")
                                .toResponseBody("text/event-stream".toMediaType())
                        )
                        .build()

                    else -> jsonResponse(
                        req,
                        200,
                        successBody,
                    )
                }
            }
            .build()

        val setting = testVertexSetting()
        val provider = GoogleProvider(client)
        val params = TextGenerationParams(model = Model(modelId = "gemini-4-hydrogen"))
        val messages = listOf(UIMessage.user("hello"))

        val syncResult = provider.generateText(setting, messages, params)
        assertEquals("ok-sync", (syncResult.message.parts.single() as UIMessagePart.Text).text)
        assertEquals(4100, syncResult.usage?.cachedTokens)

        val streamChunks = provider.streamText(setting, messages, params).toList()
        val textDelta = streamChunks.filterIsInstance<StreamChunk.TextDelta>().single()
        val streamUsage = streamChunks.filterIsInstance<StreamChunk.Usage>().single().usage
        assertEquals("ok-stream", textDelta.text)
        assertEquals(4100, streamUsage.cachedTokens)

        val prefix = "https://aiplatform.googleapis.com/v1/projects/my-proj/locations/us-central1/" +
            "publishers/google/models/gemini-4-hydrogen:"
        assertTrue(seenUrls.any { it == "${prefix}generateContent" })
        assertTrue(seenUrls.any { it == "${prefix}streamGenerateContent?alt=sse" })
        assertTrue(seenUrls.none { it.contains("rep.googleapis.com") || it.contains("/v1beta1/") })
    }

    @Test
    fun `single-region 404 is surfaced without redirecting to global`() = runTest {
        val seenUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                seenUrls += url
                if (url == "https://oauth2.googleapis.com/token") {
                    jsonResponse(req, 200, """{"access_token":"tok-1","expires_in":3600}""")
                } else {
                    jsonResponse(req, 404, """{"error":{"code":404,"message":"not found"}}""")
                }
            }
            .build()

        var thrown: HttpException? = null
        try {
            GoogleProvider(client).generateText(
                testVertexSetting(),
                listOf(UIMessage.user("hello")),
                TextGenerationParams(model = Model(modelId = "bad-model")),
            )
        } catch (e: HttpException) {
            thrown = e
        }

        assertEquals(404, thrown?.statusCode)
        assertEquals(2, seenUrls.size)
        assertTrue(seenUrls.none { it.contains("/locations/global/") })
    }

    private fun successBody(text: String): String =
        """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"$text"}]},
            "finishReason":"STOP"}],
            "usageMetadata":{"promptTokenCount":5000,"cachedContentTokenCount":4100,
            "candidatesTokenCount":2,"totalTokenCount":5002}}
        """.trimIndent().lines().joinToString("") { it.trim() }

    private fun testVertexSetting() = ProviderSetting.Google(
        vertexAI = true,
        useServiceAccount = true,
        projectId = "my-proj",
        location = "us-central1",
        serviceAccountEmail = "sa@my-proj.iam.gserviceaccount.com",
        privateKey = testPkcs8Pem(),
    )

    private fun jsonResponse(req: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun testPkcs8Pem(): String {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(kp.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
    }
}
