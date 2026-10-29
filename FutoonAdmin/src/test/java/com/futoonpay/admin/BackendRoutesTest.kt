package com.futoonpay.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRoutesTest {
    private val svc = "/svc_0123456789abcdef0123456789abcdef/"

    @Test fun plainRoutesDropThePhpSuffixButKeepQueries() {
        assertEquals("order", BackendRoutes.route(svc, "order.php"))
        assertEquals("ajax_user?act=userList", BackendRoutes.route(svc, "ajax_user.php?act=userList"))
        assertEquals("uset?my=edit&uid=7", BackendRoutes.route(svc, "uset.php?my=edit&uid=7"))
        assertEquals("./", BackendRoutes.route(svc, "login.php"))
        assertEquals("./?logout", BackendRoutes.route(svc, "login.php?logout"))
    }

    @Test fun phpInsideAQueryValueIsNotTouched() {
        assertEquals("order?ref=a.php", BackendRoutes.route(svc, "order.php?ref=a.php"))
    }

    @Test fun legacyDirectoriesKeepTheirSuffix() {
        assertFalse(BackendRoutes.usesPlainRoutes("/admin/"))
        assertEquals("order.php", BackendRoutes.route("/admin/", "order.php"))
        assertEquals("login.php?logout", BackendRoutes.route("/admin/", "login.php?logout"))
        assertEquals("order.php", BackendRoutes.route("/", "order.php"))
    }

    @Test fun pastedPageAddressesReduceToTheDirectory() {
        assertEquals(svc, BackendRoutes.directory("/svc_0123456789abcdef0123456789abcdef/home"))
        assertEquals(svc, BackendRoutes.directory("/svc_0123456789abcdef0123456789abcdef/order/"))
        assertEquals(svc, BackendRoutes.directory(svc))
        assertEquals("/admin/home/", BackendRoutes.directory("/admin/home/"))
        assertTrue(BackendRoutes.usesPlainRoutes(svc))
    }
}
