package com.futoonpay.admin.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NativeDraftAndQrTest {
    private fun form(id: String = "12") = NativeForm("编辑", "save", "POST", listOf(NativeField("id", "ID", id, "hidden", readOnly = true), NativeField("name", "名称", "原名称", "text"), NativeField("api_key", "密钥", "", "password", sensitive = true)))
    @Test fun draftSurvivesNewStoreInstanceWithoutSavingCredentials() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = FormDraftStore(context); val edited = form().copy(fields = form().fields.map { if (it.name == "name") it.copy(value = "改过的名称") else if (it.name == "api_key") it.copy(value = "private") else it })
        store.save("https://one.example/admin/", edited)
        val restored = FormDraftStore(context).restore("https://one.example/admin/", form())
        assertEquals("改过的名称", restored.fields[1].value); assertEquals("", restored.fields[2].value)
        store.remove("https://one.example/admin/", edited)
        assertEquals("原名称", store.restore("https://one.example/admin/", form()).fields[1].value)
    }
    @Test fun draftsAreIsolatedByBackendAndRecordIdentity() {
        val store = FormDraftStore(ApplicationProvider.getApplicationContext<Application>())
        store.save("https://one.example/admin/", form().copy(fields = form().fields.map { if (it.name == "name") it.copy(value = "only one") else it }))
        assertEquals("原名称", store.restore("https://two.example/admin/", form()).fields[1].value)
        assertEquals("原名称", store.restore("https://one.example/admin/", form("99")).fields[1].value)
    }
    @Test fun generatedPaymentQrDecodesTheExactServerUrl() {
        val url = "https://pay.example/cashier?trade_no=T123&amount=0.01"
        val bitmap = paymentQr(url, 400)!!
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(url, decoded.text)
        assertNull(paymentQr("")); assertNull(paymentQr("x".repeat(3000)))
    }
    @Test fun configurationDraftsNeverCrossTelegramAccounts() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first = FormDraftStore(context, "tg-user-101")
        val second = FormDraftStore(context, "tg-user-202")
        first.save("https://one.example/admin/", form().copy(fields = form().fields.map { if (it.name == "name") it.copy(value = "private draft") else it }))
        assertEquals("private draft", first.restore("https://one.example/admin/", form()).fields[1].value)
        assertEquals("原名称", second.restore("https://one.example/admin/", form()).fields[1].value)
    }
}
