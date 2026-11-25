package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendAddressTest {
    @Test fun preservesCustomAdminPathAndPort() {
        assertEquals("https://pay.example.com:8443/private-admin/", composeBackendAddress("https://pay.example.com:8443", "/private-admin"))
        assertEquals(BackendAddress("https://pay.example.com:8443", "/private-admin"), BackendAddress.fromUrl("https://pay.example.com:8443/private-admin/"))
    }
    @Test fun rejectsOriginPathsAndRedirectLikeDirectories() {
        for ((origin, path) in listOf("http://pay.example.com" to "/admin", "https://pay.example.com/admin" to "/admin", "https://pay.example.com" to "//other.example", "https://pay.example.com" to "/../admin", "https://pay.example.com" to "/admin?x=1")) {
            assertThrows(IllegalArgumentException::class.java) { composeBackendAddress(origin, path) }
        }
    }
    @Test fun supportsRootBackendWithoutHardcodingDirectory() {
        assertEquals("https://pay.example.com/", composeBackendAddress("https://pay.example.com", "/"))
    }
}
