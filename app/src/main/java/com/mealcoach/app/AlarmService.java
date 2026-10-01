package com.mealcoach.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

public class AlarmService extends Service {
    public static final String MODE_CONFIRM = "confirm";
    public static final String MODE_FINAL = "final";

    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler = new Handler();
    private float volume = 0.15f;
    private String mode = MODE_CONFIRM;

    private final Runnable ramp = new Runnable() {
        @Override public void run() {
            if (player == null || MODE_FINAL.equals(mode)) return;
            volume = Math.min(1.0f, volume + 0.12f);
            try { player.setVolume(volume, volume); } catch (Exception ignored) {}
            if (volume < 1.0f) handler.postDelayed(this, 10_000L);
        }
    };

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

        mode = intent != null ? intent.getStringExtra("mode") : MODE_CONFIRM;
        if (mode == null) mode = MODE_CONFIRM;

        startForeground(NotificationHelper.SERVICE_ID,
                NotificationHelper.buildEscalationNotification(this, mode));

        startSoundAndVibration();
        DiagnosticStore.log(this, "ALARM_SERVICE_START", "mode=" + mode);
        return START_STICKY;
    }

    private void startSoundAndVibration() {
        stopMediaOnly();

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

            volume = MODE_FINAL.equals(mode) ? 1.0f : 0.15f;
            player.setVolume(volume, volume);
            player.start();

            handler.removeCallbacks(ramp);
            if (!MODE_FINAL.equals(mode)) handler.postDelayed(ramp, 10_000L);
        } catch (Exception e) {
            stopMediaOnly();
            DiagnosticStore.log(this, "ALARM_SOUND_ERROR", e.toString());
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = MODE_FINAL.equals(mode)
                    ? new long[]{0, 900, 250, 900, 250}
                    : new long[]{0, 450, 350, 450, 900};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
    }

    private void stopMediaOnly() {
        handler.removeCallbacks(ramp);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        stopMediaOnly();
        if (vibrator != null) vibrator.cancel();
        DiagnosticStore.log(this, "ALARM_SERVICE_STOP", "mode=" + mode);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AlarmService.class));
    }
}
