# VineChat Futoon v0.5.0 Lite

把原版 FutoonPayAdmin 1.3.4 的业务代码融合进聊天悬浮面板。订单、“更多”、配置表单及路由都在现有面板内操作，不再启动独立管理 Activity。保留聊天文字识别、话题/私信 TGID 查商户、图片凭证和手动选择 Telegram 收件人。

## 代码融合

`FutoonAdmin` 现在是数据与业务模块：保留原版 Repository、PHP 客户端、表单解析、配置校验、监测指标与批量执行器，移除原版 Compose 页面、应用生命周期、独立窗口及资源。`PanelBusinessEngine` 把原版模型、表单和已解析操作接到宿主桥接协议，使用宿主按 Telegram 用户加密隔离的会话。

“更多”保留原版 20 个入口，另有结算与业务数据。设置中的支付/结算/预付款/企业付款、站点/模板/邮件/任务/管理员、公众号/OAuth/证书/客服在面板内切换。表单保存和行操作使用本地签发的限时票据、原版字段与隐藏值；不接受页面传入任意后台地址。原版未提供编辑表单的后台页面会显示明确错误。

订单列表、详情、退款和状态操作调用原版业务实现；文字识别与编号精确匹配继续使用宿主聊天流程。订单、结算、支付配置与监测的多选操作调用原版批量选项和执行器，保留状态适用条件、逐条结果及异常停止。通道监测使用原版动态列表解析；原版 HTML 表格、行操作、配置表单和子设置页面由面板统一呈现。

## 验证与安装包

[最终 CI](https://github.com/susuy0/Yacd-meta/actions/runs/39093338920) 全部通过，构建源码提交 `c7dedb80a9450cc446b5c75eb81885e5949bbbd5`。

- 123 项原版业务及融合适配测试通过，0 失败、0 错误、0 跳过；Lint 0 错误。
- 聊天识别、启动和 AndroidX 回调回归通过；面板覆盖编辑、路由、批量、设置栏目和测试支付结果。
- 默认 390×650 窗口的第一条订单完整可见；小屏、键盘、滚动与固定底部操作检查通过。
- Java、原生库和 APK 构建通过；已移除的通话、录音和联系人组件审计通过。
- APK V2 签名、完整内容摘要、ZIP CRC 和 5 个面板资源已核对。原版 Repository 与融合桥接在映射中存在，独立管理 Activity 和原版 UI 均不存在。

安装包 `VineChat-Futoon-v0.5.0-lite-arm64.apk`，40,033,396 字节（38.18 MiB）。包名 `com.futoonpay.vinechat.beta`，版本代码 `71819`，最低 API 23，目标 API 36。签名与上一版相同，可覆盖安装。

SHA-256：`f2c81dfb8a74a88fd0fe2f2466a0c940d4acc94bf1cafba5a6330f2ac8de9d76`。

本次为 `development-no-fcm` 开发测试构建，未配置 FCM。未进行线上后台或真机联调，没有执行真实资金操作或 Telegram 消息发送。完整验证信息见 `BUILD.json`。

## 构建与配置

Telegram 上游固定为 `f32ffeec763f0bdbf4bec25b52d654f77bff733d`。`integration.patch` 安装精简客户端修改并接入 `FutoonAdmin` 库。master 推送运行自动检查与 APK 构建；`native-startup-validation` 分支用于单独验证完整测试和 Lint。

Telegram API 凭据和 Firebase 推送配置沿用现有仓库配置；缺少本应用 Firebase 配置时，产物标注 `development-no-fcm`，没有验证后台推送送达。源码、APK 和 CI 输出不包含后台账号、会话或生产签名私钥。R8 mapping.txt 与 APK 一起保留在构建产物中。

GPL-2.0-or-later，随 Telegram 上游许可证。提醒与工作台未新增。
