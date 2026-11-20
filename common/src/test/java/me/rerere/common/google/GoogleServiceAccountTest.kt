package me.rerere.common.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleServiceAccountTest {
    private val validJson = """
        {
          "type": "service_account",
          "project_id": "my-project",
          "private_key_id": "abc123",
          "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----\n",
          "client_email": "tts@my-project.iam.gserviceaccount.com",
          "client_id": "1234567890",
          "auth_uri": "https://accounts.google.com/o/oauth2/auth",
          "token_uri": "https://oauth2.googleapis.com/token"
        }
    """.trimIndent()

    @Test
    fun `parses all fields from a service account json`() {
        val account = GoogleServiceAccount.parse(validJson)

        assertEquals("my-project", account.projectId)
        assertEquals("tts@my-project.iam.gserviceaccount.com", account.clientEmail)
        assertEquals("1234567890", account.clientId)
        // JSON 里的 \n 转义要还原成真实换行，签名实现直接按 PEM 解析
        assertEquals(
            "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----",
            account.privateKey,
        )
    }

    @Test
    fun `derives project id from client email when project id is absent`() {
        val account = GoogleServiceAccount.parse(
            """
            {
              "client_email": "svc@derived-project.iam.gserviceaccount.com",
              "private_key": "-----BEGIN PRIVATE KEY-----\nMIIB\n-----END PRIVATE KEY-----\n"
            }
            """.trimIndent()
        )

        assertEquals("derived-project", account.projectId)
    }

    @Test
    fun `trims whitespace and strips utf8 bom`() {
        val account = GoogleServiceAccount.parse("\uFEFF  $validJson  \n")

        assertEquals("my-project", account.projectId)
        assertEquals("tts@my-project.iam.gserviceaccount.com", account.clientEmail)
    }

    @Test
    fun `rejects non service account credentials`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            GoogleServiceAccount.parse(
                """
                {
                  "type": "authorized_user",
                  "client_id": "x.apps.googleusercontent.com",
                  "client_secret": "secret",
                  "refresh_token": "token"
                }
                """.trimIndent()
            )
        }

        assertEquals(true, error.message!!.contains("authorized_user"))
    }

    @Test
    fun `rejects json without private key`() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleServiceAccount.parse("""{"type":"service_account","client_email":"a@b.iam.gserviceaccount.com"}""")
        }
    }

    @Test
    fun `rejects malformed json`() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleServiceAccount.parse("not json at all")
        }
    }

    @Test
    fun `rejects empty input`() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleServiceAccount.parse("   ")
        }
    }

    @Test
    fun `project id inference ignores non service account emails`() {
        assertNull(GoogleServiceAccount.projectIdFromClientEmail("user@gmail.com"))
        assertNull(GoogleServiceAccount.projectIdFromClientEmail("no-at-sign"))
        assertEquals(
            "demo",
            GoogleServiceAccount.projectIdFromClientEmail("robot@demo.iam.gserviceaccount.com"),
        )
    }
}
