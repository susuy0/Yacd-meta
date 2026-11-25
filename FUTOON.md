# Futoon / Telegram Android 13.2.2 迁移

## 基线与范围

本项目从官方 `DrKLO/Telegram` master 的 `21f7d109da6ab3506c9e9577f98c6a38771ab335` 开始，版本 13.2.2 / 7303；2026-11-25 核对。固定输入见 `UPSTREAM.lock.json`。

旧项目作为只读迁移来源，未覆盖或修改。新版保留 Telegram 官方界面、相机、录音、定位、通话及媒体编辑，不套用旧版 164 文件外观/能力裁剪补丁。

### 已迁移的源码功能

- 聊天悬浮面板：识别、订单、商户、结算、更多五 Tab。
- 选中文字、消息或多条消息查单；图库 OCR / 二维码；候选、历史、收藏与导出。
- 商户识别与管理、会话商户/多通道快捷栏、开户和业务回复草稿。
- 订单、结算、通道及后台模块兼容功能。
- 聊天图片凭证、图库选图/编辑、人工选择接收人/话题和 Telegram 原生预览。
- 凭证回执 metadata 在图片、预览、编辑和作为文件发送路径透传。
- Tag、可复制 Telegram ID、快速回复、设置及按群规则自动化。

群自动化默认关闭；回复文案填入草稿，由使用者发送。按钮规则沿用原来的配置行为。图片 OCR 通过面板图库入口；聊天图片菜单用于添加凭证。

这里的“已迁移”表示源码接入，不代表真机、后台写入或 Telegram 投递验收完成。

## 首次使用入口

1. 构建、安装并登录 Telegram 账号。
2. Telegram 设置 → FutoonPay 设置：调整悬浮入口、快捷回复和本机扩展配置。
3. 进入聊天，点击“富通”悬浮入口；在面板登录页填写 HTTPS 后台目录并登录。后台连接在此完成，非全局设置页。
4. 用文字查单或面板图库识别；发送凭证时手动选收件人/话题，并确认原生发送预览。

## 架构与本轮重构

```
Telegram ChatActivity / 原生设置 / 媒体发送
    ↓ 薄宿主适配：FutoonPanel / NativeBridge / DeliveryTracker
FutoonAdmin：Compose UI、ViewModel、会话隔离、Repository
    ↓ AdminRequestPolicy + 明确区分读取/单次写操作
原 PHP 后台兼容接口
```

- `FutoonAdmin` 无 `org.telegram` 编译依赖，可独立测试。
- Java 兼容路径和 Kotlin 客户端共享 HTTPS、来源、端口及后台目录规则。
- 读取/登录逐跳检查；写请求禁止自动重试和跳转，结果不明确时提示先核对。
- 切后台清理活动凭证、旧面板、下载和选择器；失效回调不写入新作用域。
- 无效订单初始化直接中止发送；保留账号、订单和后台的凭证关联。
- 同一后台保留表单/批量操作票据；后台或登录身份变化时重置业务状态。
- 使用 AndroidX 的宿主离开提示监听，保留官方媒体/PiP 回调。

后台兼容层仍含 HTML/脚本解析及历史 JSON bridge。本轮未伪造新的服务端 API；服务端幂等、操作流水和审计需与实际后台另行对接。客户端单次请求不等于端到端事务保证。

## 环境

- JDK 17 或 21；仓库 wrapper 为 Gradle 8.13，并锁定下载 SHA-256。
- AGP 8.13.2，Kotlin / Compose compiler 2.1.0。
- Android SDK platform 36、build-tools 36.0.0。
- APK 另需 NDK 27.2.12479018、CMake 3.22.1 和官方 native 子模块。
- 首次依赖下载和编译需要额外磁盘；完整 native 构建建议预留至少 30 GiB，实际占用依所选 ABI 和缓存而定。

## 重放检查

```bash
export ANDROID_HOME=/path/to/android-sdk
# 独立业务、网络、Compose 和宿主兼容 fixture + Lint
python3 Tools/futoon/check.py tests
# 编译真实 Telegram Java 宿主，不生成缺 JNI 的 APK
python3 Tools/futoon/check.py host
# 单独重放当前生产面板方法的作用域 fixture（tests 已自动执行）
python3 Tools/futoon/test_panel_scope.py
```

