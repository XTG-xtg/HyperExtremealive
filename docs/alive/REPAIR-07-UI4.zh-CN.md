# 关于页作者信息：0.5-alive.7-ui.4

日期：2026-11-07。versionCode 19。基于 ui.3，仅修改关于页与作者信息。

## 界面

- 四位贡献者按用户指定顺序并排显示：HyperExtreme、整合维护者、HyperAlive、OpenAlive。
- 四个头像统一为 52dp 正圆、居中裁切；整列均可点击，有 Miuix 按压反馈与系统触感。
- 使用酷安公开名称，保留简短分工。窄屏或大字体时长名称省略，弹窗显示完整名称；
  无障碍描述始终包含完整项目与作者名称。
- 点击头像沿用原平台选择弹窗，显示对应姓名与项目，不直接误跳原作者。
  Frederic22 保留原有 Github / 哔哩哔哩 / 酷安，整合维护者和 OpenAlive 提供 Github / 酷安。
- 用户确认 HyperAlive 使用酷安默认头像且 Github 账号不可用，因此只显示酷安，
  不放禁用按钮、不猜测替代账号。
- 弹窗常驻组合树，保留退出动画；取消不跳转，重新选择另一人不会复用旧链接。
- 开源许可区保持原样。未修改壁纸服务、预览编辑、电源仲裁、系统时钟或防烧屏代码。

## 头像来源

头像内置在 `app/src/main/res/drawable-nodpi/contributor_*`，离线可用，无运行时头像请求。
下载时间为 2026-11-06。原始素材仅用于对应作者的身份展示，权利仍归原权利人，
不因项目代码协议而重新授权。

| 文件 | 来源 | SHA-256 |
| --- | --- | --- |
| `contributor_hyperextreme.jpg` | [酷安头像](https://avatar.coolapk.com/data/003/26/83/64_avatar_big.jpg) | `2316a7fa2184bc4bae92b195ffa8c5681bf45b036d3d14165a54e4a50e20d2f5` |
| `contributor_maintainer.jpg` | [酷安头像](https://avatar.coolapk.com/data/044/19/90/44_avatar_big.jpg) | `3eb5c35fa9f34bbd6c5c9099d1534f8c84a6291db63a6736dcddffb35f027bb6` |
| `contributor_openalive.jpg` | [酷安头像](https://avatar.coolapk.com/data/024/65/51/32_avatar_big.jpg) | `2c0ac625d397092eaa2012fac4a2064f4440751cdc710d9e7b7c9e72d2cc7d3e` |
| `contributor_hyperalive.png` | 用户提供 `20261106-235707-e494f7.gif`，单帧、200 × 200，无损转 PNG | 见 `VALIDATION-07-UI4.json` |

用户提供的原 GIF SHA-256：`797031331e836117162ee2deda6994b37e942cdae9b7b1afee191ebaa98dc3bd`。
四个酷安主页由用户指定；名称由对应公开分享页核对。Github 链接沿用项目源码及已确认 fork，
HyperAlive 不添加 Github 链接。

## 验证与边界

机器结果见 `VALIDATION-07-UI4.json`；截图与日志见 `ui4/`。

- 新增关于页 Compose / 数据测试：浅色、深色、320dp 窄屏及 1.6 倍字体、四人同排和
  至少 48dp 触控区域、完整无障碍名称、逐人逐平台链接、取消后换人、HyperAlive 仅酷安、
  四个内置头像解码。
- 运行全量单元测试和 Debug 构建，检查 APK 签名与 16KB zipalign。
- 截图来自 Robolectric，不是实体手机截图；未安装新包，也未启动外部酷安 / Github 客户端。
- 本轮无设备壁纸、电源键或 AOD 功耗测试；此前 ui.3 的 GPU 结果不冒充本轮验证。
- 未宣称 OpenAlive 全功能整合完成，通知光效与原版时钟编辑器的既有缺口不变。

## 发布

仅生成本地候选包。遵照「先不要推包」，本轮不创建 Github Release、不上传 APK、不推送标签。
