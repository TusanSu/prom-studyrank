//
//  PromApp.swift
//  Prom · 把每天的努力记成一笔账
//
//  iOS 外壳：一个 WKWebView + 四个 JS 桥（PromStore / PromUI / PromNotify / PromImage），
//  与安卓端 MainActivity 暴露的接口保持一致，界面完全复用 www/index.html。
//

import SwiftUI
import WebKit
import UserNotifications
import Photos
import UIKit
import UniformTypeIdentifiers

// MARK: - App

@main
struct PromApp: App {
    init() {
        // 前台也要能弹出提醒：没有 delegate 时，应用在前台收到的本地通知会被系统静默丢弃，
        // 番茄钟结束、测试通知都会「什么都不发生」。
        UNUserNotificationCenter.current().delegate = NotifDelegate.shared
    }

    var body: some Scene {
        WindowGroup {
            PromWebView()
                .ignoresSafeArea()
                .preferredColorScheme(nil)
        }
    }
}

// MARK: - 通知回调

final class NotifDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotifDelegate()

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler:
                                    @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }
}

// MARK: - WebView

struct PromWebView: UIViewRepresentable {
    func makeCoordinator() -> Bridge { Bridge() }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        let ucc = WKUserContentController()
        ucc.addUserScript(WKUserScript(source: Bridge.shim,
                                       injectionTime: .atDocumentStart,
                                       forMainFrameOnly: true))
        ucc.add(context.coordinator, name: "prom")
        cfg.userContentController = ucc
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []

        let wv = WKWebView(frame: .zero, configuration: cfg)
        wv.scrollView.contentInsetAdjustmentBehavior = .never
        wv.scrollView.bounces = false
        wv.isOpaque = false
        wv.backgroundColor = UIColor(red: 0.043, green: 0.043, blue: 0.047, alpha: 1)
        wv.allowsBackForwardNavigationGestures = false

        wv.navigationDelegate = context.coordinator
        context.coordinator.webView = wv

