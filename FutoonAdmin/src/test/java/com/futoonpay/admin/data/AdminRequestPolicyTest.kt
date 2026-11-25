package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdminRequestPolicyTest {
    private val policy = AdminRequestPolicy("https://fixture.example:8443/private-admin/")

    @Test fun keepsTheConfiguredOriginDirectoryAndEncodedQuery() {
        assertEquals("https://fixture.example:8443/private-admin/ajax.php?value=a%26b%252f", policy.resolveUrl("ajax.php?value=a%26b%252f"))
        assertEquals("https://fixture.example:8443/private-admin/login.php", policy.resolveUrl("/private-admin/login.php"))
        assertEquals("https://fixture.example:8443/private-admin/", policy.resolveUrl(""))
    }

    @Test fun rejectsOtherSchemesHostsPortsCredentialsAndDirectoryLookalikes() {
        listOf(
            "http://fixture.example:8443/private-admin/ajax.php",
            "https://other.example:8443/private-admin/ajax.php",
            "//other.example:8443/private-admin/ajax.php",
            "https://fixture.example/private-admin/ajax.php",
            "https://fixture.example:9443/private-admin/ajax.php",
            "https://user:secret@fixture.example:8443/private-admin/ajax.php",
            "ajax.php#fragment",
            "/private-admin-escape/ajax.php",
            "/private-admin",
            "../outside.php",
            "%2e%2e/outside.php",
            "/outside.php",
        ).forEach { path ->
            assertThrows(path, IllegalArgumentException::class.java) { policy.resolveUrl(path) }
        }
    }

    @Test fun rejectsPathsThatAProxyCouldDecodeIntoDifferentRoutes() {
        listOf(
            "%2f..%2foutside.php", "%5coutside.php", "%252e%252e/outside.php",
            "%252foutside.php", "nested/%00.php", "nested/%1f.php", "nested/%7f.php",
        ).forEach { path ->
            assertThrows(path, IllegalArgumentException::class.java) { policy.resolveUrl(path) }
        }
    }

    @Test fun validatesTheBaseBeforeAnyRequestsExist() {
        listOf(
            "http://fixture.example/private-admin/", "https://user:secret@fixture.example/private-admin/",
            "https://fixture.example/private-admin", "https://fixture.example/private-admin/?query=1",
            "https://fixture.example/private-admin/#fragment", "https://fixture.example/private%2fadmin/",
        ).forEach { base ->
            assertThrows(base, IllegalArgumentException::class.java) { AdminRequestPolicy(base) }
        }
    }

    @Test fun resolvesRedirectsRelativeToTheCurrentHopNotTheAdminRoot() {
        assertEquals(
            "https://fixture.example:8443/private-admin/pages/login.php",
            policy.resolveRedirectUrl("https://fixture.example:8443/private-admin/pages/entry.php", "login.php"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            policy.resolveRedirectUrl("https://fixture.example:8443/private-admin/pages/entry.php", "../../outside.php")
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.requireAllowedUrl("https://fixture.example:9443/private-admin/login.php")
        }
    }
}
