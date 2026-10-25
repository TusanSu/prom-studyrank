package com.studyrank.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 番茄钟结束提醒：到点弹系统通知 */
public class PomodoroReceiver extends BroadcastReceiver {

    public static final String CHANNEL = "prom_pomo";

    static void channel(Context ctx, NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "番茄钟", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("专注结束时提醒你");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");
            if (title == null) title = "专注结束";
            if (text == null) text = "休息一下，或者再记一笔";

            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            channel(ctx, nm);

            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 4302, open, flags);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(ctx, CHANNEL);
            } else {
                b = new Notification.Builder(ctx);
                b.setPriority(Notification.PRIORITY_HIGH);
            }
            b.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setWhen(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFF0B0B0B);
            nm.notify(4303, b.build());
        } catch (Exception ignored) {
        }
    }
}
