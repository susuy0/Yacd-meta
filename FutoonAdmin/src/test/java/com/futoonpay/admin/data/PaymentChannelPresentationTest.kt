package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentChannelPresentationTest {
    private val table = NativeTable(
        title = "支付通道",
        headers = listOf("ID", "显示名称", "支付方式", "支付插件", "预付扣点", "支付范围", "状态"),
        rows = listOf(
            listOf("1", "九九支付", "支付宝", "jiujiupay", "0%", "不限", "已开启"),
            listOf("2", "测试通道", "支付宝", "futong", "4%", "1-5000", "已开启"),
            listOf("3", "epay测试", "微信支付", "epay", "1.5%", "不限", "已关闭"),
        ),
    )

    @Test
    fun mapsWebsiteColumnsIntoNativeChannelCards() {
        val items = table.toPaymentChannelItems()

        assertEquals("九九支付", items[0].name)
        assertEquals("jiujiupay", items[0].plugin)
        assertEquals("4%", items[1].rate)
        assertEquals("1-5000", items[1].paymentRange)
        assertEquals(PaymentChannelStatus.DISABLED, items[2].status)
    }

    @Test
    fun usesOfficialChannelTabAndPaymentTypeParameters() {
        assertEquals(
            "pay_channel.php?tab=assigned&type=1",
            paymentChannelPath("pay_channel.php?tab=all", PaymentChannelTab.RUNNING, "1"),
        )
        assertEquals(
            "pay_channel.php?tab=disabled",
            paymentChannelPath("pay_channel.php?tab=all&type=2", PaymentChannelTab.DISABLED, ""),
        )
        assertEquals(
            "pay_channel.php?page=2&tab=unassigned&type=3#list",
            paymentChannelPath("pay_channel.php?page=2#list", PaymentChannelTab.PENDING, "3"),
        )
    }

    @Test
    fun obtainsPaymentTypeFiltersFromWebsiteForm() {
        val document = NativeDocument(
            title = "支付通道",
            notices = emptyList(),
            tables = listOf(table),
            forms = listOf(
                NativeForm(
                    title = "新增支付通道",
                    action = "ajax_pay.php?act=saveChannel",
                    method = "POST",
                    fields = listOf(
                        NativeField(
                            name = "type",
                            label = "支付方式",
                            value = "0",
                            type = "select",
                            options = listOf(
                                NativeOption("0", "请选择支付方式"),
                                NativeOption("1", "支付宝"),
                                NativeOption("2", "微信支付"),
                                NativeOption("3", "QQ钱包"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("" to "全部支付方式", "1" to "支付宝", "2" to "微信支付", "3" to "QQ钱包"),
            document.paymentChannelTypeFilters().map { it.value to it.label },
        )
    }

    @Test
    fun sortsChannelsByRateThenId() {
        val sorted = sortPaymentChannelItems(
            items = table.toPaymentChannelItems(),
            primary = PaymentChannelSortDimension.RATE,
            primaryAscending = false,
            secondary = PaymentChannelSortDimension.ID,
            secondaryAscending = true,
        )

        assertEquals(listOf("2", "3", "1"), sorted.map { it.id })
    }
}
