package com.studyrank.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 应用更新后重新排程提醒 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            ReminderScheduler.schedule(context);
        } catch (Exception ignored) {
        }
    }
}
