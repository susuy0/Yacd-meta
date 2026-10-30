package com.futoonpay.admin

internal data class OfficialMenuEntry(val title: String, val subtitle: String, val id: String)

/** Verified from the authenticated new backend navigation on 2026-10-30. */
internal object OfficialBackendFeatures {
    val sections = listOf(
        "支付配置" to listOf(
            OfficialMenuEntry("支付通道", "通道、账户配置、测试支付", "channels"),
            OfficialMenuEntry("支付方式", "费率、关联通道", "payment_types"),
            OfficialMenuEntry("支付插件", "插件信息与状态", "payment_plugins"),
            OfficialMenuEntry("轮询组", "选路策略、成员通道", "rolls"),
            OfficialMenuEntry("公众号小程序", "公众号与小程序配置", "weixin_apps"),
        ),
        "商户与账务" to listOf(
            OfficialMenuEntry("资金明细", "余额变化与交易流水", "funds"),
            OfficialMenuEntry("商户统计", "按日期查看交易表现", "merchant_stats"),
            OfficialMenuEntry("用户组设置", "用户组、费率与权限", "user_groups"),
            OfficialMenuEntry("用户组购买", "可购买用户组与价格", "group_purchase"),
            OfficialMenuEntry("5 日无流水商户", "连续 5 日无交易的商户", "inactive"),
        ),
        "审核与监测" to listOf(
            OfficialMenuEntry("授权域名", "查看与处理支付域名", "domain_audit"),
            OfficialMenuEntry("通道监测", "成功率与检测结果", "channel_monitor"),
            OfficialMenuEntry("支付黑名单", "查询与管理拦截规则", "blacklist"),
        ),
        "账号" to listOf(OfficialMenuEntry("管理员账号", "官网提供的管理员账号设置", "account_settings")),
    )
    val modules = sections.flatMap { it.second }.map { it.id }.filter { it != "domain_audit" }.toSet()
    fun available(id: String) = id in modules
}
