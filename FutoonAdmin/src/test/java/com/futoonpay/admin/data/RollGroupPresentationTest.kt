package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RollGroupPresentationTest {
    private val table = NativeTable(
        title = "轮询组",
        headers = listOf("ID", "轮询组名称", "支付方式", "轮询模式", "通道路由", "状态"),
        rows = listOf(
            listOf("8", "备用组", "支付宝", "权重", "通道 2 / 通道 3", "正常"),
            listOf("3", "主路由", "微信支付", "顺序", "通道 1", "关闭"),
            listOf("5", "低权重", "支付宝", "权重", "通道 4", "未启用"),
        ),
    )

    @Test
    fun mapsStatusRoutesAndGroupingDimensions() {
        val items = table.toRollGroupItems()

        assertEquals(RollGroupStatus.ENABLED, items[0].status)
        assertEquals(RollGroupStatus.DISABLED, items[1].status)
        assertEquals(RollGroupStatus.DISABLED, items[2].status)
        assertEquals("通道 1", items[1].routeSummary)
        assertEquals("权重", items[2].groupLabel(RollGroupDimension.MODE))
    }

    @Test
    fun appliesPrimaryThenSecondaryRollSorting() {
        val sorted = sortRollGroupItems(
            items = table.toRollGroupItems(),
            primary = RollSortDimension.STATUS,
            primaryAscending = true,
            secondary = RollSortDimension.ID,
            secondaryAscending = true,
        )

        assertEquals(listOf("3", "5", "8"), sorted.map { it.id })
    }

    @Test
    fun mapsAppStatusFiltersToWebsiteTabs() {
        assertEquals("pay_roll.php?tab=assigned", rollGroupTabPath("pay_roll.php?tab=all", RollGroupTab.RUNNING))
        assertEquals("pay_roll.php?tab=disabled", rollGroupTabPath("pay_roll.php", RollGroupTab.DISABLED))
        assertEquals("pay_roll.php?page=2&tab=unassigned", rollGroupTabPath("pay_roll.php?page=2", RollGroupTab.PENDING))
        assertEquals("pay_roll.php?tab=assigned#groups", rollGroupTabPath("pay_roll.php#groups", RollGroupTab.RUNNING))
        assertEquals("已启用（未分配）", RollGroupTab.PENDING.websiteLabel)
    }
}
