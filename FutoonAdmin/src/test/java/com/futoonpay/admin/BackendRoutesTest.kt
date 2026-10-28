package com.futoonpay.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRoutesTest {
    private val svc = "/svc_802d1b0ad0420cb3ca03b6c3285e83bb/"

    @Test fun plainRoutesDropThePhpSuffixButKeepQueries() {
        assertEquals("order", BackendRoutes.route(svc, "order.php"))
        assertEquals("ajax_user?act=userList", BackendRoutes.route(svc, "ajax_user.php?act=userList"))
        assertEquals("uset?my=edit&uid=7", BackendRoutes.route(svc, "uset.php?my=edit&uid=7"))
        assertEquals("login?logout", BackendRoutes.route(svc, "login.php?logout"))
    }

    @Test fun phpInsideAQueryValueIsNotTouched() {
        assertEquals("order?ref=a.php", BackendRoutes.route(svc, "order.php?ref=a.php"))
    }

    @Test fun legacyDirectoriesKeepTheirSuffix() {
        assertFalse(BackendRoutes.usesPlainRoutes("/admin/"))
        assertEquals("order.php", BackendRoutes.route("/admin/", "order.php"))
        assertEquals("order.php", BackendRoutes.route("/", "order.php"))
    }

    @Test fun pastedPageAddressesReduceToTheDirectory() {
        assertEquals(svc, BackendRoutes.directory("/svc_802d1b0ad0420cb3ca03b6c3285e83bb/home"))
        assertEquals(svc, BackendRoutes.directory("/svc_802d1b0ad0420cb3ca03b6c3285e83bb/order/"))
        assertEquals(svc, BackendRoutes.directory(svc))
        assertEquals("/admin/home/", BackendRoutes.directory("/admin/home/"))
        assertTrue(BackendRoutes.usesPlainRoutes(svc))
    }
}
