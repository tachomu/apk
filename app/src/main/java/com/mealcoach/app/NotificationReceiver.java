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
        DiagnosticStore.log(context, "ALARM_FIRED", "type=" + type);

        if (type == AlarmScheduler.LATE_30) {
            startService(context, AlarmService.MODE_CONFIRM);
        } else if (type == AlarmScheduler.LATE_45) {
            PenaltyManager.setBlocked(context, true);
            NotificationHelper.showReminder(context, type);
        } else if (type == AlarmScheduler.FINAL) {
            startService(context, AlarmService.MODE_FINAL);
        } else if (type == AlarmScheduler.PREP_60
                || type == AlarmScheduler.PREP_20
                || type == AlarmScheduler.PREFERRED
                || type == AlarmScheduler.LATE_15
                || type == AlarmScheduler.LATE_45) {
            NotificationHelper.showReminder(context, type);
        }
    }

    private void startService(Context context, String mode) {
        NotificationHelper.clearReminder(context);
        Intent service = new Intent(context, AlarmService.class);
        service.putExtra("mode", mode);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
    }
}
