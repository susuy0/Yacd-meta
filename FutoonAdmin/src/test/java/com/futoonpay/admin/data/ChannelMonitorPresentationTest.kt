package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelMonitorPresentationTest {
    private val table = NativeTable(
        title = "监测记录",
        headers = listOf(
            "ID", "名称", "支付方式", "插件", "5 分钟成功率", "30 分钟成功率",
            "1 小时成功率", "24 小时成功率", "状态", "监测结果", "最近检测",
        ),
        rows = listOf(
            listOf("8", "通道 A", "支付宝", "alipay", "90%", "92%", "95%", "98%", "1", "超时", "2026-08-29 10:00:00"),
            listOf("3", "通道 B", "微信支付", "wechat", "0.2", "45%", "60%", "75%", "1", "失败", "2026-08-29 11:00:00"),
            listOf("5", "通道 C", "支付宝", "alipay", "99%", "99%", "99%", "99%", "1", "正常", "2026-08-29 12:00:00"),
        ),
    )

    @Test
    fun mapsHealthRatesAndGroupingDimensions() {
        val items = table.toChannelMonitorItems()

        assertEquals(MonitorHealth.ABNORMAL, items[0].health)
        assertEquals(20.0, items[1].fiveMinuteRate!!, 0.001)
        assertEquals(MonitorHealth.HEALTHY, items[2].health)
        assertEquals("支付宝", items[2].groupLabel(MonitorGroupDimension.PAYMENT_TYPE))
        assertEquals("alipay", items[2].groupLabel(MonitorGroupDimension.PLUGIN))
    }

    @Test
    fun appliesPrimaryThenSecondaryMonitorSorting() {
        val sorted = sortChannelMonitorItems(
            items = table.toChannelMonitorItems(),
            primary = MonitorSortDimension.HEALTH,
            primaryAscending = true,
            secondary = MonitorSortDimension.FIVE_MINUTES,
            secondaryAscending = true,
        )

        assertEquals(listOf("3", "8", "5"), sorted.map { it.id })
    }
}
