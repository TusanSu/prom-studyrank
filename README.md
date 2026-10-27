# Prom

**把每天的努力，记成一笔账。**
*A 281 KB Android app that turns daily effort into a ledger.*

[**▶ 在线试用 / Live demo**](https://tusansu.github.io/prom-studyrank/)　·　[下载 APK / Download](https://github.com/TusanSu/prom-studyrank/releases)　·　[iOS 工程](ios/README.md)　·　[MIT](LICENSE)

`Android 8.0+`　·　`iOS 16+`　·　`281 KB`

<p align="center">
  <img src="docs/shots/home.png" width="24%" alt="首页 / Home">
  <img src="docs/shots/checkin.png" width="24%" alt="打卡 / Check in">
  <img src="docs/shots/tiers.png" width="24%" alt="图鉴 / Gallery">
  <img src="docs/shots/ach.png" width="24%" alt="成就 / Achievements">
</p>

---

## 这个应用在解决什么 / What it is about

学习是件**回报很晚**的事。今天背的单词，三个月后才出现在卷子上；今天弄懂的题，可能一年都用不上。人的动力系统不擅长这种长周期——它要的是**今天做了，今天就有回应**。

多数人不是不努力，是努力太久没有反馈，于是算了。

Prom 在「努力」和「回报」之间，补上当天的那一次回应。

> Studying pays off late. Words memorised today show up on an exam three months from now.
> Human motivation is bad at that delay — it wants *something today, for what you did today*.
> Most people don't quit because they're lazy. They quit because nothing came back.
> Prom puts a small response back into that gap, every day.

## 它怎么工作 / How it works

**1 · 打卡 / Check in**

选科目、记收获（背单词 / 复习 / 刷题 / 啃难点 / 模考）、填时长，折算成学习点。三十秒一笔，睡前也能补。

*Pick a subject, tag what you did, enter the minutes. Converted into points. Thirty seconds, any time.*

学习点不是简单计时：

- 科目有不同权重（数理 ×1.2，语英 ×1.0）
- 同一科目一天内多次打卡，第三笔起收益递减
- 连续打卡有加成（首卡 ×1.1）
- 每日收益分三段：240 点以内全价，240–400 七五折，400–600 五折，600 封顶——学得多依然拿得多，只是边际递减
- 每日任务按「当日学习点的 5%」结算，三个全清约等于 ×1.15，零学习日不给点
- 周目标分 4 / 8 / 12 小时三档，逐档 +20 / +40 / +80

**2 · 七重境界 / Seven tiers**

学习点从「一重·天枢」累积到「七重·摇光」，对应北斗七星。每一重分 **下 / 中 / 上** 三小级。

段位有两套刻度，别混：**单科段位**看这一科自己的学习点（七重 6000，约等于单科一百小时）；**总段位**看各科平均（七重 3000）。同一份努力在总段位里被科目数摊薄，所以总段位的门槛单独设了一张表，否则默认六科时七重要三万六千点、得跑两年。

*Points climb from **Tier I · Tianshu** to **Tier VII · Yaoguang**, named after the seven stars of the Big Dipper. Each tier has three steps.*

**3 · 龙瞳 / The dragon's eyes**

同一张龙面，七种瞳色——灰、褐、青、碧、金、赤、白热。境界越高，眼睛越亮。

*One dragon face, seven eye colours — ash, amber, cyan, jade, gold, crimson, white-hot. The higher the tier, the brighter the gaze.*

**4 · 战报 / Report**

每天一页：投入多久、赚了多少、连续第几天。

**5 · 番茄钟 / Pomodoro**

25 分钟专注，结束后自动记进当天打卡。

**6 · 成就 / Achievements**

35 枚成就，另有 4 个隐藏彩蛋，触发后才会亮起。

**7 · 双学段 / Two school stages**

初中、高中两套独立体系：科目、满分、中考 / 高考倒计时都跟着切换，地区满分可预设。

## 隐私与体积 / Privacy & footprint

| 项 / Item | 情况 / Status |
|---|---|
| 安装包 / APK size | **281 KB** |
| 网络权限 / Network permission | **不申请 / none** |
| 账号 / Account | **不需要 / not required** |
| 云端 / Cloud | **没有 / none** |
| 广告与统计 / Ads & analytics | **没有 / none** |

数据全部存在手机本地。换设备时用「用户卡」生成一段密钥，粘贴到新设备即可恢复——**不经过任何服务器**。

*Everything stays on the device. To move to a new phone, export a key string and paste it — no server involved.*

## 技术 / Under the hood

- **主体**：单文件 WebView 应用，界面与逻辑都在 `app/src/main/assets/index.html`
- **原生层**：5 个 Java 类（WebView 宿主、每日提醒、番茄钟通知、开机恢复）
- **权限**：4 个，全部与提醒、振动有关
- **图形**：无位图素材包，徽章、龙面、图表全部由 SVG / Canvas 现画

*Single-file WebView app. Five Java classes for the native shell. Four permissions, all about reminders.
No bitmap asset packs — badges, dragon and charts are all drawn in SVG/Canvas at runtime.*

## 构建 / Build

需要 Android SDK（build-tools 34+，platform android-34）。

```sh
# 方式一：不依赖 Gradle
./build-apk.sh

# 方式二：Gradle
./gradlew assembleRelease
```

输出：`build/apk/StudyRankApp-12.9-release.apk`

### 签名 / Signing

签名密码不在仓库里。`build-apk.sh` 的取值顺序：

1. 环境变量 `PROM_KS_PASS`（推荐，CI 里用）；
2. `build/.keystore.pass`（首次运行自动生成 32 位随机密码，600 权限）。

`build/` 已加入 `.gitignore`，keystore 与密码文件都不会被提交。要换密码：

```sh
keytool -importkeystore -srckeystore 旧.jks -srcstorepass:env OLD \
  -destkeystore build/studyrank-release.jks -deststorepass:env NEW
```

**别重新生成 keystore**：安卓只认「同一把钥匙」的升级包，换钥匙意味着所有用户必须先卸载再装（数据全丢）。旧版本用过的 `StudyRank2026` 已经在 git 历史里，只能当作已泄露处理——12.9 起该 keystore 已改成随机密码，证书指纹不变（`fcd70af7…`），升级链路正常。

### iOS

界面完全复用同一个 `index.html`，外面是一层 WKWebView，原生只实现四个 JS 桥。

```sh
open ios/Prom.xcodeproj     # 选好 Team 后 ⌘R
```

命令行验证编译（不签名）：

```sh
cd ios
xcodebuild -project Prom.xcodeproj -target Prom \
  -sdk iphonesimulator -configuration Debug \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO \
  SYMROOT="$PWD/build/out" OBJROOT="$PWD/build/obj" build
```

细节与差异（剪贴板、相册、通知）见 [ios/README.md](ios/README.md)。

> 签名需要你自己的 keystore —— 仓库里不含任何密钥文件。
> *Signing needs your own keystore. No keys are included in this repo.*

## 目录 / Layout

```
app/
├─ build.gradle                    versionCode 48 / versionName 12.9
└─ src/main/
   ├─ AndroidManifest.xml          4 个权限
   ├─ assets/index.html            界面与逻辑（单文件，5406 行）
   └─ java/com/studyrank/app/      5 个 Java 类
ios/                               iOS 工程（WKWebView 外壳）
├─ Prom.xcodeproj
└─ Prom/
   ├─ PromApp.swift                App 入口 + 五个 JS 桥
   └─ www/index.html               与安卓共用的网页
docs/                              GitHub Pages 在线演示
├─ index.html                      落地页（手机框内嵌 App）
└─ app.html                        App 本体，由 docs/build-pwa.sh 生成
tools/
└─ smoke-test.js                   零依赖冒烟测试（node，17 条断言）
```

> `app/src/main/assets/index.html` 是唯一真源。改完记得同步 iOS 那份：
> `cp app/src/main/assets/index.html ios/Prom/www/index.html`
>
> 改完界面先跑一遍测试：`node tools/smoke-test.js app/src/main/assets/index.html`

## 版本 / Version

**12.9** — 学习点经济重做：总段位独立刻度、日上限 240→三段软曲线、任务改乘数、周目标阶梯化。

- **总段位刻度**：`AVG_RANKS`（0/150/400/750/1250/2000/3000）单独一套，只作用在「各科平均」上；单科 `RANKS`（七重 6000）不动。默认 6 科时七重从 36000 总点降到 18000；
- **日上限**：240 硬截断 → 240 / 400 / 600 三段（全价 → 七五折 → 五折 → 封顶）。学 8 小时不再只拿 240 点；
- **任务奖励**：固定 10–25 点 → 当日学习点的 5%（3–20 夹紧），三个全清约 ×1.15，不再出现「点任务比多学一小时划算」；
- **周目标**：单档 12 小时 → 4 / 8 / 12 小时三档，逐档 +20 / +40 / +80；
- **测试**：冒烟测试 17 → 27 条断言，覆盖两套刻度、三段上限、任务乘数、周目标逐档。

*Economy rebalance: separate total-rank scale, three-stage soft daily cap (240/400/600), quest rewards as a 5 % multiplier of the day's points, tiered weekly goals. Smoke test grew to 27 assertions.*

**12.8** — 境界更名「一重·天枢 → 七重·摇光」，徽章加入龙瞳配色；新增 iOS 工程。

发布后又在同版本号内做了一轮优化：

- **存储**：头像拆到独立键、`save()` 300 ms 去抖、有原生桥时不再双写 `localStorage`、存档读入统一清洗（脏日期/NaN 学习点直接丢弃）；
- **备份**：新增「导出 / 导入备份文件」——Android 写「下载/Prom」，iOS 走系统分享面板；导入按 id 合并（不再整体覆盖）；同步密钥升级为带 crc32 校验的 `PROM2`，旧密钥仍可读；
- **修复**：iOS 前台通知不弹（缺 `UNUserNotificationCenterDelegate`）、iOS「每日提醒」开关每次重置、Android 授权结果不回传、`onPause` 落盘顺序反了、周目标奖励不落盘、成就「任务狂」「回来了」永远解锁不了、跨天不刷新、「清空全部数据」清不干净（奖励点数残留）、iOS 网页副本落后 82 行；
- **性能**：徽章 HTML 缓存、成就进度一趟算一次、学习点汇总缓存。

*Storage split + debounced writes, JSON backup import/export with id-based merge, CRC-checked sync keys, iOS/Android notification fixes, render caching, and a dependency-free smoke test (17 assertions).*


## 许可 / License

[MIT](LICENSE) © 2026 Tusan苏苏
