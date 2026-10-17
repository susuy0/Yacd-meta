# VineChat Futoon 0.2

Telegram 安卓客户端内的富通悬浮业务面板，包名 `com.futoonpay.vinechat.beta`。包含选中文本/消息/截图识别、TGID 查商户、订单和商户操作、凭证编辑与手动选收件人、批量处理、通道/轮询/监测/结算/域名/黑名单模块。

本仓库存放对官方源码的完整修改补丁及构建流程。构建会取固定上游提交 `f32ffeec763f0bdbf4bec25b52d654f77bff733d`，应用补丁，再从源码编译 native 库与 APK。无需把整个官方仓库复制到这里。

## GitHub 自动构建

1. 将此目录内容放在专用仓库根目录，包括 `.github/workflows/android.yml`。
2. 在仓库 Settings → Secrets and variables → Actions 添加：

| Secret | 内容 |
| --- | --- |
| `VINE_API_ID` | 可选，与 Hash 成对填写；均留空时使用源码自带的开发参数 |
| `VINE_API_HASH` | 可选，对应 API Hash；与 ID 成对填写 |
| `GOOGLE_SERVICES_JSON_BASE64` | Firebase 下载的 google-services.json 的 Base64，Android 客户端包名必须为 `com.futoonpay.vinechat.beta` |

macOS 可执行 `base64 -i google-services.json | tr -d '\n'` 后将结果填到 Secret，不要放进仓库文件或聊天日志。

3. 推送 main/master，或 Actions → VineChat Android → Run workflow。
4. 默认先跑 107 项核心与接口模拟测试，再构建 ARM64 APK。手动运行可选择四架构；构建完成后在该次运行的 Artifacts 下载 APK 和 SHA256SUMS。

Telegram API ID/Hash 均留空时，构建使用源码自带开发参数，不会因此阻止编译。登录可用性仍需实测，发布时应换成本应用的参数。

目前按开发测试阶段构建：没有自己的 Firebase 配置时，继续编译，产物名标明 `development-no-fcm`，聊天收发和通知处理代码保留。上游 Firebase 占位文件不包含 `com.futoonpay.vinechat.beta`，不能直接用于此包。

添加本应用 Firebase 配置后，构建会自动使用它。手动运行时勾选 `require_push` 可以强制校验配置，缺失则停止打包。配置正确也不等于设备锁屏投递已验证。

Telegram 正常消息推送保留；暂不做的是富通业务提醒、盯单、独立工作台和先前暂缓的体验扩展。

## 本地恢复源码

```bash
bash ci/checkout.sh VineChat full
```

完整功能与构建说明位于恢复后的 `VineChat/Tools/futoon/README.md`。全部 31 个修改文件由 integration.patch 重现，包含新增源码、界面资源和测试。

## 当前验证结果

- 107 项核心/HTTP 模拟契约检查通过；面板交互回归通过，包含筛选不改变自动识别、OCR 不执行业务修改、发送预览不冒充成功、发送失败与确认状态。
- 新版完整 APK、真实账号、后台推送、实际发送和资金操作尚未完成实机验证。
- 余额写入目前仅支持已核实接口的 CNY；其他币种可查看。后台版本差异仍需联调。
- 原型界面作为本地 WebView 资源运行，HTTP、OCR、加密和发送为原生能力，无生产 mock 数据。

测试包使用上游公开测试签名。GitHub 构建使用的第三方 Actions 固定到具体提交。授权 GPL-2.0-or-later；详情见 LICENSE。
