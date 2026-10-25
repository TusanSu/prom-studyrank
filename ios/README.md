# Prom for iOS

同一个 `index.html`，外面套一层 WKWebView。原生部分只做安卓端那四个桥的等价实现。

## 打开方式

```sh
open ios/Prom.xcodeproj
```

1. 选中左侧 **Prom** target → **Signing & Capabilities**
2. **Team** 选你的 Apple ID（没有的话：Xcode → Settings → Accounts → 加 Apple ID，免费账号也能装真机，7 天有效）
3. Bundle Identifier 若冲突，改成 `com.你的名字.prom`
4. 连上 iPhone，选它为运行目标，按 ⌘R

## 命令行编译（不签名，仅验证）

```sh
cd ios
xcodebuild -project Prom.xcodeproj -target Prom \
  -sdk iphonesimulator -configuration Debug \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO \
  SYMROOT="$PWD/build/out" OBJROOT="$PWD/build/obj" build
```

产物：`build/out/Debug-iphonesimulator/Prom.app`（约 624 KB）

## 结构

```
ios/
├─ Prom.xcodeproj
└─ Prom/
   ├─ PromApp.swift      App 入口 + WKWebView + 四个 JS 桥
   ├─ Assets.xcassets/   App 图标（1024，含 dark / tinted 变体）
   └─ www/               与安卓共用的网页（index.html、龙图、收款码）
```

`www` 是**文件夹引用**：改 `app/src/main/assets/index.html` 后，把它复制到 `ios/Prom/www/` 即可，不用改工程文件。

## 图标

安卓的自适应图标是「白底 + 黑色火苗」，iOS 这边沿用了同一套视觉：

| 变体 | 文件 | 用途 |
|---|---|---|
| 默认 | `icon-1024.png` | 白底黑火苗，与安卓一致 |
| 深色 | `icon-1024-dark.png` | 近黑底（`#0B0B0C`）+ 米白火苗，iOS 18 深色模式 |
| 着色 | `icon-1024-tinted.png` | 透明底黑字形，交系统按用户主题染色 |

图形取自 `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`，
裁掉自适应图标的留白后按 64.5% 高度居中——比安卓 108dp 画布更满，避免 iOS 上显得太小。


## 出 IPA（未签名）

没有开发者账号也能出包——只是别人装之前要自己签一次。

```sh
cd ios
./build-ipa.sh
```

产物：`Prom-12.8-unsigned.ipa`（约 380 KB，arm64，iOS 16+）

### 别人怎么装

| 工具 | 需要 | 说明 |
|---|---|---|
| **Sideloadly** | Windows / Mac + 数据线 | 拖入 IPA，填自己的 Apple ID，签完直接装。**7 天到期** |
| **AltStore** | 电脑装 AltServer 一次 | 之后同一 Wi-Fi 下自动续签，不用每次插线 |
| **SideStore** | 首次需电脑配对 | 之后可在手机上自动续签 |
| **ESign / 爱思助手** | — | 用现成证书签，但网上流传的企业证书随时吊销，**不建议** |

免费 Apple ID 的限制：**7 天**有效期、最多 3 个 App、不能推送通知（本地通知可以）。

### 为什么是「未签名」

签名需要 `Apple Development` 证书 + 对应设备的 provisioning profile。
这两样都绑定付费开发者账号，本仓库不含任何证书，因此在命令行只能产出未签名包。

要出**已签名**的包，在 Xcode 里配好 Team 后：

```sh
xcodebuild -project Prom.xcodeproj -scheme Prom -sdk iphoneos \
  -configuration Release -archivePath build/Prom.xcarchive archive
xcodebuild -exportArchive -archivePath build/Prom.xcarchive \
  -exportPath build/ipa -exportOptionsPlist ExportOptions.plist
```

## 桥接对照

| 安卓 (`@JavascriptInterface`) | iOS 实现 | 说明 |
|---|---|---|
| `PromStore.get/set/remove/flush` | 注入 JS，直接读写 `localStorage` | 同步接口，WKWebView 无法同步回调原生，用 localStorage 最稳 |
| `PromUI.tap(kind)` | `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` | 轻/中/重、成功/警告 |
| `PromUI.setTheme(mode)` | `overrideUserInterfaceStyle` | dark / light / system |
| `PromUI.copy(text)` | `UIPasteboard.general.string` | |
| `PromUI.readClipboard()` | 返回空字符串 | WKWebView 不支持同步读剪贴板，改为长按输入框粘贴 |
| `PromNotify.set/refresh/test` | `UNCalendarNotificationTrigger` | 每日提醒，设置存 UserDefaults |
| `PromNotify.pomoStart/pomoCancel` | `UNTimeIntervalNotificationTrigger` | 番茄钟到点通知 |
| `PromNotify.status()` | 返回 `{"granted":bool}` | 授权状态由原生回写 `window.__promNotifGranted` |
| `PromImage.pick()` | **未实现** | 网页自带的 `<input type="file">` 兜底，走系统相册 |
| `PromImage.save()` | 写入相册（`PHPhotoLibrary`） | 需要相册写入权限说明 |

## 与安卓的已知差异

- **网络权限**：iOS 无此概念，但 App 不发起任何请求，行为一致
- **剪贴板导入密钥**：iOS 上请长按输入框手动粘贴
- **开机恢复提醒**：iOS 不需要，`UNCalendarNotificationTrigger(repeats: true)` 本身就会重复
- **返回键**：iOS 无实体返回键，全部依赖界面内的按钮

## 上架前还要补的

- [x] App 图标（已从安卓 `ic_launcher_foreground.png` 生成，含浅色/深色/着色三种）
- [x] 出未签名 IPA（`./build-ipa.sh`）
- [ ] 启动图（现由 `UILaunchScreen_Generation` 生成纯色）
- [ ] 隐私清单 `PrivacyInfo.xcprivacy`（App Store 从 2024 起要求）
- [ ] App Store 审核所需的截图、描述、年龄分级
