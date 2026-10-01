package com.mealcoach.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;

public final class NotificationHelper {
    public static final String CH_PREP = "meal_prep_v2";
    public static final String CH_REMIND = "meal_reminders_v2";
    public static final String CH_URGENT = "meal_urgent_v2";
    public static final String CH_ALARM = "meal_alarm_v2";

    public static final int REMINDER_ID = 501;
    public static final int SERVICE_ID = 900;

    private NotificationHelper() {}

    public static void ensureChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel prep = new NotificationChannel(
                CH_PREP, "Підготовка до їжі", NotificationManager.IMPORTANCE_DEFAULT);
        prep.setDescription("Попередження заздалегідь");
        prep.setSound(null, null);
        prep.enableVibration(false);

        NotificationChannel remind = new NotificationChannel(
                CH_REMIND, "Нагадування про їжу", NotificationManager.IMPORTANCE_HIGH);
        remind.setDescription("Нагадування, коли вже час їсти");
        remind.enableVibration(true);

        NotificationChannel urgent = new NotificationChannel(
                CH_URGENT, "Прострочена їжа", NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription("Посилені нагадування після пропуску");
        urgent.enableVibration(true);
        urgent.setVibrationPattern(new long[]{0, 350, 250, 350, 250, 700});

        NotificationChannel alarm = new NotificationChannel(
                CH_ALARM, "Критичний сигнал MealCoach", NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("Повноекранна ескалація після тривалого пропуску");
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 700, 350, 700, 350});
        alarm.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());

        nm.createNotificationChannel(prep);
        nm.createNotificationChannel(remind);
        nm.createNotificationChannel(urgent);
        nm.createNotificationChannel(alarm);
    }

    public static void showReminder(Context c, int type) {
        ensureChannels(c);

        String title;
        String body;
        String channel;
        boolean ongoing = false;

        switch (type) {
            case AlarmScheduler.PREP_60:
                title = "Через годину — їжа";
                body = "Починай планувати: що будеш їсти і чи треба щось приготувати.";
                channel = CH_PREP;
                break;
            case AlarmScheduler.PREP_20:
                title = "Через 20 хв — їжа";
                body = "Не починай нову катку. Відкладай справи й готуй їжу.";
                channel = CH_PREP;
                break;
            case AlarmScheduler.PREFERRED:
                title = "Пора їсти";
                body = "Сфотографуй їжу в MealCoach і починай прийом.";
                channel = CH_REMIND;
                break;
            case AlarmScheduler.LATE_15:
                title = "Ти вже пропустив 15 хв";
                body = "Не відкладай автоматично. Сфотографуй їжу й починай.";
                channel = CH_URGENT;
                ongoing = true;
                break;
            case AlarmScheduler.LATE_45:
                title = "Вже 45 хв після бажаного часу";
                body = "Критична зона. Наступний рівень — максимальна межа і безперервний сигнал.";
                channel = CH_URGENT;
                ongoing = true;
                break;
            default:
                return;
        }

        Notification.Builder b = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(false)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(false)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setColor(type >= AlarmScheduler.LATE_15
                        ? Color.rgb(255, 107, 129)
                        : Color.rgb(89, 209, 181))
                .setContentIntent(openApp(c));

        if (type >= AlarmScheduler.PREFERRED) {
            b.addAction(new Notification.Action.Builder(null, "ЇСТИ → ФОТО", startMeal(c, 611)).build());
        }
        if (type >= AlarmScheduler.PREFERRED && type < AlarmScheduler.LATE_45) {
            b.addAction(new Notification.Action.Builder(null, "+15 ХВ", action(c, "DELAY", 612)).build());
        }

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(REMINDER_ID);
            nm.notify(REMINDER_ID, b.build());
        }
    }

    public static Notification buildEscalationNotification(Context c, String mode) {
        ensureChannels(c);
        boolean finalMode = AlarmService.MODE_FINAL.equals(mode);

        Intent full = new Intent(c, finalMode ? AlarmActivity.class : ConfirmActivity.class);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(
                c,
                finalMode ? 730 : 720,
                full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = finalMode ? "МАКСИМАЛЬНА МЕЖА" : "ТИ ПРОПУСТИВ ЩЕ 30 ХВ";
        String body = finalMode
                ? "Сигнал триватиме, доки ти не сфотографуєш їжу і не почнеш прийом."
                : "Звук поступово посилюється. Введи «ПІДТВЕРДЖУЮ», щоб прибрати цей рівень.";

        return new Notification.Builder(c, CH_ALARM)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setColor(Color.rgb(255, 107, 129))
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .build();
    }

    public static void clearReminder(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(REMINDER_ID);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 700, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent startMeal(Context c, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("force_camera", true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent action(Context c, String action, int req) {
        Intent i = new Intent(c, ActionReceiver.class);
        i.setAction("com.mealcoach." + action);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
