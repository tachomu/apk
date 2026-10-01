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
    public static final String K_DELAY_USED = "delay_used";
    public static final String K_PHOTO = "photo_uri";
    public static final String K_TEST = "test_mode";

    private static final long MIN = 60_000L;

    private MealEngine() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void wake(Context c) {
        resetSignals(c);
        long now = System.currentTimeMillis();
        prefs(c).edit()
                .clear()
                .putBoolean(K_DAY, true)
                .putBoolean(K_TEST, false)
                .putInt(K_MEALS, 0)
                .putInt(K_SNACKS, 0)
                .putInt(K_CONSEC_SNACKS, 0)
                .putBoolean(K_EATING, false)
                .putLong(K_START, now)
                .putLong(K_PREF, now + 30 * MIN)
                .putLong(K_DEADLINE, now + 60 * MIN)
                .putBoolean(K_FIRST, true)
                .putInt(K_DELAY_USED, 0)
                .apply();

        LogStore.log(c, "WAKE", 0, 0, "", "");
        DiagnosticStore.log(c, "WAKE", "preferred=+30m deadline=+60m");
        AlarmScheduler.scheduleCurrent(c);
    }

    public static void startTest(Context c) {
        resetSignals(c);
        long now = System.currentTimeMillis();
        prefs(c).edit()
                .clear()
                .putBoolean(K_DAY, true)
                .putBoolean(K_TEST, true)
                .putInt(K_MEALS, 0)
                .putInt(K_SNACKS, 0)
                .putInt(K_CONSEC_SNACKS, 0)
                .putBoolean(K_EATING, false)
                .putLong(K_START, now)
                .putLong(K_PREF, now + 3 * MIN)
                .putLong(K_DEADLINE, now + 6 * MIN)
                .putBoolean(K_FIRST, false)
                .putInt(K_DELAY_USED, 0)
                .apply();

        LogStore.log(c, "TEST_START", 0, 0, "6_minute_escalation_test", "");
        DiagnosticStore.log(c, "TEST_START", "stages at +1,+2,+3,+4,+5,+5.5,+6 min");
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

        LogStore.log(c, "SIT_EAT", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0),
                "", p.getString(K_PHOTO, ""));
        DiagnosticStore.log(c, "SIT_EAT", "alarm stopped; waiting for finish");
    }

    public static void ate(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false)) return;

        int meals = p.getInt(K_MEALS, 0) + 1;
        int snacks = p.getInt(K_SNACKS, 0);
        boolean test = p.getBoolean(K_TEST, false);
        long started = p.getLong(K_EATING_START, 0L);
        long now = System.currentTimeMillis();
        String photo = p.getString(K_PHOTO, "");
        String duration = started > 0 ? "duration_min=" + Math.max(0, (now - started) / MIN) : "";

        resetSignals(c);

        long pref = test ? now + 3 * MIN : now + 3 * 60 * MIN;
        long dead = test ? now + 6 * MIN : now + 4 * 60 * MIN;

        p.edit()
                .putInt(K_MEALS, meals)
                .putInt(K_CONSEC_SNACKS, 0)
                .putBoolean(K_EATING, false)
                .putLong(K_EATING_START, 0L)
                .putLong(K_START, now)
                .putLong(K_PREF, pref)
                .putLong(K_DEADLINE, dead)
                .putBoolean(K_FIRST, false)
                .putInt(K_DELAY_USED, 0)
                .putString(K_PHOTO, "")
                .apply();

        LogStore.log(c, "FULL_MEAL", meals, snacks, duration, photo);
        DiagnosticStore.log(c, "FULL_MEAL", "meals=" + meals + " next_pref=" + pref + " deadline=" + dead);
        AlarmScheduler.scheduleCurrent(c);
    }

    public static boolean snack(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false) || p.getBoolean(K_EATING, false)) return false;

        int snacks = p.getInt(K_SNACKS, 0) + 1;
        int consecutive = p.getInt(K_CONSEC_SNACKS, 0);
        boolean canExtend = consecutive < 2;
        boolean test = p.getBoolean(K_TEST, false);
        long extension = test ? MIN : 60 * MIN;
        String photo = p.getString(K_PHOTO, "");

        SharedPreferences.Editor e = p.edit()
                .putInt(K_SNACKS, snacks)
                .putInt(K_CONSEC_SNACKS, consecutive + 1)
                .putString(K_PHOTO, "");

        if (canExtend) {
            e.putLong(K_PREF, p.getLong(K_PREF, System.currentTimeMillis()) + extension)
                    .putLong(K_DEADLINE, p.getLong(K_DEADLINE, System.currentTimeMillis()) + extension)
                    .putInt(K_DELAY_USED, 0);
        }
        e.apply();

        LogStore.log(c, "SNACK", p.getInt(K_MEALS, 0), snacks,
                canExtend ? "deadline_shifted" : "no_more_extension", photo);
        DiagnosticStore.log(c, "SNACK", "count=" + snacks + " consecutive=" + (consecutive + 1) + " extend=" + canExtend);

        if (canExtend) {
            resetSignals(c);
            AlarmScheduler.scheduleCurrent(c);
        }
        return canExtend;
    }

    public static boolean delay15(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_DAY, false) || p.getBoolean(K_EATING, false)) return false;

        long now = System.currentTimeMillis();
        long pref = p.getLong(K_PREF, Long.MAX_VALUE);
        if (now < pref) {
            DiagnosticStore.log(c, "DELAY_BLOCKED", "too_early");
            return false;
        }

        int used = p.getInt(K_DELAY_USED, 0);
        if (used >= 2) {
            LogStore.log(c, "DELAY_BLOCKED", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "limit=2", "");
            DiagnosticStore.log(c, "DELAY_BLOCKED", "limit=2");
            return false;
        }

        long extension = p.getBoolean(K_TEST, false) ? MIN : 15 * MIN;
        p.edit()
                .putLong(K_DEADLINE, p.getLong(K_DEADLINE, now) + extension)
                .putInt(K_DELAY_USED, used + 1)
                .apply();

        resetSignals(c);
        LogStore.log(c, "DELAY_15", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "used=" + (used + 1), "");
        DiagnosticStore.log(c, "DELAY", "used=" + (used + 1) + " extension_ms=" + extension);
        AlarmScheduler.scheduleCurrent(c);
        return true;
    }

    public static void sleep(Context c) {
        SharedPreferences p = prefs(c);
        int meals = p.getInt(K_MEALS, 0);
        int snacks = p.getInt(K_SNACKS, 0);

        resetSignals(c);

        p.edit()
                .putBoolean(K_DAY, false)
                .putBoolean(K_TEST, false)
                .putBoolean(K_EATING, false)
                .putLong(K_EATING_START, 0L)
                .putString(K_PHOTO, "")
                .apply();

        LogStore.log(c, "SLEEP", meals, snacks, "", "");
        DiagnosticStore.log(c, "SLEEP", "meals=" + meals + " snacks=" + snacks);
    }

    public static void savePhoto(Context c, String uri) {
        SharedPreferences p = prefs(c);
        p.edit().putString(K_PHOTO, uri == null ? "" : uri).apply();
        LogStore.log(c, "PHOTO", p.getInt(K_MEALS, 0), p.getInt(K_SNACKS, 0), "", uri);
        DiagnosticStore.log(c, "PHOTO", uri == null ? "" : uri);
    }

    public static void clearNotifications(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
    }

    private static void resetSignals(Context c) {
        AlarmScheduler.cancelAll(c);
        AlarmService.stop(c);
        clearNotifications(c);
    }
}
