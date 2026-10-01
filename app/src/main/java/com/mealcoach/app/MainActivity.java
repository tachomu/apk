package com.mealcoach.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 42;
    private static final int REQ_EXPORT = 43;

    private TextView statusChip, cycleLabel, countdown, deadlineText, mealsText, snacksText, historyText;
    private ProgressBar cycleProgress;
    private Button wakeButton, sitButton, ateButton, snackButton, delayButton, photoButton, sleepButton, exportButton;
    private LinearLayout activeActions;
    private final Handler handler = new Handler();
    private Uri pendingPhotoUri;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            renderState();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        NotificationHelper.ensureChannels(this);
        requestNotificationsIfNeeded();

        statusChip = findViewById(R.id.statusChip);
        cycleLabel = findViewById(R.id.cycleLabel);
        countdown = findViewById(R.id.countdown);
        deadlineText = findViewById(R.id.deadlineText);
        mealsText = findViewById(R.id.mealsText);
        snacksText = findViewById(R.id.snacksText);
        historyText = findViewById(R.id.historyText);
        cycleProgress = findViewById(R.id.cycleProgress);
        wakeButton = findViewById(R.id.wakeButton);
        activeActions = findViewById(R.id.activeActions);
        sitButton = findViewById(R.id.sitButton);
        ateButton = findViewById(R.id.ateButton);
        snackButton = findViewById(R.id.snackButton);
        delayButton = findViewById(R.id.delayButton);
        photoButton = findViewById(R.id.photoButton);
        sleepButton = findViewById(R.id.sleepButton);
        exportButton = findViewById(R.id.exportButton);

        wakeButton.setOnClickListener(v -> {
            MealEngine.wake(this);
            refreshAll();
        });

        sitButton.setOnClickListener(v -> {
            MealEngine.startEating(this);
            refreshAll();
        });

        ateButton.setOnClickListener(v -> {
            MealEngine.ate(this);
            refreshAll();
        });

        snackButton.setOnClickListener(v -> {
            boolean shifted = MealEngine.snack(this);
            Toast.makeText(this,
                    shifted ? "Перекус: основну їжу перенесено приблизно на годину." :
                            "Перекус записано, але третій поспіль уже не переносить основну їжу.",
                    Toast.LENGTH_LONG).show();
            refreshAll();
        });

        delayButton.setOnClickListener(v -> {
            boolean ok = MealEngine.delay15(this);
            Toast.makeText(this,
                    ok ? "Додано 15 хв." : "Ліміт відкладання для цього циклу вичерпано.",
                    Toast.LENGTH_SHORT).show();
            refreshAll();
        });

        photoButton.setOnClickListener(v -> takePhoto());

        sleepButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Завершити день?")
                .setMessage("Усі нагадування про їжу зупиняться до наступного «Я прокинувся».")
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Лягаю спати", (d, w) -> {
                    MealEngine.sleep(this);
                    refreshAll();
                })
                .show());

        exportButton.setOnClickListener(v -> exportCsv());

        AlarmScheduler.scheduleCurrent(this);
        refreshAll();
    }

    @Override protected void onResume() {
        super.onResume();
        renderState();
        refreshHistory();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void refreshAll() {
        renderState();
        refreshHistory();
    }

    private void renderState() {
        SharedPreferences p = MealEngine.prefs(this);
        boolean day = p.getBoolean(MealEngine.K_DAY, false);
        int meals = p.getInt(MealEngine.K_MEALS, 0);
        int snacks = p.getInt(MealEngine.K_SNACKS, 0);

        mealsText.setText(meals >= 3 ? "СЬОГОДНІ  " + meals + " / 3+  ✓" : "СЬОГОДНІ  " + meals + " / 3+");
        snacksText.setText("ПЕРЕКУСИ  " + snacks);

        if (!day) {
            wakeButton.setVisibility(View.VISIBLE);
            activeActions.setVisibility(View.GONE);
            statusChip.setText("НЕ ЗАПУЩЕНО");
            statusChip.setTextColor(getColor(R.color.muted));
            cycleLabel.setText("ДЕНЬ ЩЕ НЕ ЗАПУЩЕНО");
            countdown.setText("Я ПРОКИНУВСЯ");
            countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("Натисни кнопку — запустимо таймер першої їжі");
            cycleProgress.setProgress(0);
            return;
        }

        wakeButton.setVisibility(View.GONE);
        activeActions.setVisibility(View.VISIBLE);

        boolean eating = p.getBoolean(MealEngine.K_EATING, false);
        boolean first = p.getBoolean(MealEngine.K_FIRST, false);
        boolean snackMode = p.getBoolean(MealEngine.K_SNACK_MODE, false);
        long start = p.getLong(MealEngine.K_START, System.currentTimeMillis());
        long pref = p.getLong(MealEngine.K_PREF, start);
        long dead = p.getLong(MealEngine.K_DEADLINE, pref);
        long now = System.currentTimeMillis();

        cycleLabel.setText(eating ? "ПРИЙОМ ЇЖІ" : snackMode ? "ПІСЛЯ ПЕРЕКУСУ" : first ? "ПЕРША ЇЖА" : "НАСТУПНА ЇЖА");

        if (eating) {
            long eatingStart = p.getLong(MealEngine.K_EATING_START, now);
            statusChip.setText("ЇМ ЗАРАЗ");
            statusChip.setTextColor(getColor(R.color.accent));
            countdown.setText(formatDuration(now - eatingStart));
            countdown.setTextColor(getColor(R.color.accent));
            deadlineText.setText("Коли закінчиш — натисни «Поїв»");
            cycleProgress.setProgress(1000);
            sitButton.setEnabled(false);
            sitButton.setAlpha(0.45f);
            return;
        }

        sitButton.setEnabled(true);
        sitButton.setAlpha(1f);

        long span = Math.max(1L, dead - start);
        int progress = (int) Math.max(0, Math.min(1000, ((now - start) * 1000L) / span));
        cycleProgress.setProgress(progress);

        if (now < pref) {
            statusChip.setText("ВСЕ ДОБРЕ");
            statusChip.setTextColor(getColor(R.color.accent));
            countdown.setText(formatDuration(pref - now));
            countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("До бажаного часу • максимум " + clock(dead));
        } else if (now < dead) {
            statusChip.setText("ВЖЕ БАЖАНО");
            statusChip.setTextColor(getColor(R.color.warn));
            countdown.setText(formatDuration(dead - now));
            countdown.setTextColor(getColor(R.color.warn));
            deadlineText.setText("До максимальної межі • " + clock(dead));
        } else {
            statusChip.setText("ПРОСТРОЧЕНО");
            statusChip.setTextColor(getColor(R.color.danger));
            countdown.setText("+" + formatDuration(now - dead));
            countdown.setTextColor(getColor(R.color.danger));
            deadlineText.setText("Максимальна межа вже пройдена");
        }
    }

    private String formatDuration(long ms) {
        ms = Math.max(0L, ms);
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0) return String.format(Locale.getDefault(), "%d год %02d хв", h, m);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private void refreshHistory() {
        historyText.setText(LogStore.recent(this, 7));
    }

    private void takePhoto() {
        if (!MealEngine.prefs(this).getBoolean(MealEngine.K_DAY, false)) {
            Toast.makeText(this, "Спочатку запусти день.", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "MealCoach_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MealCoach");
        }
        pendingPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingPhotoUri == null) {
            Toast.makeText(this, "Не вдалося підготувати файл фото.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
        if (camera.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(camera, REQ_CAMERA);
        } else {
            getContentResolver().delete(pendingPhotoUri, null, null);
            pendingPhotoUri = null;
            Toast.makeText(this, "Камеру не знайдено.", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCsv() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "MealCoach_meal_log.csv");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingPhotoUri != null) {
                MealEngine.savePhoto(this, pendingPhotoUri.toString());
                Toast.makeText(this, "Фото збережено в журнал.", Toast.LENGTH_SHORT).show();
            } else if (pendingPhotoUri != null) {
                getContentResolver().delete(pendingPhotoUri, null, null);
            }
            pendingPhotoUri = null;
            refreshHistory();
            return;
        }

        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = LogStore.copyTo(this, data.getData());
            Toast.makeText(this, ok ? "CSV експортовано." : "Журнал ще порожній.", Toast.LENGTH_SHORT).show();
        }
    }
}
