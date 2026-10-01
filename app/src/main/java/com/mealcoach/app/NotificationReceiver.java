package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        android.content.SharedPreferences p = MealEngine.prefs(context);
        if (!p.getBoolean(MealEngine.K_DAY, false) || p.getBoolean(MealEngine.K_EATING, false)) return;

        int type = intent.getIntExtra("type", 0);
        if (type == AlarmScheduler.FINAL) {
            Intent service = new Intent(context, AlarmService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } else if (type >= AlarmScheduler.PREP && type <= AlarmScheduler.ESCALATE) {
            NotificationHelper.showReminder(context, type);
        }
    }
}
