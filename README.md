# VineChat Futoon v0.4.0 Lite

订单列表和“更多”已接入原独立安卓版 FutoonPayAdmin 1.3.4 的 Kotlin / Jetpack Compose 页面、数据模型和 PHP 后台适配。页面通过应用内原生悬浮窗口打开；窗口默认使用可用屏幕区域，保留键盘避让、滚动与返回聊天。聊天识别、手动商户查询、凭证选择和 Telegram 收件人预览继续保留在悬浮工具中。

## 移植范围

- 订单：原版状态筛选、完整搜索、分页、详情、通道成功率与趋势、多选及批量操作。
- “更多”全部 20 个入口：支付通道、支付方式、轮询组、通道监测、中转代理、域名审核、设置、资金明细、风控记录、支付黑名单、商户统计、5 日无流水商户、分账接收方、分账记录、企业付款、公告管理、支付与结算设置、站点与账号设置、公众号与证书、登录日志。
- 新增、编辑、启停、删除、路由成员与权重、支付插件选择、批量操作沿用原版实现；后台未提供的能力按原版提示处理。
- 原版概览、结算和商户页面也随模块保留，以支持业务跳转。原生订单详情可准备凭证草稿，再返回聊天选择图片和发送对象。

`FutoonAdmin/` 保留全部 37 个原版 Kotlin 文件与 27 个原版测试文件。`SOURCE.json` 记录原始源码包及逐文件哈希。适配集中在宿主环境、账号隔离、共享加密会话、浮窗导航、包名与旧系统兼容；原版业务数据层保持原实现。

## 已验证安装包

`VineChat-Futoon-v0.4.0-lite-arm64.apk`，40,989,166 字节（39.1 MiB）。包名 `com.futoonpay.vinechat.beta`，版本代码 `71809`，最低 Android 6 / API 23，目标 API 36，ARM64。APK 签名与之前版本一致，版本代码更高，可覆盖安装。

SHA-256：`d0d4da6020decee44eccdcc09932276a8798101105a627f802189553b1acf776`。

- [APK 编译、原生库、压缩与精简审计](https://github.com/susuy0/Yacd-meta/actions/runs/39089871138/jobs/117564333395)：成功。
- [最终原生与界面验证](https://github.com/susuy0/Yacd-meta/actions/runs/39090676474)：成功。
- 原生测试 171 项通过，0 失败、0 错误、0 跳过；包含真实 MainActivity 在普通宿主 Application 下的内容渲染、悬浮边界及生命周期收尾。
- 原有 132 项查询/业务/监测/编辑回归、45 项布局检查、启动崩溃负例、AndroidX 回调兼容负例及浏览器界面回归通过。
- 原生模块 Lint 0 错误。保留 24 条依赖更新或代码风格警告。
- APK 完整内容、V2 签名、ZIP CRC、发布校验和、3 个私有原生 Activity、DEX 类及 5 个面板资源均已核对；移除的通话/录音/联系人组件审计通过。

APK 使用 `b67e52b1507c952eb02b550032bb6b4b49becfeb` 的生产源码。最终测试提交 `7a867bce7d0ec7a264b5d681e7a137b42c9804aa` 仅调整测试及 CI 配置，已核对生产代码、资源、integration.patch 与 APK 构建配置完全相同。这样可以保留已完成的安装包构建，并用正确的 Compose 测试规则完成原生启动验证。完整证据见 `BUILD.json`。

未执行线上资金/订单状态写入或 Telegram 消息发送；尚未进行 Android 17 真机及实际后台联调。

## 构建与配置

Telegram 上游固定为 `f32ffeec763f0bdbf4bec25b52d654f77bff733d`。`integration.patch` 安装精简客户端修改并接入 `FutoonAdmin` 库。master 推送运行自动检查与 APK 构建；`native-startup-validation` 分支用于单独验证完整测试和 Lint。

Telegram API 凭据和 Firebase 推送配置沿用现有仓库配置；缺少本应用 Firebase 配置时，产物标注 `development-no-fcm`，没有验证后台推送送达。源码、APK 和 CI 输出不包含后台账号、会话或生产签名私钥。R8 mapping.txt 与 APK 一起保留在构建产物中。

GPL-2.0-or-later，随 Telegram 上游许可证。提醒与工作台未新增。
