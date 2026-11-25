package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RollRoutePresentationTest {
    @Test fun largeWeightsAreNotSilentlyClampedToAnotherRouteRatio() {
        val form = NativeForm("路由", "saveRollInfo", "POST", listOf(NativeField("list[0][channel]", "通道", "12", "select"), NativeField("list[0][weight]", "权重", "100", "number"), NativeField("list[1][channel]", "通道", "15", "select"), NativeField("list[1][weight]", "权重", "300", "number")))
        assertEquals(0.25f, form.toRollRouteVisualItems()[0].fraction, 0.001f)
        assertEquals(0.75f, form.toRollRouteVisualItems()[1].fraction, 0.001f)
    }
    @Test
    fun parsesOfficialRouteFieldsAndNormalizesWeights() {
        val form = NativeForm(
            title = "配置通道",
            action = "ajax_pay.php?act=saveRollInfo",
            method = "POST",
            fields = listOf(
                NativeField(
                    name = "list[0][channel]",
                    label = "通道",
                    value = "8",
                    type = "select",
                    options = listOf(NativeOption("8", "九九支付 - 0%")),
                ),
                NativeField("list[0][weight]", "权重", "1", "number"),
                NativeField("list[1][channel]", "通道", "9", "select"),
                NativeField("list[1][weight]", "权重", "3", "number"),
            ),
        )

        val rows = form.toRollRouteVisualItems()

        assertEquals(listOf("8", "9"), rows.map { it.channelId })
        assertEquals("九九支付 - 0%", rows.first().channelLabel)
        assertEquals(0.25f, rows.first().fraction, 0.001f)
        assertEquals(0.75f, rows.last().fraction, 0.001f)
    }

    @Test
    fun ignoresEmptyPlaceholderRoutes() {
        val form = NativeForm(
            "配置通道", "saveRollInfo", "POST",
            listOf(
                NativeField("list[0][channel]", "通道", "", "select"),
                NativeField("list[0][weight]", "权重", "1", "number"),
            ),
        )

        assertEquals(emptyList<RollRouteVisualItem>(), form.toRollRouteVisualItems())
    }
}
