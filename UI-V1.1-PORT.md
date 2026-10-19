# 定制区域 UI v1.1 原生移植

本次仅调整一键回复、FutoonPay 设置及悬浮窗内业务页面。继续复用原生业务数据层、动态表单、分页与批量操作，不使用设计包中的 HTML 作为运行界面。

| 区域 | 实现 | 主要入口 |
| --- | --- | --- |
| 一键回复 | Tab / 分类 / 文案，独立预览，全文填入，短窗布局，管理往返恢复分类 | ChatToolsView、FutoonQuickReplies |
| 设置 | 三个独立入口，账号内本机配置，三级文案管理，字数校验与未保存退出确认 | FutoonChatSettingsActivity、ChatToolsView |
| 开户文案 | 六变量光标插入、变量高亮与定位、合成预览、恢复默认 | OpeningMessage、ChatToolsView |
| 主题 | 读取 Telegram 当前浅色、深色和自定义主题；随主题事件更新并在分离时注销 | FutoonThemeBridge、FutoonTheme |
| 悬浮容器 | 默认约 85% 可用高度，全屏 / 收起，复用页标题栏，紧凑会话摘要，五业务 Tab | FutoonPanel、NativePanelView、ModernComponents |
| 商户 | 多个 TGID 命中由用户选定；状态 0 / 1 / 2 分别显示；编辑改动核对；CNY 资金操作标明币种 | PanelWorkflows、WorkflowScreens、MerchantProfileScreen |
| 付款凭证 | 订单摘要、三列缩略图、图片编辑和移除、说明字数、手动选择收件人与 Telegram 预览 | NativePanelView、FutoonPanel |
| 开户 | 分组字段、填写 / 核对 / 结果，D0 与 D1 直接选择，已填文案不重复创建 | MerchantOpeningScreen |
| 更多 | 原生展示后台提供的页级和行级操作；查询与保存区分；操作前核对对象 | EmbeddedModules、NativeDocumentParser |
| 写入反馈 | 单次动作不自动重试或跟随重定向；未确认结果先刷新核对 | FutoonApiClient、AppViewModel、PaymentAdminScreen |

发送键和媒体入口、普通个人资料 TGID、原会话 Tag 渲染保持既有接线。一键回复与开户文案共用原生填入检查，保留既有草稿和选区；合并后超过 4096 字时拒绝填入，原草稿不变。付款凭证不会调用后台补单或修改支付状态。

弹层使用明确的入栈顺序，最上层接收点击及返回操作，解决嵌套确认页被编辑页遮挡的问题。保留创建显式 Compose recomposer、测量前与附着前准备，以及关闭时移除 Compose 子视图的既有生命周期修复。

设计建议中的拖拽图片排序、Tag 的 +N 折叠及键盘自动隐藏业务 Tab 没有加入；Tag 原渲染不变。没有新增提醒或工作台，Telegram 推送实现保持既有版本。

验证使用合成账号、商户、订单及凭证。253 项原生业务与界面测试通过，0 失败 / 错误 / 跳过；Lint 0 错误、28 警告；36 张渲染截图。另通过宿主 Java 发送与媒体入口、Android ICU 表达式、身份识别、HTTP 契约和悬浮几何回归。Android 17 真机、系统键盘行为及真实后台开户、结算、发送未实测。

构建与安装包校验信息见 BUILD.json。
