# Prom · 学习段位

一个 281 KB 的安卓学习打卡应用。把每天的努力记下来，换算成学习点，累积成七重段位。

**当前版本**：12.8（versionCode 47）　·　Android 8.0+　·　作者：Tusan苏苏

---

## 这是什么

学习是件回报很晚的事。今天背的单词，三个月后才出现在卷子上；今天弄懂的题，可能一年都用不上。大脑不擅长这种长周期——它要的是**今天做了，今天就有回应**。

Prom 在「努力」和「回报」之间，补上当天的那一次回应。

## 功能

- **打卡**：选科目、记收获、填时长，按实际投入换算学习点
- **段位**：一重·天枢 → 七重·摇光，七级；每级分 下 / 中 / 上
- **图鉴**：七枚龙瞳徽章，瞳色随段位变化
- **番茄钟**：25 分钟专注，结束自动入账
- **成就**：35 枚 + 4 个隐藏彩蛋
- **战报**：每天一页，投入、获得、连续天数
- **双学段**：初中 / 高中两套体系，含中高考倒计时、地区满分预设
- **离线**：不申请网络权限，数据只在本机；换机用密钥迁移

## 构建

需要 Android SDK（build-tools 34+、platform android-34）。

### 方式一：脚本（不依赖 Gradle）

```sh
./build-apk.sh
```

输出：`build/apk/StudyRankApp-12.8-release.apk`

脚本流程：`aapt2` 编译资源 → `javac` 编译 Java → `d8` 转 dex → 打包 → `zipalign` → `apksigner` 签名。
签名需要你自己的 keystore，脚本里的路径请替换。

### 方式二：Gradle

```sh
./gradlew assembleRelease
```

## 技术结构

- **主体**：单文件 WebView 应用，界面与逻辑都在 `app/src/main/assets/index.html`
- **原生层**：5 个 Java 类
  - `MainActivity` — WebView 宿主与存储桥
  - `ReminderScheduler` / `ReminderReceiver` — 每日提醒
  - `PomodoroReceiver` — 番茄钟通知
  - `BootReceiver` — 开机恢复提醒
- **权限**：只有 4 个，全部与提醒、振动有关
  `VIBRATE`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`、`SCHEDULE_EXACT_ALARM`
- **体积**：无第三方依赖，图形全部由 SVG / Canvas 现画

## 许可

见 [LICENSE](LICENSE)。
