package com.studyrank.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.NotificationManager;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 学习段位打卡 · WebView 壳
 * 页面在 app/src/main/assets/index.html，离线加载，无网络权限。
 *
 * 持久化说明：file:// 页面里的 localStorage 只保存在内存中，应用被系统回收后会清空，
 * 因此这里通过 addJavascriptInterface 暴露 PromStore（SharedPreferences），
 * 由页面优先写入原生存储，localStorage 仅作为兜底。
 */
public class MainActivity extends Activity {

    private static final String PREFS = "prom_store";

    private WebView webView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyStatusBar(false);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) ReminderReceiver.channel(this, nm);
        } catch (Exception ignored) {
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        // 关掉一切"网页感"：缩放、滚动条、长按菜单、过滚动光晕
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });

        // 必须早于 loadUrl 注入，页面内的首屏脚本才能读到主题
        webView.addJavascriptInterface(new StoreBridge(), "PromStore");
        webView.addJavascriptInterface(new ImageBridge(), "PromImage");
        webView.addJavascriptInterface(new UiBridge(), "PromUI");
        webView.addJavascriptInterface(new NotifyBridge(), "PromNotify");

        // 背景色与页面一致，冷启动不闪白屏
        webView.setBackgroundColor(0xFFEEF1F9);
        webView.loadUrl("file:///android_asset/index.html");

        // 提醒排程在应用启动时校准一次（时间可能被改过）
        ReminderScheduler.schedule(this);
    }

    /** 系统状态栏颜色与图标明暗，跟随页面主题 */
    private void applyStatusBar(boolean dark) {
        try {
            Window w = getWindow();
            w.setStatusBarColor(dark ? 0xFF000000 : 0xFFFFFFFF);
            w.setNavigationBarColor(dark ? 0xFF000000 : 0xFFFFFFFF);
            View dv = w.getDecorView();
            int flags = dv.getSystemUiVisibility();
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            dv.setSystemUiVisibility(flags);
        } catch (Exception ignored) {
        }
    }

    private static final int REQ_PICK_AVATAR = 41;

    /** 选头像：系统图库 → 压缩 → base64 回传给页面 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_AVATAR) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            callJs("window.__promAvatarCancel && window.__promAvatarCancel();");
            return;
        }
        try {
            Uri uri = data.getData();
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();

            int sample = 1;
            int max = Math.max(o.outWidth, o.outHeight);
            while (max / sample > 640) sample *= 2;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o2);
            if (in != null) in.close();
            if (bmp == null) {
                callJs("window.__promAvatarCancel && window.__promAvatarCancel();");
                return;
            }

            int side = Math.min(bmp.getWidth(), bmp.getHeight());
            Bitmap square = Bitmap.createBitmap(bmp, (bmp.getWidth() - side) / 2, (bmp.getHeight() - side) / 2, side, side);
            Bitmap scaled = Bitmap.createScaledBitmap(square, 220, 220, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            callJs("window.__promAvatar && window.__promAvatar('data:image/jpeg;base64," + b64 + "');");
        } catch (Exception e) {
            callJs("window.__promAvatarCancel && window.__promAvatarCancel();");
        }
    }

    private void callJs(final String js) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (webView != null) webView.evaluateJavascript(js, null);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static final int REQ_NOTIFY = 4211;

    /** 每日提醒：状态 / 开关 / 测试 */
    public class NotifyBridge {

        @JavascriptInterface
        public String status() {
            try {
                SharedPreferences p = ReminderScheduler.prefs(MainActivity.this);
                boolean on = p.getBoolean(ReminderScheduler.KEY_ON, false);
                int h = p.getInt(ReminderScheduler.KEY_HOUR, 21);
                int m = p.getInt(ReminderScheduler.KEY_MIN, 0);
                boolean granted = true;
                if (Build.VERSION.SDK_INT >= 33) {
                    granted = checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                }
                return "{\"on\":" + (on ? "true" : "false") + ",\"h\":" + h + ",\"m\":" + m
                    + ",\"granted\":" + (granted ? "true" : "false") + "}";
            } catch (Exception e) {
                return "{\"on\":false,\"h\":21,\"m\":0,\"granted\":false}";
            }
        }

        @JavascriptInterface
        public void set(final boolean on, final int hour, final int min) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ReminderScheduler.set(MainActivity.this, on, hour, min);
                }
            });
        }

        /** 打卡后调用：今天已打卡就把今天的闹钟推到明天 */
        @JavascriptInterface
        public void refresh() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ReminderScheduler.schedule(MainActivity.this);
                }
            });
        }

        @JavascriptInterface
        public void test() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
                        return;
                    }
                    ReminderReceiver.post(MainActivity.this);
                }
            });
        }

        /** 番茄钟：到点发系统通知（App 在后台也能响） */
        @JavascriptInterface
        public void pomoStart(final int seconds, final String title, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (am == null) return;
                        Intent i = new Intent(MainActivity.this, PomodoroReceiver.class);
                        i.putExtra("title", title);
                        i.putExtra("text", text);
                        int f = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
                        PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, 4301, i, f);
                        long at = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
                        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                        } else if (Build.VERSION.SDK_INT >= 23) {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                        } else {
                            am.set(AlarmManager.RTC_WAKEUP, at, pi);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void pomoCancel() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (am == null) return;
                        Intent i = new Intent(MainActivity.this, PomodoroReceiver.class);
                        int f = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
                        PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, 4301, i, f);
                        am.cancel(pi);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        /** 跳到系统的本应用通知设置页（权限被永久拒绝时用） */
        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            Intent i2 = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i2.setData(Uri.parse("package:" + getPackageName()));
                            i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i2);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
                    }
                }
            });
        }
    }

    /** 页面调用：状态栏主题 + 轻微触感 */
    public class UiBridge {

        @JavascriptInterface
        public void setTheme(String mode) {
            final boolean dark = "dark".equals(mode);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyStatusBar(dark);
                }
            });
        }

        /** 复制文本到剪贴板 */
        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Prom", text));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        /** 读取剪贴板（用于自动填入同步密钥） */
        @JavascriptInterface
        public String readClipboard() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null || !cm.hasPrimaryClip()) return "";
                ClipData clip = cm.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) return "";
                CharSequence t = clip.getItemAt(0).coerceToText(getApplicationContext());
                return t == null ? "" : t.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void tap(String kind) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                if (Build.VERSION.SDK_INT >= 29) {
                    int effect = "hard".equals(kind) ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_TICK;
                    v.vibrate(VibrationEffect.createPredefined(effect));
                } else {
                    v.vibrate("hard".equals(kind) ? 24 : 10);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** 页面调用的持久化接口：PromStore.get / set / remove / flush */
    public class StoreBridge {

        @JavascriptInterface
        public String get(String key) {
            try {
                return prefs.getString(key, null);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void set(String key, String value) {
            try {
                prefs.edit().putString(key, value).apply();
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void remove(String key) {
            try {
                prefs.edit().remove(key).apply();
            } catch (Exception ignored) {
            }
        }

        /** 立即落盘（切后台时调用） */
        @JavascriptInterface
        public void flush() {
            try {
                prefs.edit().commit();
            } catch (Exception ignored) {
            }
        }
    }

    /** 保存战报图片：API 29+ 直接写入相册，旧版本写入应用图片目录 */
    public class ImageBridge {

        /** 打开系统图库选头像 */
        @JavascriptInterface
        public void pick() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(i, REQ_PICK_AVATAR);
                    } catch (Exception e) {
                        callJs("window.__promAvatarCancel && window.__promAvatarCancel();");
                    }
                }
            });
        }

        @JavascriptInterface
        public String save(String dataUrl, String filename) {
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) return "保存失败：数据格式异常";
                byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Prom");
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "保存失败：无法创建相册文件";
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os == null) return "保存失败：无法写入相册";
                    os.write(bytes);
                    os.close();
                    return "已保存到相册 Pictures/Prom";
                }

                File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (dir == null) return "保存失败：存储不可用";
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, filename);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(bytes);
                fos.close();
                return "已保存到 " + out.getAbsolutePath();
            } catch (Exception e) {
                return "保存失败：" + e.getMessage();
            }
        }
    }

    @Override
    protected void onPause() {
        // 切后台时让页面把最新状态写进 SharedPreferences，避免被回收时丢数据
        if (webView != null) {
            try {
                webView.evaluateJavascript("window.__promFlush && window.__promFlush();", null);
            } catch (Exception ignored) {
            }
        }
        if (prefs != null) {
            prefs.edit().commit();
        }
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 返回键：页面可回退时先回退，否则走系统默认（退出应用）
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.evaluateJavascript("window.__promFlush && window.__promFlush();", null);
            } catch (Exception ignored) {
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
