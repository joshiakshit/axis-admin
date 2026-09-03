package com.ash.axis.admin.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminModelsTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    @Suppress("LongMethod")
    fun `decodes session users health and config responses`() {
        val session =
            json.decodeFromString<AxisSession>(
                """
                {
                  "status":"approved",
                  "role":"admin",
                  "admno":"21000",
                  "name":"Admin",
                  "sessionToken":"token"
                }
                """.trimIndent(),
            )
        val users =
            json.decodeFromString<UsersResponse>(
                """
                {
                  "users":[{
                    "admno":"21001",
                    "name":"User",
                    "email":"u@example.com",
                    "status":"pending",
                    "role":"user",
                    "session_count":2
                  }]
                }
                """.trimIndent(),
            )
        val health =
            json.decodeFromString<HealthResponse>(
                """
                {
                  "service":"axis-backend",
                  "users":1,
                  "pending":1,
                  "approved":0,
                  "banned":0,
                  "apkUploaded":false,
                  "metrics":{"launch":4}
                }
                """.trimIndent(),
            )
        val config =
            json.decodeFromString<RemoteConfig>(
                """
                {
                  "appVersion":"3.0.3",
                  "minSupportedVersionCode":1,
                  "latestVersionCode":2,
                  "latestVersionName":"2.0",
                  "updateUrl":"https://example.com",
                  "killSwitch":false,
                  "message":"",
                  "notice":"Hi",
                  "autoApprovePrefix":"024",
                  "updatedAt":"now"
                }
                """.trimIndent(),
            )

        assertEquals("admin", session.role)
        assertEquals(2, users.users.single().sessionCount)
        assertEquals(4, health.metrics["launch"])
        assertEquals("Hi", config.notice)
    }

    @Test
    fun `config patch sends only changed fields`() {
        val encoded = json.encodeToString(ConfigPatch(notice = "Maintenance"))

        assertTrue(encoded.contains("\"notice\":\"Maintenance\""))
        assertFalse(encoded.contains("killSwitch"))
        assertFalse(encoded.contains("minSupportedVersionCode"))
        assertFalse(encoded.contains("authToken"))
    }
}
