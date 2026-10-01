package com.mealcoach.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 42;
    private static final int REQ_EXPORT = 43;
    private static final int REQ_DEBUG_EXPORT = 44;

    private TextView screenTitle, statusChip, cycleLabel, countdown, deadlineText;
    private TextView mealsText, mealDotsText, snacksText, flowTitle, flowSteps;
    private TextView lastEventText, historyText, testStatus;
    private ProgressBar cycleProgress;
    private Button primaryButton, snackButton, delayButton, sleepButton;
    private Button exportButton, diagnosticExportButton, testButton;
    private Button navToday, navHistory, navSettings;
    private Button notificationSettingsButton, exactAlarmButton, batteryButton, autostartButton;
    private LinearLayout flowHint, secondaryActions;
    private ScrollView todayPage, historyPage, settingsPage;

    private final Handler handler = new Handler();
    private Uri pendingPhotoUri;
    private String pendingFoodAction;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            renderState();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getColor(R.color.bg));
        getWindow().setNavigationBarColor(getColor(R.color.bg));
        setContentView(R.layout.activity_main);

        applySystemInsets();
        NotificationHelper.ensureChannels(this);
        requestNotificationsIfNeeded();
        bindViews();
        wireActions();

        AlarmScheduler.scheduleCurrent(this);
        showPage(0);
        refreshAll();

        findViewById(R.id.root).postDelayed(() -> consumeForceCamera(getIntent()), 350L);
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void bindViews() {
        screenTitle = findViewById(R.id.screenTitle);
        statusChip = findViewById(R.id.statusChip);
        cycleLabel = findViewById(R.id.cycleLabel);
        countdown = findViewById(R.id.countdown);
        deadlineText = findViewById(R.id.deadlineText);
        mealsText = findViewById(R.id.mealsText);
        mealDotsText = findViewById(R.id.mealDotsText);
        snacksText = findViewById(R.id.snacksText);
        flowTitle = findViewById(R.id.flowTitle);
        flowSteps = findViewById(R.id.flowSteps);
        lastEventText = findViewById(R.id.lastEventText);
        historyText = findViewById(R.id.historyText);
        testStatus = findViewById(R.id.testStatus);
        cycleProgress = findViewById(R.id.cycleProgress);

        primaryButton = findViewById(R.id.primaryButton);
        snackButton = findViewById(R.id.snackButton);
        delayButton = findViewById(R.id.delayButton);
        sleepButton = findViewById(R.id.sleepButton);
        exportButton = findViewById(R.id.exportButton);
        diagnosticExportButton = findViewById(R.id.diagnosticExportButton);
        testButton = findViewById(R.id.testButton);

        navToday = findViewById(R.id.navToday);
        navHistory = findViewById(R.id.navHistory);
        navSettings = findViewById(R.id.navSettings);

        notificationSettingsButton = findViewById(R.id.notificationSettingsButton);
        exactAlarmButton = findViewById(R.id.exactAlarmButton);
        batteryButton = findViewById(R.id.batteryButton);
        autostartButton = findViewById(R.id.autostartButton);

        flowHint = findViewById(R.id.flowHint);
        secondaryActions = findViewById(R.id.secondaryActions);
        todayPage = findViewById(R.id.todayPage);
        historyPage = findViewById(R.id.historyPage);
        settingsPage = findViewById(R.id.settingsPage);
    }

    private void wireActions() {
        primaryButton.setOnClickListener(v -> {
            SharedPreferences p = MealEngine.prefs(this);
            if (!p.getBoolean(MealEngine.K_DAY, false)) {
                MealEngine.wake(this);
                refreshAll();
            } else if (p.getBoolean(MealEngine.K_EATING, false)) {
                MealEngine.ate(this);
                Toast.makeText(this, "Прийом їжі записано.", Toast.LENGTH_SHORT).show();
                refreshAll();
            } else {
                beginFoodPhoto("MEAL");
            }
        });

        snackButton.setOnClickListener(v -> beginFoodPhoto("SNACK"));

        delayButton.setOnClickListener(v -> {
            boolean ok = MealEngine.delay15(this);
            Toast.makeText(this,
                    ok ? "Додано 15 хв." : "Зараз відкладання недоступне або ліміт вичерпано.",
                    Toast.LENGTH_SHORT).show();
            refreshAll();
        });

        sleepButton.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Завершити день?")
                .setMessage("Усі нагадування зупиняться до наступного «Я прокинувся».")
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Лягаю спати", (d, w) -> {
                    MealEngine.sleep(this);
                    refreshAll();
                })
                .show());

        exportButton.setOnClickListener(v -> exportCsv());
        diagnosticExportButton.setOnClickListener(v -> exportDiagnostics());

        testButton.setOnClickListener(v -> {
            SharedPreferences p = MealEngine.prefs(this);
            if (p.getBoolean(MealEngine.K_DAY, false)) {
                new AlertDialog.Builder(this)
                        .setTitle("Запустити 6-хвилинний тест?")
                        .setMessage("Поточний день буде замінено тестовим циклом. Кожен рівень ескалації пройде приблизно за хвилину.")
                        .setNegativeButton("Скасувати", null)
                        .setPositiveButton("Запустити", (d, w) -> startTestNow())
                        .show();
            } else {
                startTestNow();
            }
        });

        navToday.setOnClickListener(v -> showPage(0));
        navHistory.setOnClickListener(v -> showPage(1));
        navSettings.setOnClickListener(v -> showPage(2));

        notificationSettingsButton.setOnClickListener(v -> openNotificationSettings());
        exactAlarmButton.setOnClickListener(v -> openExactAlarmSettings());
        batteryButton.setOnClickListener(v -> openBatterySettings());
        autostartButton.setOnClickListener(v -> openAutostartSettings());
    }

    private void startTestNow() {
        MealEngine.startTest(this);
        showPage(0);
        Toast.makeText(this, "Тест запущено. Не закривай сповіщення вручну — дивимось повний ланцюжок.", Toast.LENGTH_LONG).show();
        refreshAll();
    }

    private void showPage(int page) {
        todayPage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == 2 ? View.VISIBLE : View.GONE);

        navToday.setTextColor(getColor(page == 0 ? R.color.accent : R.color.muted));
        navHistory.setTextColor(getColor(page == 1 ? R.color.accent : R.color.muted));
        navSettings.setTextColor(getColor(page == 2 ? R.color.accent : R.color.muted));

        screenTitle.setText(page == 0 ? "MEAL COACH" : page == 1 ? "ІСТОРІЯ" : "НАЛАШТУВАННЯ");
        if (page == 1) refreshHistory();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        findViewById(R.id.root).postDelayed(() -> consumeForceCamera(intent), 250L);
    }

    private void consumeForceCamera(Intent intent) {
        if (intent != null && intent.getBooleanExtra("force_camera", false)) {
            intent.removeExtra("force_camera");
            SharedPreferences p = MealEngine.prefs(this);
            if (p.getBoolean(MealEngine.K_DAY, false) && !p.getBoolean(MealEngine.K_EATING, false)) {
                showPage(0);
                beginFoodPhoto("MEAL");
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
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
        boolean test = p.getBoolean(MealEngine.K_TEST, false);
        int meals = p.getInt(MealEngine.K_MEALS, 0);
        int snacks = p.getInt(MealEngine.K_SNACKS, 0);

        mealDotsText.setText(mealDots(meals));
        mealsText.setText(meals + " / 3+ прийомів" + (meals >= 3 ? "  ✓" : ""));
        snacksText.setText("ПЕРЕКУСИ  " + snacks);
        lastEventText.setText(LogStore.recent(this, 1));
        testStatus.setText(test
                ? "ТЕСТ АКТИВНИЙ: цикл стиснуто до 6 хвилин."
                : "6 хвилин: кожна стадія ескалації проходить приблизно за хвилину.");

        if (!day) {
            statusChip.setText("НЕ ЗАПУЩЕНО");
            statusChip.setTextColor(getColor(R.color.muted));
            cycleLabel.setText("ДЕНЬ ЩЕ НЕ ЗАПУЩЕНО");
            countdown.setText("ПОЧАТИ ДЕНЬ");
            countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("Після пробудження запустимо перше вікно їжі");
            cycleProgress.setProgress(0);

            flowHint.setVisibility(View.GONE);
            secondaryActions.setVisibility(View.GONE);
            sleepButton.setVisibility(View.GONE);
            primaryButton.setText("Я ПРОКИНУВСЯ");
            return;
        }

        sleepButton.setVisibility(View.VISIBLE);

        boolean eating = p.getBoolean(MealEngine.K_EATING, false);
        boolean first = p.getBoolean(MealEngine.K_FIRST, false);
        long start = p.getLong(MealEngine.K_START, System.currentTimeMillis());
        long pref = p.getLong(MealEngine.K_PREF, start);
        long dead = p.getLong(MealEngine.K_DEADLINE, pref);
        long now = System.currentTimeMillis();

        cycleLabel.setText(test ? "ТЕСТОВИЙ ЦИКЛ" : first ? "ПЕРША ЇЖА" : "НАСТУПНА ЇЖА");

        if (eating) {
            long eatingStart = p.getLong(MealEngine.K_EATING_START, now);
            statusChip.setText("ЇМ ЗАРАЗ");
            statusChip.setTextColor(getColor(R.color.accent));
            countdown.setText(formatDuration(now - eatingStart));
            countdown.setTextColor(getColor(R.color.accent));
            deadlineText.setText("Таймер прийому їжі • фото вже записане");
            cycleProgress.setProgress(1000);

            flowHint.setVisibility(View.VISIBLE);
            flowTitle.setText("ЗАРАЗ ТИ ЇСИ");
            flowSteps.setText("Їж нормально, не поспішай.\nКоли реально закінчиш — натисни кнопку нижче.");
            primaryButton.setText("ЗАКІНЧИВ ЇСТИ");
            secondaryActions.setVisibility(View.GONE);
            return;
        }

        flowHint.setVisibility(View.VISIBLE);
        flowTitle.setText("КОЛИ СІДАЄШ ЇСТИ");
        flowSteps.setText("1  Сфотографуй їжу\n2  Після фото починай їсти\n3  Коли закінчиш — натисни «Закінчив їсти»");
        primaryButton.setText("СФОТОГРАФУВАТИ ЇЖУ → ПОЧАТИ");
        secondaryActions.setVisibility(View.VISIBLE);
        delayButton.setVisibility(now >= pref ? View.VISIBLE : View.GONE);

        long span = Math.max(1L, dead - start);
        int progress = (int) Math.max(0, Math.min(1000, ((now - start) * 1000L) / span));
        cycleProgress.setProgress(progress);

        if (now < pref) {
            statusChip.setText(test ? "ТЕСТ" : "ВСЕ ДОБРЕ");
            statusChip.setTextColor(getColor(R.color.accent));
            countdown.setText(formatDuration(pref - now));
            countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("До бажаного часу • максимум " + clock(dead));
        } else if (now < dead) {
            statusChip.setText("ВЖЕ ПОРА");
            statusChip.setTextColor(getColor(R.color.warn));
            countdown.setText(formatDuration(dead - now));
            countdown.setTextColor(getColor(R.color.warn));
            deadlineText.setText("До максимальної межі • " + clock(dead));
        } else {
            statusChip.setText("ПРОСТРОЧЕНО");
            statusChip.setTextColor(getColor(R.color.danger));
            countdown.setText("+" + formatDuration(now - dead));
            countdown.setTextColor(getColor(R.color.danger));
            deadlineText.setText("Максимальна межа пройдена");
        }
    }

    private String mealDots(int meals) {
        String a = meals >= 1 ? "●" : "○";
        String b = meals >= 2 ? "●" : "○";
        String c = meals >= 3 ? "●" : "○";
        return a + "  " + b + "  " + c;
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
        if (historyText != null) historyText.setText(LogStore.recent(this, 100));
    }

    private void beginFoodPhoto(String action) {
        SharedPreferences p = MealEngine.prefs(this);
        if (!p.getBoolean(MealEngine.K_DAY, false) || p.getBoolean(MealEngine.K_EATING, false)) return;

        pendingFoodAction = action;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "MealCoach_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MealCoach");
        }

        pendingPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingPhotoUri == null) {
            Toast.makeText(this, "Не вдалося створити файл для фото.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
        if (camera.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(camera, REQ_CAMERA);
        } else {
            getContentResolver().delete(pendingPhotoUri, null, null);
            pendingPhotoUri = null;
            pendingFoodAction = null;
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

    private void exportDiagnostics() {
        DiagnosticStore.log(this, "DIAGNOSTIC_EXPORT_REQUEST", "user requested export");
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "MealCoach_debug.log");
        startActivityForResult(i, REQ_DEBUG_EXPORT);
    }

    private void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    private void openExactAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                Toast.makeText(this, "На цій версії Android окремий дозвіл не потрібен.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAutostartSettings() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(i);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingPhotoUri != null) {
                MealEngine.savePhoto(this, pendingPhotoUri.toString());

                if ("SNACK".equals(pendingFoodAction)) {
                    boolean shifted = MealEngine.snack(this);
                    Toast.makeText(this,
                            shifted
                                    ? "Перекус записано. Наступну основну їжу посунуто на годину."
                                    : "Перекус записано. Це третій поспіль, тому дедлайн більше не переноситься.",
                            Toast.LENGTH_LONG).show();
                } else {
                    MealEngine.startEating(this);
                    Toast.makeText(this,
                            "Фото записано. Тепер їж; коли закінчиш — натисни «Закінчив їсти».",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                if (pendingPhotoUri != null) {
                    getContentResolver().delete(pendingPhotoUri, null, null);
                }
                Toast.makeText(this, "Фото скасовано — прийом їжі не розпочато.", Toast.LENGTH_SHORT).show();
            }
            pendingPhotoUri = null;
            pendingFoodAction = null;
            refreshAll();
            return;
        }

        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = LogStore.copyTo(this, data.getData());
            Toast.makeText(this, ok ? "CSV експортовано." : "Журнал ще порожній.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestCode == REQ_DEBUG_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = DiagnosticStore.copyTo(this, data.getData());
            Toast.makeText(this, ok ? "Діагностику експортовано." : "Діагностичних логів ще немає.", Toast.LENGTH_SHORT).show();
        }
    }
}