        if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "www") {
            wv.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

// MARK: - Bridge

final class Bridge: NSObject, WKScriptMessageHandler, WKNavigationDelegate {

    weak var webView: WKWebView?
    private let dailyID = "prom.daily"
    private let pomoID = "prom.pomodoro"

    /// 注入的 JS 垫片：把安卓的四个 @JavascriptInterface 对象在 iOS 上补齐。
    /// PromStore 直接用 localStorage —— 同步、可靠，不需要跨进程调用。
    static let shim = """
    (function () {
      if (window.__promIOS) return;
      window.__promIOS = true;

      function post(type, extra) {
        try {
          var msg = { type: type };
          if (extra) { for (var k in extra) { if (extra.hasOwnProperty(k)) msg[k] = extra[k]; } }
          window.webkit.messageHandlers.prom.postMessage(msg);
        } catch (e) {}
      }

      window.PromStore = {
        get: function (k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
        set: function (k, v) { try { localStorage.setItem(k, v); } catch (e) {} },
        remove: function (k) { try { localStorage.removeItem(k); } catch (e) {} },
        flush: function () {}
      };

      window.PromUI = {
        tap: function (kind) { post('tap', { kind: kind || 'light' }); },
        setTheme: function (mode) { post('setTheme', { mode: mode }); },
        copy: function (text) { post('copy', { text: text }); },
        readClipboard: function () { return ''; }
      };

      window.PromNotify = {
        status: function () {
          // 之前这里只回 granted，页面读不到 on / h / m，
          // 于是每次启动开关都显示成「关闭」，时间也回到 21:00。
          var on = false, h = 21, m = 0;
          try {
            var raw = localStorage.getItem('prom.remind');
            if (raw) {
              var s = JSON.parse(raw);
              on = !!s.on;
              if (typeof s.h === 'number') h = s.h;
              if (typeof s.m === 'number') m = s.m;
            }
          } catch (e) {}
          return JSON.stringify({ on: on, h: h, m: m, granted: !!window.__promNotifGranted });
        },
        set: function (on, hour, min) {
          try {
            localStorage.setItem('prom.remind', JSON.stringify({ on: !!on, h: hour, m: min }));
          } catch (e) {}
          post('notifySet', { on: !!on, hour: hour, min: min });
        },
        refresh: function () { post('notifyRefresh'); },
        test: function () { post('notifyTest'); },
        pomoStart: function (seconds, title, text) {
          post('pomoStart', { seconds: seconds, title: title || '番茄钟', text: text || '' });
        },
        pomoCancel: function () { post('pomoCancel'); },
        openSettings: function () { post('openSettings'); },
        requestPermission: function () { post('requestPermission'); }
      };

      window.PromImage = {
        save: function (dataUrl, filename) {
          post('saveImage', { data: dataUrl, name: filename || 'prom.png' });
          return '已保存到相册';
        }
      };

      window.PromFile = {
        exportText: function (text, name) {
          post('exportText', { text: text, name: name || 'prom-backup.json' });
          return '已打开分享面板，存到「文件」即可';
        },
        pickText: function () { post('pickText'); }
      };
    })();
    """

    // MARK: 页面就绪后同步通知状态

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        syncNotifState()
    }

    private func syncNotifState() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let granted = (settings.authorizationStatus == .authorized
                           || settings.authorizationStatus == .provisional)
            DispatchQueue.main.async {
                // 顺带回写页面，修正「通知权限」那一行（首屏渲染时状态还没查完）
                self.webView?.evaluateJavaScript(
                    "window.__promNotifGranted = \(granted); window.__promPerm && window.__promPerm(\(granted));",
                    completionHandler: nil)
            }
        }
    }

    // MARK: 消息分发

    func userContentController(_ ucc: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any],
              let type = body["type"] as? String else { return }

        switch type {
        case "tap":            haptic(body["kind"] as? String ?? "light")
        case "setTheme":       setTheme(body["mode"] as? String ?? "system")
        case "copy":           copyToPasteboard(body["text"] as? String ?? "")
        case "notifySet":      scheduleDaily(on: body["on"] as? Bool ?? false,
                                             hour: body["hour"] as? Int ?? 21,
                                             min: body["min"] as? Int ?? 0)
        case "notifyRefresh":
            scheduleDaily(on: nil, hour: nil, min: nil)
            syncNotifState()
        case "notifyTest":     sendTest()
        case "pomoStart":      pomodoro(seconds: body["seconds"] as? Int ?? 1500,
                                        title: body["title"] as? String ?? "番茄钟",
                                        text: body["text"] as? String ?? "")
        case "pomoCancel":     cancelPomodoro()
        case "openSettings":   openSystemSettings()
        case "requestPermission": requestAuth()
        case "saveImage":      saveImage(dataURL: body["data"] as? String ?? "")
        case "exportText":     shareText(body["text"] as? String ?? "",
                                         name: body["name"] as? String ?? "prom-backup.json")
        case "pickText":       pickBackupFile()
        default: break
        }
    }

    // MARK: 触感

    private func haptic(_ kind: String) {
        DispatchQueue.main.async {
            switch kind {
            case "hard", "heavy":
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            case "medium":
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            case "success":
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            case "warning":
                UINotificationFeedbackGenerator().notificationOccurred(.warning)
            default:
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
    }

    // MARK: 主题 / 剪贴板

    private func setTheme(_ mode: String) {
        DispatchQueue.main.async {
            switch mode {
            case "dark":  self.webView?.overrideUserInterfaceStyle = .dark
            case "light": self.webView?.overrideUserInterfaceStyle = .light
            default:      self.webView?.overrideUserInterfaceStyle = .unspecified
            }
        }
    }

    private func copyToPasteboard(_ text: String) {
        UIPasteboard.general.string = text
    }

    // MARK: 通知

    private func requestAuth(_ done: ((Bool) -> Void)? = nil) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            DispatchQueue.main.async {
                self.webView?.evaluateJavaScript(
                    "window.__promNotifGranted = \(ok); window.__promPerm && window.__promPerm(\(ok));",
                    completionHandler: nil)
                done?(ok)
            }
        }
    }

    private func scheduleDaily(on: Bool?, hour: Int?, min: Int?) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [dailyID])

        // on == nil 表示「刷新」：沿用上次保存的设置
        var enabled = on
        var h = hour
        var m = min
        if enabled == nil {
            let d = UserDefaults.standard
            enabled = d.bool(forKey: "prom.remind.on")
            h = d.object(forKey: "prom.remind.hour") as? Int ?? 21
            m = d.object(forKey: "prom.remind.min") as? Int ?? 0
        } else {
            let d = UserDefaults.standard
            d.set(enabled!, forKey: "prom.remind.on")
            d.set(h, forKey: "prom.remind.hour")
            d.set(m, forKey: "prom.remind.min")
        }
        guard enabled == true else { return }

        requestAuth { ok in
            guard ok else { return }
            var comps = DateComponents()
            comps.hour = h
            comps.minute = m
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
            let content = UNMutableNotificationContent()
            content.title = "Prom"
            content.body = "今天记一笔？三十秒就好。"
            content.sound = .default
            center.add(UNNotificationRequest(identifier: self.dailyID, content: content, trigger: trigger))
        }
    }

    private func sendTest() {
        requestAuth { ok in
            guard ok else { return }
            let content = UNMutableNotificationContent()
            content.title = "Prom"
            content.body = "提醒长这样。每天这个时间我会来找你。"
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
            UNUserNotificationCenter.current()
                .add(UNNotificationRequest(identifier: "prom.test", content: content, trigger: trigger))
        }
    }

    private func pomodoro(seconds: Int, title: String, text: String) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [pomoID])
        requestAuth { ok in
            guard ok else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = text.isEmpty ? "时间到，去记一笔。" : text
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(max(1, seconds)),
                                                            repeats: false)
            center.add(UNNotificationRequest(identifier: self.pomoID, content: content, trigger: trigger))
        }
    }

    private func cancelPomodoro() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [pomoID])
    }

    private func openSystemSettings() {
        DispatchQueue.main.async {
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
        }
    }

    // MARK: 保存图片到相册

    private func saveImage(dataURL: String) {
        guard let comma = dataURL.firstIndex(of: ",") else { return }
        let b64 = String(dataURL[dataURL.index(after: comma)...])
        guard let data = Data(base64Encoded: b64), let img = UIImage(data: data) else { return }

        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else { return }
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: img)
            }, completionHandler: nil)
        }
    }

    // MARK: 备份文件（导出 / 导入）

    /// 导出：写进临时目录再交给系统分享面板（存到「文件」或发给另一台设备）
    private func shareText(_ text: String, name: String) {
        let safeName = name.isEmpty ? "prom-backup.json" : name
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(safeName)
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            return
        }
        DispatchQueue.main.async {
            guard let root = Bridge.topViewController() else { return }
            let ac = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            if let pop = ac.popoverPresentationController {
                pop.sourceView = root.view
                pop.sourceRect = CGRect(x: root.view.bounds.midX, y: root.view.bounds.midY, width: 0, height: 0)
                pop.permittedArrowDirections = []
            }
            root.present(ac, animated: true)
        }
    }

    /// 导入：系统文件选择器 → 读成 base64 → 回传页面（纯 ASCII，不用操心转义）
    private func pickBackupFile() {
        DispatchQueue.main.async {
            guard let root = Bridge.topViewController() else { return }
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .data],
                                                         asCopy: true)
            picker.delegate = self
            picker.allowsMultipleSelection = false
            root.present(picker, animated: true)
        }
    }

    /// 找到当前最上层的控制器（分享面板 / 文件选择器都要从它 present）
    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

// MARK: - 文件选择回调

extension Bridge: UIDocumentPickerDelegate {
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            webView?.evaluateJavaScript("window.__promToast && window.__promToast('读取备份文件失败');",
                                        completionHandler: nil)
            return
        }
        let b64 = data.base64EncodedString()
        webView?.evaluateJavaScript("window.__promImportB64 && window.__promImportB64('\(b64)');",
                                    completionHandler: nil)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {}
}
