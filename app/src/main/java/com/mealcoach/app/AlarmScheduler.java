package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmScheduler {
    public static final int PREP = 1;
    public static final int PREFERRED = 2;
    public static final int ESCALATE = 3;
    public static final int FINAL = 4;

    private static final int BASE_REQ = 4100;

    private AlarmScheduler() {}

    public static void scheduleCurrent(Context context) {
        cancelAll(context);
        android.content.SharedPreferences p = MealEngine.prefs(context);
        if (!p.getBoolean(MealEngine.K_DAY, false) || p.getBoolean(MealEngine.K_EATING, false)) return;

        long now = System.currentTimeMillis();
        long pref = p.getLong(MealEngine.K_PREF, 0L);
        long deadline = p.getLong(MealEngine.K_DEADLINE, 0L);
        if (pref <= 0 || deadline <= 0) return;

        scheduleOne(context, PREP, pref - 20 * 60_000L, now);
        scheduleOne(context, PREFERRED, pref, now);
        scheduleOne(context, ESCALATE, pref + 15 * 60_000L, now);
        scheduleOne(context, FINAL, deadline, now);
    }

    private static void scheduleOne(Context context, int type, long at, long now) {
        if (type != FINAL && at <= now) return;
        if (type == FINAL && at <= now) at = now + 1500L;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(context, type);
        if (am == null) return;

        if (Build.VERSION.SDK_INT >= 31) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int type = PREP; type <= FINAL; type++) {
            am.cancel(pending(context, type));
        }
    }

    private static PendingIntent pending(Context context, int type) {
        Intent i = new Intent(context, NotificationReceiver.class);
        i.putExtra("type", type);
        return PendingIntent.getBroadcast(
                context,
                BASE_REQ + type,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
