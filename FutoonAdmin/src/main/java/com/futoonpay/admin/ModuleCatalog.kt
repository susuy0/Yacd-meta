package com.futoonpay.admin

internal sealed interface ModuleSource {
    data class Ajax(
        val endpoint: String,
        val referer: String,
        val idField: String,
        val fields: List<String>,
        val labels: Map<String, String>,
        val searchField: String = "",
    ) : ModuleSource

    data class Documents(val pages: List<ModulePage>) : ModuleSource
}

internal data class ModulePage(val title: String, val path: String)
internal data class ModuleDefinition(val id: String, val title: String, val subtitle: String, val source: ModuleSource)

internal object ModuleCatalog {
    private val definitions = listOf(
        ModuleDefinition("funds", "资金明细", "账户余额变化与交易流水", ajax(
            "ajax_user.php?act=recordList", "record.php", "id",
            listOf("uid", "type", "money", "oldmoney", "newmoney", "trade_no", "date", "addtime", "action"),
            "uid" to "商户 UID", "type" to "类型", "money" to "金额", "oldmoney" to "变更前",
            "newmoney" to "变更后", "trade_no" to "关联订单", "date" to "时间", "addtime" to "时间", "action" to "说明",
            searchField = "uid",
        )),
        ModuleDefinition("merchant_stats", "商户统计", "按日期查看商户交易表现", docs(ModulePage("商户统计", "ustat.php"))),
        ModuleDefinition("risk", "风控记录", "风险命中与请求记录", ajax(
            "ajax_order.php?act=riskList", "risk.php", "id",
            listOf("uid", "type", "content", "url", "date", "addtime"),
            "uid" to "商户 UID", "type" to "风险类型", "content" to "内容", "url" to "来源", "date" to "时间", "addtime" to "时间",
            searchField = "content",
        )),
        ModuleDefinition("blacklist", "支付黑名单", "查询、添加与移除拦截规则", ajax(
            "ajax_user.php?act=blackList", "blacklist.php", "id",
            listOf("type", "content", "addtime", "endtime", "remark"),
            "type" to "类型", "content" to "匹配内容", "addtime" to "添加时间", "endtime" to "到期时间", "remark" to "备注",
            searchField = "content",
        )),
        ModuleDefinition("inactive", "无流水商户", "连续 5 日无交易的商户", docs(ModulePage("无流水商户", "inactive_merchant.php"))),
        ModuleDefinition("channels", "支付通道", "通道参数、状态与账户", docs(ModulePage("支付通道", "pay_channel.php"))),
        ModuleDefinition("payment_types", "支付方式", "支付产品与费率配置", docs(ModulePage("支付方式", "pay_type.php"))),
        ModuleDefinition("rolls", "通道轮询", "通道路由与轮询规则", docs(ModulePage("轮询规则", "pay_roll.php"))),
        ModuleDefinition("channel_monitor", "通道监测", "可用率与通道健康状态", docs(ModulePage("通道监测", "pay_channel_monitor.php"))),
        ModuleDefinition("proxies", "中转代理", "代理节点、状态与连接配置", docs(ModulePage("中转代理", "proxy.php"))),
        ModuleDefinition("profit_receivers", "分账接收方", "接收方账户与关系管理", docs(ModulePage("分账接收方", "ps_receiver.php"))),
        ModuleDefinition("profit_orders", "分账记录", "分账订单、状态与结果", ajax(
            "ajax_profitsharing.php?act=orderList", "ps_order.php", "trade_no",
            listOf("trade_no", "rid", "money", "addtime", "endtime", "status", "result"),
            "trade_no" to "订单号", "rid" to "接收方", "money" to "金额", "addtime" to "创建时间", "endtime" to "完成时间", "status" to "状态", "result" to "处理结果",
            searchField = "trade_no",
        )),
        ModuleDefinition("transfer", "企业付款", "付款任务、收款方与处理结果", docs(ModulePage("企业付款", "transfer.php"))),
        ModuleDefinition("announcements", "公告管理", "站内公告与启停状态", docs(ModulePage("公告", "gonggao.php"))),
        ModuleDefinition("payment_settings", "支付与结算设置", "资金业务全局参数", docs(
            ModulePage("支付", "set.php?mod=pay"), ModulePage("结算", "set.php?mod=settle"),
            ModulePage("预付款", "set.php?mod=prepayment"), ModulePage("企业付款", "set.php?mod=transfer"),
        )),
        ModuleDefinition("site_settings", "站点与账号设置", "站点外观、邮件、任务与管理员", docs(
            ModulePage("站点", "set.php?mod=site"), ModulePage("模板", "set.php?mod=template"),
            ModulePage("邮件", "set.php?mod=mail"), ModulePage("定时任务", "set.php?mod=cron"),
            ModulePage("管理员", "set.php?mod=account"),
        )),
        ModuleDefinition("integrations", "公众号与证书", "微信与证书集成", docs(
            ModulePage("微信公众号", "pay_weixin.php"),
            ModulePage("微信 OAuth", "pay_weixin.php?mod=oauth"), ModulePage("证书", "certificate.php"),
            ModulePage("微信客服", "set_wxkf.php"),
        )),
        ModuleDefinition("login_logs", "登录日志", "管理员与商户登录记录", ajax(
            "ajax_user.php?act=logList", "log.php", "id",
            listOf("uid", "type", "ip", "city", "date", "addtime", "data"),
            "uid" to "用户 UID", "type" to "类型", "ip" to "IP", "city" to "地区", "date" to "时间", "addtime" to "时间", "data" to "设备信息",
            searchField = "uid",
        )),
    )

    fun find(id: String?): ModuleDefinition = definitions.firstOrNull { it.id == id } ?: definitions.first()

    private fun ajax(
        endpoint: String, referer: String, idField: String, fields: List<String>,
        vararg labels: Pair<String, String>, searchField: String = "",
    ) = ModuleSource.Ajax(endpoint, referer, idField, fields, labels.toMap(), searchField)

    private fun docs(vararg pages: ModulePage) = ModuleSource.Documents(pages.toList())
}

