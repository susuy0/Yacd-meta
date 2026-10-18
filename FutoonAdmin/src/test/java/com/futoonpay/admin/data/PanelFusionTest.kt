package com.futoonpay.admin.data

import com.futoonpay.admin.PanelBusinessEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import okhttp3.FormBody

class PanelFusionTest {
    @Test fun orderListUsesOriginalFiltersAndRows() {
        var fields=emptyMap<String,String>()
        val engine=PanelBusinessEngine(fixtureClient({r->
            val b=r.body as FormBody;fields=(0 until b.size).associate {b.name(it) to b.value(it)}
            200 to """{"total":1,"rows":[{"trade_no":"SYS10001","uid":"18","money":"20.00","status":1}]}"""
        }))
        val out=engine.execute("orders.list",JSONObject().put("uid","18").put("status","1").put("page",2))
        assertEquals("18",fields["uid"]);assertEquals("1",fields["dstatus"]);assertEquals("30",fields["offset"])
        assertEquals("SYS10001",out.getJSONArray("rows").getJSONObject(0).getString("trade_no"))
    }
    @Test fun settingsStayInPanelAndSaveOriginalHiddenFields() {
        var sent=emptyMap<String,String>()
        val engine=PanelBusinessEngine(fixtureClient({r->
            if(r.method=="POST") {val b=r.body as FormBody;sent=(0 until b.size).associate {b.name(it) to b.value(it)};200 to """{"code":0,"msg":"ok"}"""}
            else 200 to """<title>站点设置</title><form action="set.php?mod=site" method="post"><input type="hidden" name="token" value="fresh"><label>站点名称<input name="sitename" value="old"></label></form>"""
        }))
        val doc=engine.execute("module.read",JSONObject().put("module","site_settings"))
        assertEquals(5,doc.getJSONArray("sections").length())
        val form=doc.getJSONArray("forms").getJSONObject(0)
        assertFalse(form.getJSONArray("fields").toString().contains("fresh"))
        val result=engine.execute("form.save",JSONObject().put("ticket",form.getString("ticket")).put("values",JSONObject().put("sitename","new").put("token","forged")))
        assertEquals(0,result.getInt("code"));assertEquals("fresh",sent["token"]);assertEquals("new",sent["sitename"])
        try {engine.execute("form.save",JSONObject().put("ticket",form.getString("ticket")).put("values",JSONObject()));fail("ticket must be one use")}catch(_:IllegalStateException){}
    }
    @Test fun routeEditorUsesOriginalRepositoryAndValidatesBeforeMutation() {
        var writes=0
        val engine=PanelBusinessEngine(fixtureClient({r->
            when {
                r.url.queryParameter("act")=="rollInfo" -> 200 to """{"code":0,"channels":[{"id":"11","name":"A"},{"id":"22","name":"B"}],"info":[{"channel":"11","weight":"3"}]}"""
                r.method=="POST" -> {writes++;200 to """{"code":0}"""}
                else->200 to "<html><title>轮询</title></html>"
            }
        }))
        engine.execute("module.read",JSONObject().put("module","rolls"))
        val f=engine.execute("module.editor",JSONObject().put("module","rolls").put("id","8").put("editor","route"))
        assertEquals("route",f.getString("kind"));assertEquals(3,f.getJSONArray("routeOptions").length())
        val values=JSONObject().put("list[0][channel]","11").put("list[0][weight]","-1")
        val out=engine.execute("form.save",JSONObject().put("ticket",f.getString("ticket")).put("values",values))
        assertEquals(-1,out.getInt("code"));assertEquals(0,writes)
    }
    @Test fun unknownModulesAndForgedActionTicketsCannotRequestBackend() {
        var calls=0;val engine=PanelBusinessEngine(fixtureClient({calls++;200 to "{}"}))
        for(p in listOf(JSONObject().put("module","https://other.test/"),JSONObject().put("ticket","invented"))) {
            try {engine.execute(if(p.has("module"))"module.read" else "module.action",p);fail()}catch(_:Exception){}
        }
        assertEquals(0,calls)
    }
    @Test fun monitorUsesOriginalPayloadParserAndKeepsActions() {
        val engine=PanelBusinessEngine(fixtureClient({r->
            if(r.method=="POST")200 to """{"code":0,"rows":[{"id":"88","name":"A","success_rate_5min":"92.5%","success_rate_24hour":"97.1%","status":"1"}]}"""
            else 200 to "<html><title>监测</title></html>"
        }))
        val out=engine.execute("module.read",JSONObject().put("module","channel_monitor"))
        val row=out.getJSONArray("rows").getJSONObject(0)
        assertEquals("88",row.getString("id"));assertEquals("92.5%",row.getString("success_rate_5min"))
    }
}
