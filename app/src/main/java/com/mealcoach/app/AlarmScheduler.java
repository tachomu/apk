package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.SharedPreferences;

public final class AlarmScheduler {
    public static final int PREP_60 = 1;
    public static final int PREP_20 = 2;
    public static final int PREFERRED = 3;
    public static final int LATE_15 = 4;
    public static final int LATE_30 = 5;
    public static final int LATE_45 = 6;
    public static final int FINAL = 7;

    private static final int BASE_REQ = 4100;
    private static final long MIN = 60_000L;

    private AlarmScheduler() {}

    public static void scheduleCurrent(Context context) {
        cancelAll(context);
        SharedPreferences p = MealEngine.prefs(context);
        if (!p.getBoolean(MealEngine.K_DAY, false) || p.getBoolean(MealEngine.K_EATING, false)) return;

        long now = System.currentTimeMillis();
        long start = p.getLong(MealEngine.K_START, now);
        long pref = p.getLong(MealEngine.K_PREF, 0L);
        long deadline = p.getLong(MealEngine.K_DEADLINE, 0L);
        boolean test = p.getBoolean(MealEngine.K_TEST, false);

        if (pref <= 0 || deadline <= 0) return;

        if (test) {
            scheduleIfBeforeDeadline(context, PREP_60, start + MIN, deadline, now);
            scheduleIfBeforeDeadline(context, PREP_20, start + 2 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, PREFERRED, pref, deadline, now);
            scheduleIfBeforeDeadline(context, LATE_15, start + 4 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, LATE_30, start + 5 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, LATE_45, start + 5 * MIN + 30_000L, deadline, now);
            scheduleOne(context, FINAL, deadline, now);
        } else {
            scheduleIfBeforeDeadline(context, PREP_60, pref - 60 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, PREP_20, pref - 20 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, PREFERRED, pref, deadline, now);
            scheduleIfBeforeDeadline(context, LATE_15, pref + 15 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, LATE_30, pref + 30 * MIN, deadline, now);
            scheduleIfBeforeDeadline(context, LATE_45, pref + 45 * MIN, deadline, now);
            scheduleOne(context, FINAL, deadline, now);
        }

        DiagnosticStore.log(context, "ALARMS_SCHEDULED",
                "test=" + test + " pref=" + pref + " deadline=" + deadline);
    }

    private static void scheduleIfBeforeDeadline(Context context, int type, long at, long deadline, long now) {
        if (at >= deadline - 1000L) return;
        scheduleOne(context, type, at, now);
    }

    private static void scheduleOne(Context context, int type, long at, long now) {
        if (at <= now) return;

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
        for (int type = PREP_60; type <= FINAL; type++) {
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
