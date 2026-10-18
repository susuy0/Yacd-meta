package com.futoonpay.admin.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class PrototypeFeaturesTest {
    @Test fun proxyAndConfigurationSecretsAreMaskedInListPresentation() {
        assertEquals("••••••••", nativeVisibleValue("密码", "private"))
        assertEquals("http://user:••••••••@relay.example:8080", nativeVisibleValue("代理地址", "http://user:private@relay.example:8080"))
        assertEquals("wx_v3", nativeVisibleValue("支付插件", "wx_v3"))
    }
    @Test fun removingOneSearchConditionRetainsOtherServerConditions() {
        val original = OrderSearchFilters(value = "S100", merchantId = "18", channelId = "12", status = 1, startDate = "2026-10-01", endDate = "2026-10-08")
        val changed = original.withoutChip("channel")
        assertEquals("", changed.channelId); assertEquals("18", changed.merchantId)
        assertEquals("S100", changed.value); assertEquals(1, changed.status)
        assertEquals(original.startDate, changed.startDate)
    }
    @Test fun datePresetsCoverCalendarBoundariesAndKeepIds() {
        val original = OrderSearchFilters(channelId = "12", merchantId = "18")
        val result = original.withPreset(DatePreset.WEEK, LocalDate.of(2026, 1, 3))
        assertEquals("2025-12-28", result.startDate); assertEquals("2026-01-03", result.endDate)
        assertEquals("12", result.channelId)
        val yesterday = original.withPreset(DatePreset.YESTERDAY, LocalDate.of(2026, 1, 1))
        assertEquals("2025-12-31", yesterday.startDate); assertEquals(yesterday.startDate, yesterday.endDate)
    }
    @Test fun refundValidationRejectsOverpaymentZeroMalformedAndSubCent() {
        listOf("0", "-1", "501", "NaN", "Infinity", "1.001", "1e9999", "").forEach { assertFalse(it, validRefundAmount(it, "500.00")) }
        assertTrue(validRefundAmount("500.00", "500.00")); assertTrue(validRefundAmount("0.01", "500.00"))
    }
    @Test fun draftExcludesSecretsHiddenRecordIdentityAndReadonlyFields() {
        val form = NativeForm("编辑", "save", "POST", listOf(
            NativeField("name", "名称", "通道 A", "text"), NativeField("password", "密码", "secret", "password"),
            NativeField("account::api_key", "密钥", "secret", "text"), NativeField("id", "ID", "12", "hidden", readOnly = true)))
        assertEquals(mapOf("name" to "通道 A"), form.draftValues())
        assertEquals("secret", form.restoreDraft(mapOf("password" to "changed")).fields[1].value)
    }
    @Test fun outdatedSelectionIsNotRestoredIntoCurrentPluginOptions() {
        val form = NativeForm("编辑", "save", "POST", listOf(NativeField("plugin", "插件", "wx_v3", "select", listOf(NativeOption("wx_v3", "微信")))))
        assertEquals("wx_v3", form.restoreDraft(mapOf("plugin" to "removed_plugin")).fields.single().value)
    }
    @Test fun merchantFlowsAggregateMultipleRowsWithoutMixingCurrencies() {
        fun row(currency: String, amount: String) = JSONObject().put("uid", "18").put("currency", currency).put("total", amount)
        val flow = aggregateMerchantFlows(listOf("2026-10-08"), listOf("18"), listOf(listOf(row("CNY", "10"), row("CNY", "20"), row("USDT", "5"))))["18"]!!
        assertEquals(30.0, flow.total, 0.0)
        assertEquals(5.0, flow.currencyPoints.getValue("USDT").single().total, 0.0)
    }
    @Test fun unknownStatusesRemainUnknown() {
        assertEquals("状态 9", orderStatusLabel(9)); assertEquals("状态 9", settlementStatusLabel(9)); assertEquals("状态 9", domainAuditStatusLabel(9))
    }
}