`--gradle /path/to/gradle` 可指定已有 Gradle。当前机器验证使用已有 Gradle 8.14.5，避免重建一套工具缓存；wrapper 8.13 保留为固定构建入口。`futoonJavaChecks=true` 只用于编译，显式阻止 assemble/package/install 等任务。

旧 Java 后台契约 fixture 已纳入独立纯 JVM 子项目 `:HostCompatibility:test`，直接编译真实宿主 Java 和共享请求策略，避开 Android 的测试 bootclasspath；单独运行可使用 `bash Tools/futoon/run-tests.sh`，或 `GRADLE=/path/to/gradle bash Tools/futoon/run-tests.sh`。

## 完整 APK

当前目录为节省空间使用 sparse checkout，native 源码尚未展开。准备更充足的磁盘后：

```bash
git sparse-checkout disable
git submodule update --init --recursive --depth 1 --jobs 2
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
export ANDROID_HOME=/path/to/android-sdk
export FUTOON_API_ID=YOUR_TELEGRAM_API_ID
export FUTOON_API_HASH=YOUR_TELEGRAM_API_HASH
python3 Tools/futoon/check.py apk
```

实际分发使用自己的 Telegram API 配置。未设置时保留上游开发值，仅供编译；未验证登录。包名前缀为 `com.futoonpay.vinechat`，Debug 后缀 `.beta`，避免覆盖官方客户端。

Firebase 推送使用匹配该包名的自有 `TMessagesProj_App/google-services.json`。缺少配置时可显式传 `-PfutoonSkipGoogleServices=true` 构建开发版；该选项不代表推送可用。Debug 使用本机 debug keystore；正式签名配置放在 Git 忽略的 `futoon-signing.properties`：

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

本轮构建入口为 `TMessagesProj_App`；其 release/standalone 产物要求上述签名配置。其余上游渠道项目保留原有分发配置，尚未进行对应迁移和发布验证。

原生库从固定官方源码构建，不从旧 APK 复制 `.so`。上游自动更新关闭，避免更新覆盖业务集成；官方包专属的 passkey/FPNV 功能关闭。

## 本机验证结果（2026-11-25）

| 项目 | 结果 |
| --- | --- |
| 真实 Telegram 宿主 Java 编译 | 通过，148 tasks，最终源码重跑成功 |
| 业务/网络/Compose 单元测试 | 57 suites、384 tests，0 failures/errors/skipped |
| 纯 JVM 宿主回归 | 8 tests 通过；其中四个契约 fixture 共 270 checks，另含 4 个 bridge snapshot 测试 |
| 面板作用域 fixture | 34 断言通过，提取 5 个当前生产方法及真实 dispatch |
| Android Lint | 通过；0 Error，42 Warning、3 Hint，告警尚待清理 |
| 原项目及官方子模块 | 原项目未修改；两个已初始化子模块工作树干净 |

实际运行使用本机 Gradle 8.14.5 / Homebrew JDK 21；独立面板 fixture 使用 Temurin 21。测试命令、来源版本及关键源码摘要见 `VALIDATION.json`。完整日志保存在本任务工作目录，HTML/XML 报告由上述测试任务生成。

本机磁盘余量约 2.7 GiB。便捷脚本为源码检查预留 3 GiB 门槛；最后一次宿主编译复用现有依赖和编译缓存，直接执行同等 Gradle 任务通过。完整 native/APK 构建仍建议预留至少 30 GiB，尚未执行。

## 待验收

- 完整 APK / native 编译、安装与账号登录。
- 真机面板尺寸、键盘、返回、旋转和多账号切换。
- 测试后台的只读契约、写操作、超时核对和权限边界。
- 选图、编辑、人工选人/话题、发送失败及真实服务器回执。
- 后台运行推送、图库权限、PiP 和自动化规则。
- 服务端幂等、审计、可查询操作结果协议。

本地执行结果与未验收项目分别记录，避免把 fixture 通过等同于运行验收。
