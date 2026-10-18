package com.futoonpay.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDocumentParserTest {
    @Test
    fun parsesServerRenderedPaymentChannelsAndKeepsPluginAsSelect() {
        val html = """
            <html><body>
              <table><thead><tr><th>ID</th><th>名称</th><th>插件</th><th>状态</th><th>操作</th></tr></thead>
                <tbody><tr><td>1</td><td>九九支付</td><td>jiujiupay</td><td>已开启</td>
                  <td><button onclick="editChannel(1)">编辑</button></td></tr></tbody></table>
              <form id="form-channel">
                <input name="action" value="add"><input name="id" value="">
                <input name="name" value=""><select name="type"><option value="0">请选择</option><option value="1">支付宝</option></select>
                <select name="plugin"><option value="">请选择支付插件</option></select><input name="rate" value="">
              </form>
              <script>
                function editChannel(id) {
                  ${'$'}.ajax({ type: 'GET', url: 'ajax_pay.php?act=getChannel&id='+id });
                }
                function saveChannel() {
                  ${'$'}.ajax({ type: 'POST', url: 'ajax_pay.php?act=saveChannel', data: ${'$'}("#form-channel").serialize() });
                }
              </script>
            </body></html>
        """.trimIndent()

        val result = NativeDocumentParser.parse(html, "pay_channel.php?tab=all")

        assertEquals("九九支付", result.tables.single().rows.single()[1])
        assertEquals("ajax_pay.php?act=getChannel&id=1", result.tables.single().rowActions.single().single().endpoint)
        assertEquals("ajax_pay.php?act=saveChannel", result.forms.single().action)
        assertEquals("select", result.forms.single().fields.first { it.name == "plugin" }.type)
    }

    @Test
    fun parsesTablesAndFormsWithoutSensitiveValues() {
        val html = """
            <html><head><title>通道设置</title></head><body>
              <table><thead><tr><th>名称</th><th>状态</th></tr></thead>
                <tbody><tr><td>测试通道</td><td>启用</td></tr></tbody></table>
              <form method="post" action="set.php?mod=pay">
                <label>商户号<input name="merchant_id" value="1001"></label>
                <label>密钥<input name="app_secret" value="must-not-leak"></label>
                <label><input type="radio" name="enabled" value="1" checked>启用</label>
                <label><input type="radio" name="enabled" value="0">停用</label>
              </form>
            </body></html>
        """.trimIndent()

        val result = NativeDocumentParser.parse(html, "set.php?mod=pay")

        assertEquals("测试通道", result.tables.single().rows.single().first())
        assertEquals("1001", result.forms.single().fields.first { it.name == "merchant_id" }.value)
        assertEquals("", result.forms.single().fields.first { it.name == "app_secret" }.value)
        assertTrue(result.forms.single().fields.first { it.name == "app_secret" }.sensitive)
        assertEquals(2, result.forms.single().fields.first { it.name == "enabled" }.options.size)
    }

    @Test
    fun convertsInlineAjaxButtonsAndSerializedFormsToNativeActions() {
        val html = """
            <html><body>
              <table><thead><tr><th>ID</th><th>名称</th><th>操作</th></tr></thead>
                <tbody><tr><td>7</td><td>代理 A</td><td><button onclick="setStatus(7,0)">停用</button></td></tr></tbody></table>
              <form id="form-proxy"><input name="action" value="add"><input name="name" value=""></form>
              <script>
                function setStatus(id,status) {
                  ${'$'}.ajax({
                    type: 'GET',
                    url: 'ajax_proxy.php?act=setProxy&id='+id+'&status='+status,
                  });
                }
                function saveProxy() {
                  ${'$'}.ajax({
                    type: 'POST',
                    url: 'ajax_proxy.php?act=saveProxy',
                    data: ${'$'}("#form-proxy").serialize()
                  });
                }
              </script>
            </body></html>
        """.trimIndent()

        val result = NativeDocumentParser.parse(html, "proxy.php")

        assertEquals("ajax_proxy.php?act=setProxy&id=7&status=0", result.tables.single().rowActions.single().single().endpoint)
        assertTrue(result.tables.single().rowActions.single().single().destructive)
        assertEquals("ajax_proxy.php?act=saveProxy", result.forms.single().action)
        assertEquals("POST", result.forms.single().method)
    }

    @Test
    fun discoversCustomizedPaymentPathsFromNavigation() {
        val html = """
            <nav>
              <a href="./pay_channel.php">支付通道</a>
              <a href="./channel_health.php?view=all"> 通道 监测 </a>
              <a href="set.php?mod=proxy">中转代理</a>
            </nav>
        """.trimIndent()

        assertEquals(
            "channel_health.php?view=all",
            NativeDocumentParser.navigationPath(html, listOf("通道监测", "通道监控")),
        )
        assertEquals(
            "set.php?mod=proxy",
            NativeDocumentParser.navigationPath(html, listOf("中转代理")),
        )
    }

    @Test
    fun ignoresUnusedJavascriptArgumentsWhenBuildingRowActions() {
        val html = """
            <script>
              function setStatus(id, status, button) {
                ${'$'}.ajax({
                  type: 'GET',
                  url: 'ajax_proxy.php?act=setProxy&id='+id+'&status='+status,
                });
              }
            </script>
        """.trimIndent()

        val action = NativeDocumentParser.actionsForValues(
            html,
            mapOf("id" to "9", "status" to "1"),
        ).single()

        assertEquals("ajax_proxy.php?act=setProxy&id=9&status=0", action.endpoint)
        assertEquals("关闭", action.label)
    }

    @Test
    fun parsesJavascriptDataObjectWithoutRegexFailure() {
        val html = """
            <script>
              function resetResult(id) {
                ${'$'}.ajax({
                  type: 'POST',
                  url: 'ajax_monitor.php?act=resetResult',
                  data: { id: id }
                });
              }
            </script>
        """.trimIndent()

        val action = NativeDocumentParser.actionsForValues(html, mapOf("id" to "17")).single()

        assertEquals("ajax_monitor.php?act=resetResult", action.endpoint)
        assertEquals(mapOf("id" to "17"), action.fields)
    }

    @Test
    fun prefersRealSelectOverSameNameHiddenInput() {
        val html = """
            <form>
              <input type="hidden" name="plugin" value="alipay">
              <select name="plugin">
                <option value="alipay">支付宝插件</option>
                <option value="wechat">微信插件</option>
              </select>
            </form>
        """.trimIndent()

        val field = NativeDocumentParser.parse(html, "pay_channel.php")
            .forms.single().fields.single()

        assertEquals("select", field.type)
        assertEquals(2, field.options.size)
        assertEquals("alipay", field.value)
    }

    @Test
    fun resolvesPluginInputLinkedToDatalistAsSelect() {
        val html = """
            <form>
              <input name="plugin" list="plugin-options" value="alipay">
              <datalist id="plugin-options">
                <option value="alipay">支付宝插件</option>
                <option value="wechat">微信插件</option>
              </datalist>
            </form>
        """.trimIndent()

        val field = NativeDocumentParser.parse(html, "pay_channel.php")
            .forms.single().fields.single()

        assertEquals("select", field.type)
        assertEquals(2, field.options.size)
    }

    @Test
    fun resolvesPluginSelectWithCustomizedNameFromNearbyChineseLabel() {
        val html = """
            <form><div class="form-group">
              <label>支付插件</label>
              <input type="hidden" name="plugin" value="alipay">
              <select name="channel_driver"><option value="alipay">支付宝</option><option value="wxpay">微信</option></select>
            </div></form>
        """.trimIndent()

        val field = NativeDocumentParser.parse(html, "pay_channel.php").forms.single().fields
            .first { it.name == "plugin" }

        assertEquals("select", field.type)
        assertEquals(listOf("alipay", "wxpay"), field.options.map { it.value })
    }

    @Test
    fun discoversBootstrapEndpointsBeyondListNamedActions() {
        val html = """
            <script>
              $('#table').bootstrapTable({ url: 'ajax_monitor.php?act=getChannelMonitor' });
              function reset(id) { $.ajax({url:'ajax_monitor.php?act=resetResult&id='+id}); }
            </script>
        """.trimIndent()

        assertEquals(
            listOf("ajax_monitor.php?act=getChannelMonitor"),
            NativeDocumentParser.listEndpoints(html),
        )
    }
}
