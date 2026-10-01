package com.mealcoach.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

public class AlarmService extends Service {
    private static final int NOTIFICATION_ID = 900;
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.content.SharedPreferences p = MealEngine.prefs(this);
        if (!p.getBoolean(MealEngine.K_DAY, false) || p.getBoolean(MealEngine.K_EATING, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, NotificationHelper.buildAlarmNotification(this));
        startSoundAndVibration();
        return START_STICKY;
    }

    private void startSoundAndVibration() {
        if (player == null) {
            Uri alarm = Settings.System.DEFAULT_ALARM_ALERT_URI;
            if (alarm == null) alarm = Settings.System.DEFAULT_NOTIFICATION_URI;
            try {
                player = new MediaPlayer();
                player.setDataSource(this, alarm);
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e) {
                if (player != null) {
                    player.release();
                    player = null;
                }
            }
        }

        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 450, 800, 450}, 0));
        }
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        if (vibrator != null) vibrator.cancel();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AlarmService.class));
    }
}
