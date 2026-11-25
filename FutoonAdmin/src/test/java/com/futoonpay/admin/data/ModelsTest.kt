package com.futoonpay.admin.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun expandsStringEncodedAndObjectMappedRows() {
        val stringRows = JSONObject().put("data", "[{\"id\":7,\"name\":\"通道 A\"}]")
        assertEquals("通道 A", stringRows.rows().objectRows().single().text("name"))

        val mappedRows = JSONObject().put(
            "result",
            JSONObject().put("7", JSONObject().put("id", 7)).put("8", JSONObject().put("id", 8)),
        )
        assertEquals(listOf("7", "8"), mappedRows.rows().objectRows().map { it.text("id") })
    }

    @Test
    fun convertsArrayRowsUsingBootstrapFieldOrder() {
        val rows = JSONArray().put(JSONArray().put(12).put("轮询 A").put(1))
        val result = rows.objectRows(listOf("id", "name", "status")).single()

        assertEquals("12", result.text("id"))
        assertEquals("轮询 A", result.text("name"))
        assertEquals("1", result.text("status"))
    }

    @Test
    fun mapsCompleteOrderDetailFromOfficialPayload() {
        val payload = JSONObject().put(
            "data",
            JSONObject()
                .put("trade_no", "T100")
                .put("out_trade_no", "M100")
                .put("api_trade_no", "API100")
                .put("uid", "1001")
                .put("typename", "支付宝")
                .put("channel", "12")
                .put("channelname", "富通支付宝")
                .put("money", "128.00")
                .put("realmoney", "128.00")
                .put("getmoney", "122.88")
                .put("status", "1")
                .put("settle", "0")
                .put("notify", "2"),
        )

        val detail = payload.toOrderDetail()

        assertEquals("T100", detail.tradeNo)
        assertEquals("API100", detail.apiTradeNo)
        assertEquals("富通支付宝", detail.channelName)
        assertEquals("122.88", detail.merchantIncome)
        assertEquals(2, detail.notifyCount)
    }

    @Test
    fun aggregatesSevenDayMerchantFlowByUid() {
        val dates = (1..7).map { "2026-08-${it.toString().padStart(2, '0')}" }
        val rows = dates.indices.map { index ->
            listOf(
                JSONObject()
                    .put("uid", "1001")
                    .put("total", "${100 + index}.00")
                    .put("type_1", "60")
                    .put("type_2", "30")
                    .put("type_3", "10"),
            )
        }

        val flow = aggregateMerchantFlows(dates, listOf("1001", "1000"), rows).getValue("1001")

        assertEquals(721.0, flow.total, 0.001)
        assertEquals(103.0, flow.dailyAverage, 0.001)
        assertEquals(106.0, flow.peak, 0.001)
        assertEquals(420.0, flow.alipayTotal, 0.001)
        assertEquals(0.0, aggregateMerchantFlows(dates, listOf("1000"), rows).getValue("1000").total, 0.001)
    }
}
