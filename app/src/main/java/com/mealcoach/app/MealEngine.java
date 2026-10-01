package com.mealcoach.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

public final class MealEngine {
    public static final String PREFS = "mealcoach";
    public static final String K_DAY = "day";
    public static final String K_MEALS = "meals";
    public static final String K_SNACKS = "snacks";
    public static final String K_CONSEC_SNACKS = "consecutive_snacks";
    public static final String K_EATING = "eating";
    public static final String K_EATING_START = "eating_start";
    public static final String K_START = "cycle_start";
    public static final String K_PREF = "preferred";
    public static final String K_DEADLINE = "deadline";
    public static final String K_FIRST = "first_cycle";
    public static final String K_SNACK_MODE = "snack_mode";
    public static final String K_DELAY_USED = "delay_used";
    public static final String K_PHOTO = "photo_uri";

    private static final long MIN = 60_000L;

    private MealEngine() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void wake(Context c) {
        AlarmScheduler.cancelAll(c);
        AlarmService.stop(c);
        clearNotifications(c);

        long now = System.currentTimeMillis();
        prefs(c).edit()
                .clear()
                .putBoolean(K_DAY, true)
                .putInt(K_MEALS, 0)
                .putInt(K_SNACKS, 0)
                .putInt(K_CONSEC_SNACKS, 0)
                .putBoolean(K_EATING, false)
                .putLong(K_START, now)
                .putLong(K_PREF, now + 30 * MIN)
                .putLong(K_DEADLINE, now + 60 * MIN)
                .putBoolean(K_FIRST, true)
                .putBoolean(K_SNACK_MODE, false)
                .putInt(K_DELAY_USED, 0)
                .apply();

        LogStore.log(c, "WAKE", 0, 0, "", "");
        AlarmScheduler.scheduleCurrent(c);
    }

    public static void startEating(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false)) return;

        AlarmScheduler.cancelAll(c);
        AlarmService.stop(c);
        clearNotifications(c);

        p.edit()
                .putBoolean(K_EATING, true)
                .putLong(K_EATING_START, System.currentTimeMillis())
                .apply();

        LogStore.log(c, "SIT_EAT", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "", p.getString(K_PHOTO, ""));
    }

    public static void ate(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false)) return;

        int meals = p.getInt(K_MEALS, 0) + 1;
        int snacks = p.getInt(K_SNACKS, 0);
        long started = p.getLong(K_EATING_START, 0L);
        long now = System.currentTimeMillis();
        String duration = started > 0 ? "duration_min=" + Math.max(0, (now - started) / MIN) : "";

        AlarmScheduler.cancelAll(c);
        AlarmService.stop(c);
        clearNotifications(c);

        p.edit()
                .putInt(K_MEALS, meals)
                .putInt(K_CONSEC_SNACKS, 0)
                .putBoolean(K_EATING, false)
                .putLong(K_EATING_START, 0L)
                .putLong(K_START, now)
                .putLong(K_PREF, now + 3 * 60 * MIN)
                .putLong(K_DEADLINE, now + 4 * 60 * MIN)
                .putBoolean(K_FIRST, false)
                .putBoolean(K_SNACK_MODE, false)
                .putInt(K_DELAY_USED, 0)
                .apply();

        LogStore.log(c, "FULL_MEAL", meals, snacks, duration, p.getString(K_PHOTO, ""));
        AlarmScheduler.scheduleCurrent(c);
    }

    public static boolean snack(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false)) return false;

        int snacks = p.getInt(K_SNACKS, 0) + 1;
        int consecutive = p.getInt(K_CONSEC_SNACKS, 0);
        boolean canExtend = consecutive < 2;
        long now = System.currentTimeMillis();

        SharedPreferences.Editor e = p.edit()
                .putInt(K_SNACKS, snacks)
                .putInt(K_CONSEC_SNACKS, consecutive + 1);

        if (canExtend) {
            e.putLong(K_START, now)
                    .putLong(K_PREF, now + 45 * MIN)
                    .putLong(K_DEADLINE, now + 60 * MIN)
                    .putBoolean(K_SNACK_MODE, true)
                    .putInt(K_DELAY_USED, 0)
                    .putBoolean(K_EATING, false);
        }
        e.apply();

        LogStore.log(c, "SNACK", p.getInt(K_MEALS, 0), snacks, canExtend ? "deadline_shifted" : "no_more_extension", p.getString(K_PHOTO, ""));
        if (canExtend) {
            AlarmService.stop(c);
            clearNotifications(c);
            AlarmScheduler.scheduleCurrent(c);
        }
        return canExtend;
    }

    public static boolean delay15(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false) || p.getBoolean(K_EATING, false)) return false;

        int used = p.getInt(K_DELAY_USED, 0);
        if (used >= 2) {
            LogStore.log(c, "DELAY_BLOCKED", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "limit=2", "");
            return false;
        }

        p.edit()
                .putLong(K_DEADLINE, p.getLong(K_DEADLINE, System.currentTimeMillis()) + 15 * MIN)
                .putInt(K_DELAY_USED, used + 1)
                .apply();

        AlarmService.stop(c);
        clearNotifications(c);
        LogStore.log(c, "DELAY_15", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "used=" + (used + 1), "");
        AlarmScheduler.scheduleCurrent(c);
        return true;
    }

    public static void sleep(Context c) {
        SharedPreferences p = prefs(c);
        int meals = p.getInt(K_MEALS, 0);
        int snacks = p.getInt(K_SNACKS, 0);

        AlarmScheduler.cancelAll(c);
        AlarmService.stop(c);
        clearNotifications(c);

        p.edit()
                .putBoolean(K_DAY, false)
                .putBoolean(K_EATING, false)
                .putLong(K_EATING_START, 0L)
                .apply();

        LogStore.log(c, "SLEEP", meals, snacks, "", "");
    }

    public static void savePhoto(Context c, String uri) {
        SharedPreferences p = prefs(c);
        p.edit().putString(K_PHOTO, uri == null ? "" : uri).apply();
        LogStore.log(c, "PHOTO", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "", uri);
    }

    public static void clearNotifications(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
    }
}
