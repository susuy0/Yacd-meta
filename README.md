# VineChat FUTOON v0.6.1 Native

FUTOON PAY 管理功能以 Android Jetpack Compose 原生组件嵌入 Telegram 当前聊天上方的悬浮面板。

## 原生融合

- 业务主界面不再包含 WebView、HTML、JavaScript 或 CSS。
- 订单、结算和“更多”在同一个原生悬浮容器内切换，不启动独立管理 Activity。
- “更多”包含当前会话商户、支付通道、支付方式、轮询组、通道监测、中转代理、域名审核、风控、黑名单、资金、商户统计、无流水商户、分账、企业付款、公告、支付/结算设置、站点/账号设置、公众号/证书和登录日志。
- 直接移植 FUTOON PAY 1.3.4 的 CoreScreens、PaymentAdminScreen、ConfigurationScreens、ModernManage、商户编辑、批量组件及 AppViewModel。原 NativeModuleActivity 和 DomainAuditActivity 拆除 Activity 外壳后，以组件嵌入同一容器。
- 动态表单、插件账户参数、支付方式绑定、轮询成员及设置子栏目使用原版控件、字段校验和提交流程。后台客户端、Repository、解析、监测和批量执行代码继续复用原版实现。
- 群话题末尾 TGID 与私聊对方 TGID 可精确查询商户；订单识别使用原 Telegram 聊天上下文。
- 图片凭证仍由用户选择 Telegram 收件人并进入发送预览，不自动发送。
- 所有资金、状态和配置写操作先显示原生二次确认。

提醒、盯单通知和独立工作台不在本次范围内；Telegram 原有消息通知及推送能力保留。

## 构建

上游 Telegram 固定为 `f32ffeec763f0bdbf4bec25b52d654f77bff733d`。测试包名为 `com.futoonpay.vinechat.beta`，版本代码 `71839`。

CI 会执行原业务单测、原生面板契约审计、Lint、Java 编译、ARM64 原生库及 APK 构建，并审计已移除的通话、录音和联系人组件。

测试不会连接真实付款后台，不执行真实资金操作或 Telegram 消息发送。完整构建通过后仍需真机及实际后台联调。

GPL-2.0-or-later，随 Telegram 上游许可证。
