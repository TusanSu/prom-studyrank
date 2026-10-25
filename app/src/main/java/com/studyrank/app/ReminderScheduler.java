package com.studyrank.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 每日打卡提醒：计算下一次触发时刻并用 AlarmManager 排程 */
public class ReminderScheduler {

    public static final String PREFS = "prom_reminder";
    public static final String KEY_ON = "on";
    public static final String KEY_HOUR = "hour";
    public static final String KEY_MIN = "min";

    private static final int REQ = 4201;
    private static final String APP_PREFS = "prom_store";
    private static final String STATE_KEY = "prom.state.v1";

    static String dayKey(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    /** 从页面存下的状态里取出所有打卡日期 */
    static Set<String> recordDays(Context c) {
        Set<String> set = new HashSet<>();
        try {
            String raw = c.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).getString(STATE_KEY, null);
            if (raw == null) return set;
            JSONObject o = new JSONObject(raw);
            JSONArray arr = o.optJSONArray("records");
            if (arr == null) return set;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.optJSONObject(i);
                if (r == null) continue;
                String d = r.optString("date", "");
                if (d.length() >= 10) set.add(d.substring(0, 10));
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    static boolean todayDone(Context c) {
        return recordDays(c).contains(dayKey(Calendar.getInstance()));
    }

    /** 连续多少天没打卡（从昨天往前数；从未打过卡返回 0） */
    static int gapDays(Context c) {
        Set<String> days = recordDays(c);
        if (days.isEmpty()) return 0;
        Calendar cur = Calendar.getInstance();
        cur.add(Calendar.DAY_OF_YEAR, -1);
        int n = 0;
        while (!days.contains(dayKey(cur))) {
            n++;
            cur.add(Calendar.DAY_OF_YEAR, -1);
            if (n > 999) break;
        }
        return n;
    }

    /** 连续打卡天数（今天没打就从昨天往前数） */
    static int streak(Context c) {
        Set<String> days = recordDays(c);
        Calendar cur = Calendar.getInstance();
        if (!days.contains(dayKey(cur))) cur.add(Calendar.DAY_OF_YEAR, -1);
        int n = 0;
        while (days.contains(dayKey(cur))) {
            n++;
            cur.add(Calendar.DAY_OF_YEAR, -1);
            if (n > 3650) break;
        }
        return n;
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent pending(Context c, boolean create) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("com.studyrank.app.REMIND");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return create ? PendingIntent.getBroadcast(c, REQ, i, flags) : null;
    }

    public static void schedule(Context c) {
        SharedPreferences p = prefs(c);
        boolean on = p.getBoolean(KEY_ON, false);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);

        if (am == null) return;
        PendingIntent pi = pending(c, true);
        if (pi == null) return;

        if (!on) {
            am.cancel(pi);
            return;
        }

        int hour = p.getInt(KEY_HOUR, 21);
        int min = p.getInt(KEY_MIN, 0);

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, min);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        } else if (todayDone(c)) {
            // 今天已经打过卡，今天不再响，直接排到明天
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            long at = next.getTimeInMillis();
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
            } catch (Exception ignored) {
            }
        }
    }

    /** 页面保存设置后调用：开启则排程，关闭则取消 */
    public static void set(Context c, boolean on, int hour, int min) {
        SharedPreferences.Editor e = prefs(c).edit();
        e.putBoolean(KEY_ON, on);
        e.putInt(KEY_HOUR, hour);
        e.putInt(KEY_MIN, min);
        e.apply();
        schedule(c);
    }
}
