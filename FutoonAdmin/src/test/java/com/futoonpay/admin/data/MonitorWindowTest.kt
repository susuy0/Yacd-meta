package com.futoonpay.admin.data

import org.junit.Assert.*
import org.junit.Test

class MonitorWindowTest {
    private val item = NativeTable("监测", listOf("ID", "名称", "状态", "5 分钟成功率", "success_rate_6hour", "total_count_5min", "success_count_5min"),
        listOf(listOf("12", "微信通道", "1", "92%", "86", "50", "46"))).toChannelMonitorItems().first()
    @Test fun keepsWindowsIndependentAndMissingCountsUnknown() {
        assertEquals(92.0, item.metrics(MonitorWindow.FIVE_MINUTES).rate!!, 0.001)
        assertEquals(50, item.metrics(MonitorWindow.FIVE_MINUTES).total)
        assertEquals(46, item.metrics(MonitorWindow.FIVE_MINUTES).successful)
        assertNull(item.metrics(MonitorWindow.FIVE_MINUTES).processing)
        assertEquals(86.0, item.metrics(MonitorWindow.SIX_HOURS).rate!!, 0.001)
        assertNull(item.metrics(MonitorWindow.TWELVE_HOURS).rate)
        assertNull(item.metrics(MonitorWindow.TWELVE_HOURS).total)
    }
    @Test fun enabledStatusDoesNotDependOnRateOrDetection() {
        assertEquals("启用", item.enabledLabel())
        assertEquals("停用", item.copy(status = "0", fiveMinuteRate = 100.0, result = "通过").enabledLabel())
    }
}
