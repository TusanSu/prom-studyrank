package com.studyrank.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 每天到点发一条提醒（已打卡则跳过），并排下一次 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL = "prom_remind";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            // 今天已经打过卡就不再打扰
            if (!ReminderScheduler.todayDone(ctx)) {
                post(ctx);
            }
        } catch (Exception ignored) {
        }
        ReminderScheduler.schedule(ctx);
    }

    static void channel(Context ctx, NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "每日打卡提醒", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("每天提醒你来打卡");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    /** 按断签天数和连续天数挑一句文案 */
    static String[] copyFor(int streak, int gap) {
        if (gap >= 7) {
            return new String[]{"已经 " + gap + " 天没打卡了", "再不来，之前攒的记录就真的凉了"};
        }
        if (gap >= 3) {
            return new String[]{"已经 " + gap + " 天没打卡", "段位正在往下掉，今天补一笔就止住"};
        }
        if (streak >= 30) {
            return new String[]{"连续 " + streak + " 天，别断在今晚", "这个记录一旦断了就得从头再来"};
        }
        if (streak >= 7) {
            return new String[]{"连续 " + streak + " 天，稳住", "今天的记录还空着，补一笔再睡"};
        }
        if (streak >= 3) {
            return new String[]{"连续 " + streak + " 天，继续保持", "花一分钟记一笔，别让链子断在今天"};
        }
        if (streak >= 1) {
            return new String[]{"昨天打过卡，今天呢", "连续 " + streak + " 天，再记一笔就多一天"};
        }
        return new String[]{"今天还没打卡", "花一分钟记一笔，段位不会自己涨"};
    }

    static void post(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        channel(ctx, nm);

        int streak = 0, gap = 0;
        try {
            streak = ReminderScheduler.streak(ctx);
            gap = ReminderScheduler.gapDays(ctx);
        } catch (Exception ignored) {
        }
        String[] copy = copyFor(streak, gap);

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 4202, open, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, CHANNEL);
        } else {
            b = new Notification.Builder(ctx);
            b.setPriority(Notification.PRIORITY_DEFAULT);
        }

        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(copy[0])
            .setContentText(copy[1])
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFF0B0B0B);

        nm.notify(4203, b.build());
    }
}
