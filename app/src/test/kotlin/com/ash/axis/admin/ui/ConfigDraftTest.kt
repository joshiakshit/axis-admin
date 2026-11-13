package com.ash.axis.admin.ui

import com.ash.axis.admin.data.RemoteConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigDraftTest {
    private val base = RemoteConfig(appVersion = "3.1.3", minSupportedVersionCode = 10, latestVersionCode = 16, updatedAt = "revision")

    @Test
    fun `review describes every high impact change together`() {
        val draft = ConfigDraft.from(base).copy(minimum = "12", killSwitch = true, prefixes = "024")
        val review = draft.review(base)
        assertTrue(review.contains("below 12 will be blocked"))
        assertTrue(review.contains("BLOCK ALL STUDENT ACCESS"))
        assertTrue(review.contains("Automatic approval: 024"))
        assertEquals("revision", draft.patch(base).expectedUpdatedAt)
    }

    @Test
    fun `invalid release policy and unsafe download links cannot be saved`() {
        val invalid = ConfigDraft.from(base).copy(minimum = "20", updateUrl = "javascript:alert(1)", prefixes = "024,,025")
        assertTrue(invalid.errors().containsAll(setOf("latestCode", "updateUrl", "prefixes")))
        assertTrue(ConfigDraft.from(base).copy(minimum = "-1").errors().contains("minimum"))
    }
}
