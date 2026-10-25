# v0.11.0 — iOS PDF 原生界面对齐

用户要求：以已提供的iOS社区PDF作为视觉基准，一次整合交付，不发布中间APK。具体映射见IOS-REFERENCE.md。应用名「短信」、首页「Futoon chat」和所有Futoon操作保持不变。删除的通话、语音录制、拍照、联系人、定位和视频编辑不恢复。

| 范围 | 涉及页面与组件 | 交付检查 |
| --- | --- | --- |
| 相册/文件 | ChatAttachAlertPhotoLayout、DocumentLayout、PhotoPicker/AlbumPicker、PhotoAttachPhotoCell、SharedDocumentCell | 网格、相册入口、标题、选择状态、文件行、预览/发送；凭证选图和手动收件人不变 |
| 资料详情/群资料 | ProfileActivity、TextDetailCell、UserCell、SharedMediaLayout、SharedLink/Document/AudioCell | 信息行、成员、媒体/文件分区、Telegram ID复制、头像查看、群权限 |
| 设置二级页 | Privacy/Notifications/Data/Theme/Devices及共用TextSettings/Detail/Check/Radio/Header/InfoCell | 原生分区、图标、行高、字体、开关/单选、表单与确认；保留原事件 |
| 复杂消息 | ChatMessageCell、MessageDrawable、ReplyMessageLine、BotKeyboardView、Shared*Cell | 媒体/文件/引用/转发/机器人按钮，收发、选中、已读、错误色和无障碍 |
| 菜单/操作态 | ActionBarMenuSubItem/PopupWindow、AlertDialog、BottomSheet、ShareAlert、选择顶栏 | 长按、多选、转发、确认/取消、弹层及关闭恢复；不增加自动发送 |
| 其他原生页 | DialogsSearch/SearchField、Login/TwoStep、GroupCreate/ChatEdit/Topics/CountrySelect | 搜索、登录、群话题管理使用同一标题/输入/列表/按钮规则，实际能力不删 |
| Futoon全部页面 | NativePanelView、ChatToolsView、ModernComponents、统一Compose主题、各业务表单/详情 | 悬浮窗、快捷回复、设置、识别、订单、商户、结算、更多使用一致控件；ID/路由/字段/回调不变 |
| 动画与适配 | 原生Popup/Sheet/页面转场，Compose对话框与导航，RTL/字体/窗口/IME | 进入/退出及选择反馈统一，尊重系统减少动画；窄屏、横屏、大字体、键盘、防重入 |

系统自带的照片/文件授权窗口由Android绘制；可控范围为应用内页面。WhatsApp没有的通道/话题/业务功能按同一视觉规则呈现，不删除功能。参考APK包含动态版本分支，静态资源只能证明指定尺寸/颜色；不把合成截图称作已登录WhatsApp/Android17真机像素对比。

验收：各行必须有真实代码改动和检查证据；业务/权限/生命周期回归、完整宿主编译、APK签名/身份/权限核验全部通过后，只交付一个新APK及对应映射记录。
