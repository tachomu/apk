package com.mealcoach.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.provider.Settings;

public final class NotificationHelper {
    public static final String CH_REMIND = "meal_reminders";
    public static final String CH_ALARM = "meal_alarm";

    private NotificationHelper() {}

    public static void ensureChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel remind = new NotificationChannel(
                CH_REMIND,
                "Нагадування про їжу",
                NotificationManager.IMPORTANCE_HIGH
        );
        remind.setDescription("Наполегливі нагадування MealCoach");
        remind.enableVibration(true);

        NotificationChannel alarm = new NotificationChannel(
                CH_ALARM,
                "Термінове нагадування про їжу",
                NotificationManager.IMPORTANCE_HIGH
        );
        alarm.setDescription("Гучний сигнал після максимальної межі");
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 700, 400, 700, 400});
        alarm.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());

        nm.createNotificationChannel(remind);
        nm.createNotificationChannel(alarm);
    }

    public static void showReminder(Context c, int type) {
        ensureChannels(c);
        String title;
        String text;
        int id;

        if (type == AlarmScheduler.PREP) {
            title = "Їжа скоро";
            text = "Приблизно через 20 хв — бажаний час. Підготуй їжу зараз.";
            id = 501;
        } else if (type == AlarmScheduler.PREFERRED) {
            title = "Пора їсти";
            text = "Ти вже у бажаному вікні. Не відкладай без потреби.";
            id = 502;
        } else {
            title = "Ти відкладаєш їжу";
            text = "Минуло ще 15 хв. Наступне нагадування буде значно жорсткішим.";
            id = 503;
        }

        Notification.Builder b = new Notification.Builder(c, CH_REMIND)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(false)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setColor(Color.rgb(89, 209, 181))
                .setContentIntent(openApp(c))
                .addAction(new Notification.Action.Builder(null, "СІВ ЇСТИ", action(c, "SIT", 601)).build())
                .addAction(new Notification.Action.Builder(null, "ПОЇВ", action(c, "ATE", 602)).build())
                .addAction(new Notification.Action.Builder(null, "+15 ХВ", action(c, "DELAY", 603)).build());

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());
    }

    public static Notification buildAlarmNotification(Context c) {
        ensureChannels(c);

        Intent full = new Intent(c, AlarmActivity.class);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(
                c, 710, full, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(c, CH_ALARM)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("ЧАС ЇСТИ")
                .setContentText("Максимальна межа пройдена. Натисни «Сів їсти».")
                .setStyle(new Notification.BigTextStyle().bigText("Максимальна межа пройдена. Сигнал триватиме, доки ти не підтвердиш, що сів їсти."))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setColor(Color.rgb(255, 107, 129))
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .addAction(new Notification.Action.Builder(null, "СІВ ЇСТИ", action(c, "SIT", 711)).build())
                .addAction(new Notification.Action.Builder(null, "ПОЇВ", action(c, "ATE", 712)).build())
                .build();
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 700, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent action(Context c, String action, int req) {
        Intent i = new Intent(c, ActionReceiver.class);
        i.setAction("com.mealcoach." + action);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
