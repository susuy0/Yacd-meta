package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdminEndpointStoreTest {
    @Test
    fun addsHttpsAndPreservesAdminDirectory() {
        assertEquals(
            "https://example.com/private-admin/",
            normalizeAdminBaseUrl("example.com/private-admin").toString(),
        )
    }

    @Test
    fun removesQueryAndFragmentFromBackendRoot() {
        assertEquals(
            "https://example.com/admin/",
            normalizeAdminBaseUrl("https://example.com/admin?tab=all#top").toString(),
        )
    }

    @Test
    fun rejectsInsecureOrCredentialBearingUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeAdminBaseUrl("http://example.com/admin/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeAdminBaseUrl("https://admin:secret@example.com/admin/")
        }
    }
}
