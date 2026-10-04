package com.mealcoach.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA=42;
    private static final int REQ_DEBUG_EXPORT=44;
    private static final int REQ_FOOD_SOUND=45;
    private static final int REQ_WATER_SOUND=46;
    private static final int REQ_DATA_EXPORT=47;

    private TextView screenTitle,statusChip,cycleLabel,countdown,deadlineText,mealDotsText,mealsText,snacksText;
    private TextView flowTitle,flowSteps,lastEventText,waterStateText,waterTotalText,waterPaceText,waterWarningText,waterHistoryText;
    private TextView streakRankText,streakDaysText,streakStatsText,streakNextText,streakBadgesText,streakMarksText,streakArchiveText;
    private TextView reliabilityText,testStatus,currentStatsText,historyStatsText,foodHistoryText;
    private ProgressBar cycleProgress,waterProgress,statsFoodProgress,statsWaterProgress,statsStreakProgress,testProgress;
    private StreakSceneView streakSceneView;
    private HydrationTankView hydrationTankView;
    private WaterTimelineView waterTimelineView;

    private Button primaryButton,snackButton,delayButton,extendEatingButton,sleepButton;
    private Button waterQuick1,waterQuick2,waterQuick3,waterCustomButton,waterUndoButton,waterHistoryEditButton;
    private Button streakResetButton,statsButton,settingsButton,closeStatsButton,closeSettingsButton,navFood,navWater,navStreak;
    private Button foodModuleButton,waterModuleButton,streakModuleButton;
    private Button notificationSettingsButton,exactAlarmButton,fullScreenButton,batteryButton,accessibilityButton,autostartButton;
    private Button waterGoalSettingsButton,waterQuickSettingsButton,foodSoundButton,waterSoundButton;
    private Button testButton,testNextButton,diagnosticExportButton,dataExportButton,memeModeButton;

    private LinearLayout flowHint,secondaryActions,bottomNav;
    private ScrollView foodPage,waterPage,streakPage,statsPage,settingsPage;

    private final Handler handler=new Handler();
    private Uri pendingPhotoUri;
    private String pendingFoodAction;
    private int currentTab=0;
    private boolean overlayOpen=false;
    private float touchDownX,touchDownY;
    private long undoHideAt=0L;

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            renderAll();
            handler.postDelayed(this,1000L);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(getColor(R.color.bg));
        getWindow().setNavigationBarColor(getColor(R.color.bg));
        setContentView(R.layout.activity_main);

        applyInsets();
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        bind();
        wire();

        StreakEngine.ensure(this);
        AlarmScheduler.scheduleCurrent(this);
        WatchdogScheduler.schedule(this);
        if(HydrationEngine.active(this))WaterScheduler.scheduleNext(this,60*60_000L);

        showTabInstant(getIntent().getIntExtra("open_tab",0));
        if(getIntent().getBooleanExtra("open_settings",false))openSettings();
        else if(!AppSettings.setupDone(this)){
            openSettings();
            handler.postDelayed(()->new AlertDialog.Builder(this)
                    .setTitle("Перший запуск")
                    .setMessage("Для надійних нагадувань пройди пункти з ✕ у блоці «Надійність». Особливо: сповіщення, точні будильники, full-screen alarm, батарея та автозапуск Xiaomi.")
                    .setPositiveButton("Зрозуміло",null)
                    .show(),300L);
        }
        handler.postDelayed(()->consumeForceCamera(getIntent()),300L);
        renderAll();
    }

    private void applyInsets(){
        View root=findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void bind(){
        screenTitle=findViewById(R.id.screenTitle);
        statusChip=findViewById(R.id.statusChip);
        cycleLabel=findViewById(R.id.cycleLabel);
        countdown=findViewById(R.id.countdown);
        deadlineText=findViewById(R.id.deadlineText);
        mealDotsText=findViewById(R.id.mealDotsText);
        mealsText=findViewById(R.id.mealsText);
        snacksText=findViewById(R.id.snacksText);
        flowTitle=findViewById(R.id.flowTitle);
        flowSteps=findViewById(R.id.flowSteps);
        lastEventText=findViewById(R.id.lastEventText);

        waterStateText=findViewById(R.id.waterStateText);
        waterTotalText=findViewById(R.id.waterTotalText);
        waterPaceText=findViewById(R.id.waterPaceText);
        waterWarningText=findViewById(R.id.waterWarningText);
        waterHistoryText=findViewById(R.id.waterHistoryText);

        streakRankText=findViewById(R.id.streakRankText);
        streakDaysText=findViewById(R.id.streakDaysText);
        streakStatsText=findViewById(R.id.streakStatsText);
        streakNextText=findViewById(R.id.streakNextText);
        streakBadgesText=findViewById(R.id.streakBadgesText);
        streakMarksText=findViewById(R.id.streakMarksText);
        streakArchiveText=findViewById(R.id.streakArchiveText);
        streakSceneView=findViewById(R.id.streakSceneView);

        reliabilityText=findViewById(R.id.reliabilityText);
        testStatus=findViewById(R.id.testStatus);
        currentStatsText=findViewById(R.id.currentStatsText);
        historyStatsText=findViewById(R.id.historyStatsText);
        foodHistoryText=findViewById(R.id.foodHistoryText);

        cycleProgress=findViewById(R.id.cycleProgress);
        waterProgress=findViewById(R.id.waterProgress);
        hydrationTankView=findViewById(R.id.hydrationTankView);
        waterTimelineView=findViewById(R.id.waterTimelineView);
        statsFoodProgress=findViewById(R.id.statsFoodProgress);
        statsWaterProgress=findViewById(R.id.statsWaterProgress);
        statsStreakProgress=findViewById(R.id.statsStreakProgress);
        testProgress=findViewById(R.id.testProgress);

        primaryButton=findViewById(R.id.primaryButton);
        snackButton=findViewById(R.id.snackButton);
        delayButton=findViewById(R.id.delayButton);
        extendEatingButton=findViewById(R.id.extendEatingButton);
        sleepButton=findViewById(R.id.sleepButton);

        waterQuick1=findViewById(R.id.waterQuick1);
        waterQuick2=findViewById(R.id.waterQuick2);
        waterQuick3=findViewById(R.id.waterQuick3);
        waterCustomButton=findViewById(R.id.waterCustomButton);
        waterUndoButton=findViewById(R.id.waterUndoButton);
        waterHistoryEditButton=findViewById(R.id.waterHistoryEditButton);

        streakResetButton=findViewById(R.id.streakResetButton);

        statsButton=findViewById(R.id.statsButton);
        settingsButton=findViewById(R.id.settingsButton);
        closeStatsButton=findViewById(R.id.closeStatsButton);
        closeSettingsButton=findViewById(R.id.closeSettingsButton);

        navFood=findViewById(R.id.navFood);
        navWater=findViewById(R.id.navWater);
        navStreak=findViewById(R.id.navStreak);

        foodModuleButton=findViewById(R.id.foodModuleButton);
        waterModuleButton=findViewById(R.id.waterModuleButton);
        streakModuleButton=findViewById(R.id.streakModuleButton);

        notificationSettingsButton=findViewById(R.id.notificationSettingsButton);
        exactAlarmButton=findViewById(R.id.exactAlarmButton);
        fullScreenButton=findViewById(R.id.fullScreenButton);
        batteryButton=findViewById(R.id.batteryButton);
        accessibilityButton=findViewById(R.id.accessibilityButton);
        autostartButton=findViewById(R.id.autostartButton);

        waterGoalSettingsButton=findViewById(R.id.waterGoalSettingsButton);
        waterQuickSettingsButton=findViewById(R.id.waterQuickSettingsButton);
        foodSoundButton=findViewById(R.id.foodSoundButton);
        waterSoundButton=findViewById(R.id.waterSoundButton);

        testButton=findViewById(R.id.testButton);
        testNextButton=findViewById(R.id.testNextButton);
        diagnosticExportButton=findViewById(R.id.diagnosticExportButton);
        dataExportButton=findViewById(R.id.dataExportButton);
        memeModeButton=findViewById(R.id.memeModeButton);

        flowHint=findViewById(R.id.flowHint);
        secondaryActions=findViewById(R.id.secondaryActions);
        bottomNav=findViewById(R.id.bottomNav);

        foodPage=findViewById(R.id.foodPage);
        waterPage=findViewById(R.id.waterPage);
        streakPage=findViewById(R.id.streakPage);
        statsPage=findViewById(R.id.statsPage);
        settingsPage=findViewById(R.id.settingsPage);
    }

    private void wire(){
        primaryButton.setOnClickListener(v->{
            SharedPreferences p=MealEngine.prefs(this);
            if(!p.getBoolean(MealEngine.K_DAY,false)){
                MealEngine.wake(this);
                renderAll();
            }else if(p.getBoolean(MealEngine.K_EATING,false)){
                MealEngine.ate(this);
                Toast.makeText(this,"Прийом завершено.",Toast.LENGTH_SHORT).show();
                renderAll();
            }else{
                beginFoodPhoto("MEAL");
            }
        });

        snackButton.setOnClickListener(v->beginFoodPhoto("SNACK"));
        delayButton.setOnClickListener(v->{
            boolean ok=MealEngine.delay15(this);
            Toast.makeText(this,ok?"+15 хв":"Зараз недоступно або ліміт вичерпано",Toast.LENGTH_SHORT).show();
            renderAll();
        });
        extendEatingButton.setOnClickListener(v->{
            boolean ok=MealEngine.extendEating(this);
            Toast.makeText(this,ok?"Продовжено на 10 хв":"Більше продовжувати не можна",Toast.LENGTH_SHORT).show();
            renderAll();
        });
        sleepButton.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Завершити wake-день?")
                .setMessage("Їжа і вода зупиняться до наступного пробудження.")
                .setNegativeButton("Скасувати",null)
                .setPositiveButton("Лягаю спати",(d,w)->{MealEngine.sleep(this);renderAll();})
                .show());

        waterQuick1.setOnClickListener(v->addWater(HydrationEngine.quick(this,1)));
        waterQuick2.setOnClickListener(v->addWater(HydrationEngine.quick(this,2)));
        waterQuick3.setOnClickListener(v->addWater(HydrationEngine.quick(this,3)));

        waterQuick1.setOnLongClickListener(v->{editQuickButton(1);return true;});
        waterQuick2.setOnLongClickListener(v->{editQuickButton(2);return true;});
        waterQuick3.setOnLongClickListener(v->{editQuickButton(3);return true;});
        waterCustomButton.setOnClickListener(v->{
            if(!MealEngine.prefs(this).getBoolean(MealEngine.K_DAY,false)){
                MealEngine.wake(this);
                Toast.makeText(this,"Wake-день запущено.",Toast.LENGTH_SHORT).show();
                renderAll();
            }else{
                askNumber("Скільки мл випив?",0,value->addWater(value));
            }
        });
        waterUndoButton.setOnClickListener(v->{
            if(HydrationEngine.undo(this)){
                undoHideAt=0L;
                Toast.makeText(this,"Останній запис скасовано.",Toast.LENGTH_SHORT).show();
                renderAll();
            }
        });
        waterHistoryEditButton.setOnClickListener(v->editWaterHistory());

        streakResetButton.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Точно записати?")
                .setMessage("Поточна серія почнеться заново. Рекорд та архів залишаться.")
                .setNegativeButton("Ні",null)
                .setPositiveButton("Так, подрочив",(d,w)->resetStreak())
                .show());

        navFood.setOnClickListener(v->showTabAnimated(0,tabDirection(0)));
        navWater.setOnClickListener(v->showTabAnimated(1,tabDirection(1)));
        navStreak.setOnClickListener(v->showTabAnimated(2,tabDirection(2)));

        statsButton.setOnClickListener(v->openStats());
        settingsButton.setOnClickListener(v->openSettings());
        closeStatsButton.setOnClickListener(v->closeOverlay());
        closeSettingsButton.setOnClickListener(v->closeOverlay());

        foodModuleButton.setOnClickListener(v->toggleFoodModule());
        waterModuleButton.setOnClickListener(v->toggleWaterModule());
        streakModuleButton.setOnClickListener(v->{AppSettings.setStreakEnabled(this,!AppSettings.streakEnabled(this));renderAll();});

        notificationSettingsButton.setOnClickListener(v->openNotificationSettings());
        exactAlarmButton.setOnClickListener(v->openExactAlarmSettings());
        fullScreenButton.setOnClickListener(v->openFullScreenSettings());
        batteryButton.setOnClickListener(v->openBatterySettings());
        accessibilityButton.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        autostartButton.setOnClickListener(v->openAutostartSettings());

        waterGoalSettingsButton.setOnClickListener(v->askNumber("Денна ціль води, мл",HydrationEngine.goal(this),value->{HydrationEngine.setGoal(this,value);renderAll();}));
        waterQuickSettingsButton.setOnClickListener(v->chooseQuickButton());

        foodSoundButton.setOnClickListener(v->chooseSound(true));
        waterSoundButton.setOnClickListener(v->chooseSound(false));

        testButton.setOnClickListener(v->{
            ReminderTestManager.start(this);
            Toast.makeText(this,"Фоновий тест запущено. Згорни додаток і не відкривай 6 хв.",Toast.LENGTH_LONG).show();
            renderAll();
        });
        testNextButton.setOnClickListener(v->{ReminderTestManager.nextNow(this);renderAll();});
        diagnosticExportButton.setOnClickListener(v->exportDiagnostics());
        dataExportButton.setOnClickListener(v->exportAllData());
        memeModeButton.setOnClickListener(v->{StreakEngine.toggleMeme(this);renderAll();});
    }

    private int tabDirection(int target){
        if(target==currentTab)return 0;
        if(target==(currentTab+1)%3)return 1;
        return -1;
    }

    private View pageFor(int tab){
        return tab==0?foodPage:tab==1?waterPage:streakPage;
    }

    private void showTabInstant(int tab){
        currentTab=((tab%3)+3)%3;
        overlayOpen=false;
        applyPageTone(currentTab,false);
        foodPage.setVisibility(currentTab==0?View.VISIBLE:View.GONE);
        waterPage.setVisibility(currentTab==1?View.VISIBLE:View.GONE);
        streakPage.setVisibility(currentTab==2?View.VISIBLE:View.GONE);
        statsPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        bottomNav.setVisibility(View.VISIBLE);
        updateNav();
        renderAll();
    }

    private void showTabAnimated(int tab,int direction){
        int target=((tab%3)+3)%3;
        if(target==currentTab&& !overlayOpen)return;
        if(overlayOpen){showTabInstant(target);return;}

        View old=pageFor(currentTab);
        View next=pageFor(target);
        int width=findViewById(R.id.contentFrame).getWidth();
        if(width<=0)width=getResources().getDisplayMetrics().widthPixels;
        int dir=direction==0?1:direction;

        next.setVisibility(View.VISIBLE);
        next.setTranslationX(dir*width);
        next.setAlpha(0.72f);
        next.animate().translationX(0).alpha(1f).setDuration(220).start();
        final View oldRef=old;
        old.animate().translationX(-dir*width).alpha(0.72f).setDuration(220).withEndAction(()->{
            oldRef.setVisibility(View.GONE);
            oldRef.setTranslationX(0);
            oldRef.setAlpha(1f);
        }).start();

        int oldTab=currentTab;
        currentTab=target;
        animatePageTone(oldTab,target);
        findViewById(R.id.root).performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        updateNav();
        renderAll();
    }

    private int pageColor(int tab){
        if(tab==1)return Color.rgb(8,14,22);
        if(tab==2)return Color.rgb(16,10,21);
        return Color.rgb(9,16,16);
    }

    private void applyPageTone(int tab,boolean updateSystemBars){
        int color=pageColor(tab);
        findViewById(R.id.root).setBackgroundColor(color);
        if(updateSystemBars){
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
        }
    }

    private void animatePageTone(int fromTab,int toTab){
        int from=pageColor(fromTab),to=pageColor(toTab);
        ValueAnimator a=ValueAnimator.ofArgb(from,to);
        a.setDuration(220L);
        a.addUpdateListener(v->{
            int color=(int)v.getAnimatedValue();
            findViewById(R.id.root).setBackgroundColor(color);
            getWindow().setStatusBarColor(color);
        });
        a.start();
    }

    private void updateNav(){
        navFood.setTextColor(getColor(currentTab==0?R.color.accent:R.color.muted));
        navWater.setTextColor(currentTab==1?getColor(R.color.accent):getColor(R.color.muted));
        navStreak.setTextColor(currentTab==2?Color.rgb(190,120,255):getColor(R.color.muted));
        screenTitle.setText(currentTab==0?"ДЕСЯТИЙ · ЇЖА":currentTab==1?"ДЕСЯТИЙ · ВОДА":"ДЕСЯТИЙ · СЕРІЯ");
    }

    private void openStats(){
        overlayOpen=true;
        findViewById(R.id.root).setBackgroundColor(getColor(R.color.bg));
        getWindow().setStatusBarColor(getColor(R.color.bg));
        foodPage.setVisibility(View.GONE);
        waterPage.setVisibility(View.GONE);
        streakPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        statsPage.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.GONE);
        screenTitle.setText("ДЕСЯТИЙ · СТАТИСТИКА");
        statusChip.setText("▥");
        renderStats();
    }

    private void openSettings(){
        overlayOpen=true;
        findViewById(R.id.root).setBackgroundColor(getColor(R.color.bg));
        getWindow().setStatusBarColor(getColor(R.color.bg));
        foodPage.setVisibility(View.GONE);
        waterPage.setVisibility(View.GONE);
        streakPage.setVisibility(View.GONE);
        statsPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.GONE);
        screenTitle.setText("ДЕСЯТИЙ · НАЛАШТУВАННЯ");
        statusChip.setText("⚙");
        renderSettings();
    }

    private void closeOverlay(){
        if(settingsPage.getVisibility()==View.VISIBLE)AppSettings.setSetupDone(this,true);
        showTabInstant(currentTab);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            touchDownX=e.getX();touchDownY=e.getY();
        }else if(e.getAction()==MotionEvent.ACTION_UP&&!overlayOpen){
            float dx=e.getX()-touchDownX;
            float dy=e.getY()-touchDownY;
            if(Math.abs(dx)>150&&Math.abs(dx)>Math.abs(dy)*1.45f){
                if(dx<0)showTabAnimated((currentTab+1)%3,1);
                else showTabAnimated((currentTab+2)%3,-1);
            }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        if(i.getBooleanExtra("open_settings",false))openSettings();
        else if(i.hasExtra("open_tab"))showTabInstant(i.getIntExtra("open_tab",0));
        handler.postDelayed(()->consumeForceCamera(i),200L);
    }

    private void consumeForceCamera(Intent i){
        if(i!=null&&i.getBooleanExtra("force_camera",false)){
            i.removeExtra("force_camera");
            showTabInstant(0);
            SharedPreferences p=MealEngine.prefs(this);
            if(p.getBoolean(MealEngine.K_DAY,false)&&!p.getBoolean(MealEngine.K_EATING,false))beginFoodPhoto("MEAL");
        }
    }

    @Override protected void onResume(){
        super.onResume();
        renderAll();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause(){
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void renderAll(){
        renderFood();
        renderWater();
        renderStreak();
        if(statsPage.getVisibility()==View.VISIBLE)renderStats();
        if(settingsPage.getVisibility()==View.VISIBLE)renderSettings();
    }

    private void renderFood(){
        SharedPreferences p=MealEngine.prefs(this);
        boolean enabled=AppSettings.foodEnabled(this);
        boolean day=p.getBoolean(MealEngine.K_DAY,false);
        int meals=p.getInt(MealEngine.K_MEALS,0);
        int snacks=p.getInt(MealEngine.K_SNACKS,0);

        mealDotsText.setText((meals>=1?"●":"○")+"  "+(meals>=2?"●":"○")+"  "+(meals>=3?"●":"○"));
        mealsText.setText(meals+" / 3+ прийомів"+(meals>=3?"  ✓":""));
        snacksText.setText("ПЕРЕКУСИ "+snacks);
        lastEventText.setText(LogStore.recent(this,1));

        if(!enabled){
            cycleLabel.setText("МОДУЛЬ ЇЖІ ВИМКНЕНО");
            countdown.setText("OFF");
            countdown.setTextColor(getColor(R.color.muted));
            deadlineText.setText("Увімкни модуль у налаштуваннях.");
            cycleProgress.setProgress(0);
            flowHint.setVisibility(View.GONE);
            secondaryActions.setVisibility(View.GONE);
            extendEatingButton.setVisibility(View.GONE);
            sleepButton.setVisibility(day?View.VISIBLE:View.GONE);
            primaryButton.setEnabled(false);
            primaryButton.setText("ЇЖА ВИМКНЕНА");
            if(!overlayOpen&&currentTab==0){statusChip.setText("OFF");statusChip.setTextColor(getColor(R.color.muted));}
            return;
        }

        primaryButton.setEnabled(true);

        if(!day){
            if(!overlayOpen&&currentTab==0){statusChip.setText("НЕ ЗАПУЩЕНО");statusChip.setTextColor(getColor(R.color.muted));}
            cycleLabel.setText("ДЕНЬ ЩЕ НЕ ЗАПУЩЕНО");
            countdown.setText("ПОЧАТИ ДЕНЬ");
            countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("Пробудження запускає їжу і воду");
            cycleProgress.setProgress(0);
            flowHint.setVisibility(View.GONE);
            secondaryActions.setVisibility(View.GONE);
            extendEatingButton.setVisibility(View.GONE);
            sleepButton.setVisibility(View.GONE);
            primaryButton.setText("Я ПРОКИНУВСЯ");
            return;
        }

        sleepButton.setVisibility(View.VISIBLE);
        boolean eating=p.getBoolean(MealEngine.K_EATING,false);
        long now=System.currentTimeMillis();
        long start=p.getLong(MealEngine.K_START,now);
        long pref=p.getLong(MealEngine.K_PREF,start);
        long dead=p.getLong(MealEngine.K_DEADLINE,pref);
        cycleLabel.setText(p.getBoolean(MealEngine.K_FIRST,false)?"ПЕРША ЇЖА":"НАСТУПНА ЇЖА");

        if(eating){
            long es=p.getLong(MealEngine.K_EATING_START,now);
            int ext=p.getInt(MealEngine.K_EATING_EXT,0);
            if(!overlayOpen&&currentTab==0){statusChip.setText("ЇМ ЗАРАЗ");statusChip.setTextColor(getColor(R.color.accent));}
            countdown.setText(formatDuration(now-es));
            countdown.setTextColor(getColor(R.color.accent));
            deadlineText.setText("Автозавершення через "+formatDuration(Math.max(0,es+(30L+10L*ext)*60_000L-now)));
            cycleProgress.setProgress(1000);
            flowHint.setVisibility(View.VISIBLE);
            flowTitle.setText("ЗАРАЗ ТИ ЇСИ");
            flowSteps.setText("Через 20 хв буде «Ти ще їси?». Через 30 хв — автоматичне завершення.");
            primaryButton.setText("ЗАКІНЧИВ ЇСТИ");
            secondaryActions.setVisibility(View.GONE);
            extendEatingButton.setVisibility(ext<2?View.VISIBLE:View.GONE);
            return;
        }

        flowHint.setVisibility(View.VISIBLE);
        flowTitle.setText("КОЛИ СІДАЄШ ЇСТИ");
        flowSteps.setText("1  Сфотографуй їжу\n2  Після фото починай їсти\n3  Якщо забудеш завершити — додаток підстрахує");
        primaryButton.setText("СФОТОГРАФУВАТИ ЇЖУ → ПОЧАТИ");
        secondaryActions.setVisibility(View.VISIBLE);
        extendEatingButton.setVisibility(View.GONE);
        int delayUsed=p.getInt(MealEngine.K_DELAY_USED,0);
        delayButton.setVisibility(now>=pref&&now<pref+30L*60_000L&&delayUsed<2?View.VISIBLE:View.GONE);

        int prog=(int)Math.max(0,Math.min(1000,((now-start)*1000L)/Math.max(1,dead-start)));
        cycleProgress.setProgress(prog);

        if(now<pref){
            if(!overlayOpen&&currentTab==0){statusChip.setText("ВСЕ ДОБРЕ");statusChip.setTextColor(getColor(R.color.accent));}
            countdown.setText(formatDuration(pref-now));
            countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("До бажаного часу • максимум "+clock(dead));
            cycleProgress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        }else if(now<dead){
            if(!overlayOpen&&currentTab==0){statusChip.setText("НЕ ТЯГНИ");statusChip.setTextColor(getColor(R.color.orange));}
            countdown.setText(formatDuration(dead-now));
            countdown.setTextColor(getColor(R.color.orange));
            deadlineText.setText("До максимальної межі • "+clock(dead));
            cycleProgress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.orange)));
        }else{
            if(!overlayOpen&&currentTab==0){statusChip.setText("ПРОСТРОЧЕНО");statusChip.setTextColor(getColor(R.color.danger));}
            countdown.setText("+"+formatDuration(now-dead));
            countdown.setTextColor(getColor(R.color.danger));
            deadlineText.setText("Максимальна межа пройдена");
            cycleProgress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.danger)));
        }
    }

    private void renderWater(){
        boolean enabled=AppSettings.waterEnabled(this);
        boolean active=HydrationEngine.active(this);
        int total=HydrationEngine.total(this);
        int goal=HydrationEngine.goal(this);

        waterProgress.setMax(goal);
        waterProgress.setProgress(Math.min(goal,total));
        waterTotalText.setText(total+" / "+goal+" мл");
        waterQuick1.setText("+"+HydrationEngine.quick(this,1));
        waterQuick2.setText("+"+HydrationEngine.quick(this,2));
        waterQuick3.setText("+"+HydrationEngine.quick(this,3));
        waterHistoryText.setText(HydrationEngine.recent(this,6));
        waterUndoButton.setVisibility(System.currentTimeMillis()<undoHideAt?View.VISIBLE:View.GONE);
        hydrationTankView.setData(total,goal,HydrationEngine.expectedNow(this),HydrationEngine.status(this));
        waterTimelineView.setData(
                HydrationEngine.drinkPoints(this),
                HydrationEngine.wakeTime(this),
                HydrationEngine.wakeTime(this)+HydrationEngine.expectedAwakeMs(this),
                goal
        );

        if(!enabled){
            waterStateText.setText("МОДУЛЬ ВОДИ ВИМКНЕНО");
            waterStateText.setTextColor(getColor(R.color.muted));
            waterPaceText.setText("Увімкни модуль у налаштуваннях.");
            waterWarningText.setText("—");
            waterCustomButton.setText("ВОДА ВИМКНЕНА");
            waterCustomButton.setEnabled(false);
            waterQuick1.setEnabled(false);waterQuick2.setEnabled(false);waterQuick3.setEnabled(false);
            if(!overlayOpen&&currentTab==1){statusChip.setText("OFF");statusChip.setTextColor(getColor(R.color.muted));}
            return;
        }

        waterCustomButton.setEnabled(true);
        if(!active){
            waterStateText.setText("ДЕНЬ НЕ ЗАПУЩЕНО");
            waterStateText.setTextColor(getColor(R.color.muted));
            waterPaceText.setText("Графік прив'язаний до фактичного пробудження, а не до 00:00.");
            waterWarningText.setText("Ціль: "+goal+" мл.");
            waterCustomButton.setText("Я ПРОКИНУВСЯ");
            waterQuick1.setEnabled(false);waterQuick2.setEnabled(false);waterQuick3.setEnabled(false);
            if(!overlayOpen&&currentTab==1){statusChip.setText("НЕ ЗАПУЩЕНО");statusChip.setTextColor(getColor(R.color.muted));}
            return;
        }

        waterCustomButton.setText("ВВЕСТИ СВОЮ КІЛЬКІСТЬ");
        waterQuick1.setEnabled(true);waterQuick2.setEnabled(true);waterQuick3.setEnabled(true);

        int st=HydrationEngine.status(this);
        int expected=HydrationEngine.expectedNow(this);
        int projected=HydrationEngine.projectedTotal(this);
        int color=st==HydrationEngine.GREEN?getColor(R.color.accent):st==HydrationEngine.ORANGE?getColor(R.color.orange):getColor(R.color.danger);

        waterStateText.setText(HydrationEngine.statusLabel(this));
        waterStateText.setTextColor(color);
        waterProgress.setProgressTintList(ColorStateList.valueOf(color));
        waterWarningText.setText(HydrationEngine.warning(this)+"\n\n"+HydrationEngine.nextPlan(this));
        waterPaceText.setText("Орієнтир зараз ≈ "+expected+" мл • прогноз ≈ "+projected+" мл\nНайдовша пауза сьогодні: "+HydrationEngine.longestGapMinutes(this)+" хв • план вчиться з твоїх wake-днів.");

        if(!overlayOpen&&currentTab==1){statusChip.setText(HydrationEngine.statusLabel(this));statusChip.setTextColor(color);}
    }

    private void renderStreak(){
        boolean enabled=AppSettings.streakEnabled(this);
        StreakEngine.ensure(this);
        int d=StreakEngine.days(this);

        streakDaysText.setText(d+" "+daysWord(d));
        streakRankText.setText(enabled?StreakEngine.rank(this):"МОДУЛЬ ВИМКНЕНО");
        streakSceneView.setDays(enabled?d:0);
        streakStatsText.setText("Рекорд "+StreakEngine.best(this)+" • чистих днів "+StreakEngine.cleanDaysTracked(this)+"/"+StreakEngine.trackedWindow(this));
        streakNextText.setText(StreakEngine.nextMilestone(this));
        streakBadgesText.setText(StreakEngine.badges(this));
        streakMarksText.setText(StreakEngine.dayMarks(this,10));
        streakArchiveText.setText(StreakEngine.archiveText(this,6));
        streakResetButton.setVisibility(enabled?View.VISIBLE:View.GONE);

        if(!overlayOpen&&currentTab==2){
            statusChip.setText(enabled?StreakEngine.rank(this):"OFF");
            statusChip.setTextColor(enabled?Color.rgb(190,120,255):getColor(R.color.muted));
        }
    }

    private void renderStats(){
        SharedPreferences p=MealEngine.prefs(this);
        boolean day=p.getBoolean(MealEngine.K_DAY,false);
        int meals=p.getInt(MealEngine.K_MEALS,0);
        int snacks=p.getInt(MealEngine.K_SNACKS,0);
        int water=HydrationEngine.total(this);
        int goal=HydrationEngine.goal(this);
        int streak=StreakEngine.days(this);
        String active=day?formatDuration(System.currentTimeMillis()-p.getLong(MealEngine.K_WAKE_TIME,System.currentTimeMillis())):"день не запущено";

        String foodNow=AppSettings.foodEnabled(this)
                ?meals+" / 3+ • перекуси "+snacks
                :"модуль вимкнено";
        String waterNow=AppSettings.waterEnabled(this)
                ?water+" / "+goal+" мл • "+(HydrationEngine.active(this)?HydrationEngine.statusLabel(this):"не запущено")
                :"модуль вимкнено";
        String streakNow=AppSettings.streakEnabled(this)
                ?streak+" "+daysWord(streak)+" • рекорд "+StreakEngine.best(this)
                :"модуль вимкнено";

        currentStatsText.setText(
                "Їжа: "+foodNow+"\n"+
                "Вода: "+waterNow+"\n"+
                "Серія: "+streakNow+"\n"+
                "Поточний wake-день: "+active
        );
        statsFoodProgress.setMax(3);
        statsFoodProgress.setProgress(Math.min(3,meals));
        statsWaterProgress.setMax(Math.max(1,goal));
        statsWaterProgress.setProgress(Math.min(goal,water));
        int nextMilestone=StreakEngine.nextMilestoneValue(this);
        statsStreakProgress.setMax(Math.max(1,nextMilestone));
        statsStreakProgress.setProgress(Math.min(streak,nextMilestone));

        historyStatsText.setText(DaySummaryStore.stats(this));
        foodHistoryText.setText(LogStore.recent(this,30));
    }

    private void renderSettings(){
        boolean notif=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        boolean exact=AlarmScheduler.canExact(this);

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        boolean battery=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());

        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        boolean full=Build.VERSION.SDK_INT<34||(nm!=null&&nm.canUseFullScreenIntent());
        boolean access=isAccessibilityEnabled();

        int score=(notif?1:0)+(exact?1:0)+(battery?1:0)+(full?1:0)+(access?1:0);

        reliabilityText.setText(
                "Сповіщення "+mark(notif)+"\n"+
                "Точні будильники "+mark(exact)+"\n"+
                "Full-screen alarm "+mark(full)+"\n"+
                "Батарея без обмежень "+mark(battery)+"\n"+
                "Штраф / блок соцмереж "+mark(access)+"\n"+
                "Автозапуск Xiaomi — перевіряється вручну\n\n"+
                "Надійність: "+score+"/5"
        );

        foodModuleButton.setText("ЇЖА: "+(AppSettings.foodEnabled(this)?"ON":"OFF"));
        waterModuleButton.setText("ВОДА: "+(AppSettings.waterEnabled(this)?"ON":"OFF"));
        streakModuleButton.setText("СЕРІЯ: "+(AppSettings.streakEnabled(this)?"ON":"OFF"));

        waterGoalSettingsButton.setText("ЦІЛЬ: "+HydrationEngine.goal(this)+" МЛ");
        waterQuickSettingsButton.setText("ШВИДКІ: "+HydrationEngine.quick(this,1)+" / "+HydrationEngine.quick(this,2)+" / "+HydrationEngine.quick(this,3));

        foodSoundButton.setText("ЗВУК ЇЖІ: "+(AppSettings.foodSound(this).isEmpty()?"ВБУДОВАНИЙ":"СВІЙ ФАЙЛ"));
        waterSoundButton.setText("ЗВУК ВОДИ: "+(AppSettings.waterSound(this).isEmpty()?"ВБУДОВАНИЙ":"СВІЙ ФАЙЛ"));
        memeModeButton.setText("RESET-АНІМАЦІЯ: "+(StreakEngine.meme(this)?"CINEMATIC «ЗРАДА»":"СПОКІЙНА"));

        int mask=ReminderTestManager.mask(this);
        int got=Integer.bitCount(mask);
        long rem=ReminderTestManager.nextRemaining(this);
        StringBuilder b=new StringBuilder();
        if(ReminderTestManager.running(this)){
            b.append("АКТИВНИЙ. Згорни додаток і просто чекай.\n");
            b.append("Наступна подія через ").append(formatDuration(rem)).append("\n");
        }else{
            b.append(got==7?"ЗАВЕРШЕНО.":"НЕ АКТИВНИЙ.").append("\n");
        }
        b.append("Отримано: ").append(got).append("/7");
        if(got>0)b.append(" • max delay ").append(Math.round(ReminderTestManager.maxDelay(this)/1000f)).append(" c");
        b.append("\n");
        for(int s=1;s<=7;s++){
            b.append((mask&(1<<(s-1)))!=0?"✓ ":"○ ")
                    .append(s).append(" ").append(ReminderTestManager.stageName(s));
            if(s<7)b.append("\n");
        }
        b.append("\n\nРЕАЛЬНА ПЕРЕВІРКА");
        b.append("\nForeground +30 ").append(mark(ReminderTestManager.service5(this)));
        b.append("\nFull-screen +30 ").append(mark(ReminderTestManager.fullScreen5(this)));
        b.append("\nAccessibility штраф ").append(mark(isAccessibilityEnabled()));
        b.append("\nForeground FINAL ").append(mark(ReminderTestManager.service7(this)));
        b.append("\nFull-screen FINAL ").append(mark(ReminderTestManager.fullScreen7(this)));
        testStatus.setText(b.toString());
        testProgress.setProgress(got);
    }

    private void toggleFoodModule(){
        SharedPreferences p=MealEngine.prefs(this);
        if(AppSettings.foodEnabled(this)&&p.getBoolean(MealEngine.K_EATING,false)){
            Toast.makeText(this,"Спочатку заверши поточний прийом їжі.",Toast.LENGTH_SHORT).show();
            return;
        }
        boolean next=!AppSettings.foodEnabled(this);
        AppSettings.setFoodEnabled(this,next);
        if(!next){
            AlarmScheduler.cancelAll(this);
            AlarmService.stop(this);
            PenaltyManager.setBlocked(this,false);
        }else{
            AlarmScheduler.scheduleCurrent(this);
        }
        renderAll();
    }

    private void toggleWaterModule(){
        boolean next=!AppSettings.waterEnabled(this);
        AppSettings.setWaterEnabled(this,next);
        if(!next){
            WaterScheduler.cancel(this);
        }else if(MealEngine.prefs(this).getBoolean(MealEngine.K_DAY,false)&&!HydrationEngine.active(this)){
            HydrationEngine.wake(this);
        }
        renderAll();
    }

    private void addWater(int amount){
        if(!AppSettings.waterEnabled(this)){
            Toast.makeText(this,"Модуль води вимкнено.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(!HydrationEngine.active(this)){
            Toast.makeText(this,"Спочатку запусти wake-день.",Toast.LENGTH_SHORT).show();
            return;
        }
        HydrationEngine.add(this,amount);
        findViewById(R.id.root).performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        undoHideAt=System.currentTimeMillis()+15_000L;
        Toast.makeText(this,"+"+amount+" мл  •  можна скасувати",Toast.LENGTH_SHORT).show();
        renderAll();
    }

    private interface NumberCallback{void onValue(int v);}

    private void askNumber(String title,int initial,NumberCallback cb){
        EditText input=new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if(initial>0)input.setText(String.valueOf(initial));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Скасувати",null)
                .setPositiveButton("OK",(d,w)->{
                    try{
                        int v=Integer.parseInt(input.getText().toString().trim());
                        if(v>0)cb.onValue(v);
                    }catch(Exception e){
                        Toast.makeText(this,"Некоректне число",Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void editQuickButton(int idx){
        askNumber("Нове значення швидкої кнопки, мл",HydrationEngine.quick(this,idx),v->{
            HydrationEngine.setQuick(this,idx,v);
            Toast.makeText(this,"Кнопку оновлено.",Toast.LENGTH_SHORT).show();
            renderAll();
        });
    }

    private void editWaterHistory(){
        java.util.List<long[]> drinks=HydrationEngine.effectiveDrinks(this);
        if(drinks.isEmpty()){
            Toast.makeText(this,"Сьогодні ще немає записів води.",Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels=new String[drinks.size()];
        SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.getDefault());
        for(int i=0;i<drinks.size();i++){
            long[] d=drinks.get(i);
            labels[i]=fmt.format(new Date(d[0]))+"  •  +"+d[1]+" мл";
        }

        new AlertDialog.Builder(this)
                .setTitle("Видалити помилковий запис")
                .setItems(labels,(dialog,which)->{
                    long[] selected=drinks.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(labels[which])
                            .setMessage("Прибрати цей запис із сьогоднішньої води?")
                            .setNegativeButton("Ні",null)
                            .setPositiveButton("Видалити",(d,w)->{
                                boolean ok=HydrationEngine.deleteDrink(this,selected[0]);
                                Toast.makeText(this,ok?"Запис видалено.":"Не вдалося видалити.",Toast.LENGTH_SHORT).show();
                                renderAll();
                            }).show();
                })
                .show();
    }

    private void chooseQuickButton(){
        String[] items={
                "Кнопка 1: "+HydrationEngine.quick(this,1)+" мл",
                "Кнопка 2: "+HydrationEngine.quick(this,2)+" мл",
                "Кнопка 3: "+HydrationEngine.quick(this,3)+" мл"
        };
        new AlertDialog.Builder(this).setTitle("Яку кнопку змінити?").setItems(items,(d,which)->{
            int idx=which+1;
            askNumber("Нове значення, мл",HydrationEngine.quick(this,idx),v->{HydrationEngine.setQuick(this,idx,v);renderAll();});
        }).show();
    }

    private void resetStreak(){
        int lost=StreakEngine.days(this);
        StreakEngine.reset(this);
        if(StreakEngine.meme(this))showBetrayal(lost);
        else{
            Toast.makeText(this,"Серія почалась заново.",Toast.LENGTH_SHORT).show();
            renderAll();
        }
    }

    private void showBetrayal(int lostDays){
        final View root=findViewById(R.id.root);

        ColorMatrix matrix=new ColorMatrix();
        matrix.setSaturation(0.05f);
        Paint layerPaint=new Paint();
        layerPaint.setColorFilter(new ColorMatrixColorFilter(matrix));
        root.setLayerType(View.LAYER_TYPE_HARDWARE,layerPaint);
        root.animate().scaleX(1.035f).scaleY(1.035f).alpha(0.50f).setDuration(650).start();

        final Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(Color.argb(105,0,0,0));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        View line=new View(this);
        line.setBackgroundColor(Color.rgb(175,35,35));

        TextView title=new TextView(this);
        title.setText("ЗРАДА");
        title.setTextColor(Color.rgb(205,55,55));
        title.setTextSize(58);
        title.setLetterSpacing(0.12f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setAlpha(0f);
        title.setScaleX(1.28f);
        title.setScaleY(1.28f);

        TextView sub=new TextView(this);
        sub.setText("СЕРІЮ ВТРАЧЕНО  •  "+lostDays+" "+daysWord(lostDays));
        sub.setTextColor(Color.rgb(220,220,220));
        sub.setTextSize(14);
        sub.setLetterSpacing(0.08f);
        sub.setGravity(Gravity.CENTER);
        sub.setAlpha(0f);

        box.addView(line,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,2));
        box.addView(title,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,120));
        box.addView(sub,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,50));

        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,190);
        bp.gravity=Gravity.CENTER;
        bp.leftMargin=28;
        bp.rightMargin=28;
        frame.addView(box,bp);

        dialog.setContentView(frame);
        Window w=dialog.getWindow();
        if(w!=null)w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(false);
        dialog.show();

        title.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start();
        sub.animate().alpha(1f).setStartDelay(350).setDuration(500).start();

        handler.postDelayed(()->{
            if(dialog.isShowing())dialog.dismiss();
            root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).withEndAction(()->root.setLayerType(View.LAYER_TYPE_NONE,null)).start();
            renderAll();
        },2600L);
    }

    private void beginFoodPhoto(String action){
        if(!MealEngine.prefs(this).getBoolean(MealEngine.K_DAY,false)){
            Toast.makeText(this,"Спочатку запусти wake-день.",Toast.LENGTH_SHORT).show();
            return;
        }
        pendingFoodAction=action;

        ContentValues values=new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,"Food_"+System.currentTimeMillis()+".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT>=29)values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Desyatyi");

        pendingPhotoUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(pendingPhotoUri==null){
            Toast.makeText(this,"Не вдалося створити фото.",Toast.LENGTH_SHORT).show();
            return;
        }

        Intent cam=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cam.putExtra(MediaStore.EXTRA_OUTPUT,pendingPhotoUri);
        if(cam.resolveActivity(getPackageManager())!=null){
            startActivityForResult(cam,REQ_CAMERA);
        }else{
            getContentResolver().delete(pendingPhotoUri,null,null);
            pendingPhotoUri=null;
            Toast.makeText(this,"Камеру не знайдено.",Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseSound(boolean food){
        String current=food?AppSettings.foodSound(this):AppSettings.waterSound(this);
        String[] items=current.isEmpty()
                ? new String[]{"Вибрати свій аудіофайл"}
                : new String[]{"Вибрати інший аудіофайл","Повернути вбудований звук"};
        new AlertDialog.Builder(this).setTitle(food?"Звук їжі":"Звук води").setItems(items,(d,which)->{
            if(!current.isEmpty()&&which==1){
                if(food)AppSettings.setFoodSound(this,"");
                else AppSettings.setWaterSound(this,"");
                NotificationHelper.ensureChannels(this);
                renderAll();
            }else{
                pickSound(food?REQ_FOOD_SOUND:REQ_WATER_SOUND);
            }
        }).show();
    }

    private void pickSound(int req){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,req);
    }

    private void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1001);
        }
    }

    private void openNotificationSettings(){
        Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
        startActivity(i);
    }

    private void openExactAlarmSettings(){
        try{
            if(Build.VERSION.SDK_INT>=31){
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));
            }else{
                Toast.makeText(this,"Окремий дозвіл не потрібен.",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){openAppDetails();}
    }

    private void openFullScreenSettings(){
        if(Build.VERSION.SDK_INT<34){
            Toast.makeText(this,"На цій версії Android окремий дозвіл не потрібен.",Toast.LENGTH_SHORT).show();
            return;
        }
        try{
            startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",Uri.parse("package:"+getPackageName())));
        }catch(Exception e){openAppDetails();}
    }

    private void openBatterySettings(){
        try{
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));
        }catch(Exception e){
            try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
            catch(Exception ignored){openAppDetails();}
        }
    }

    private void openAutostartSettings(){
        try{
            Intent i=new Intent();
            i.setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(i);
        }catch(Exception e){openAppDetails();}
    }

    private void openAppDetails(){
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
    }

    private boolean isAccessibilityEnabled(){
        try{
            String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(enabled==null)return false;
            String a=new ComponentName(this,SocialBlockService.class).flattenToString();
            String b=new ComponentName(this,SocialBlockService.class).flattenToShortString();
            return enabled.contains(a)||enabled.contains(b)||enabled.contains(SocialBlockService.class.getName());
        }catch(Exception e){return false;}
    }

    private void exportAllData(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,"desyatyi_data.zip");
        startActivityForResult(i,REQ_DATA_EXPORT);
    }

    private void exportDiagnostics(){
        DiagnosticStore.log(this,"DIAGNOSTIC_EXPORT_REQUEST","user");
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE,"desyatyi_debug.log");
        startActivityForResult(i,REQ_DEBUG_EXPORT);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);

        if(req==REQ_CAMERA){
            if(result==RESULT_OK&&pendingPhotoUri!=null){
                MealEngine.savePhoto(this,pendingPhotoUri.toString());
                if("SNACK".equals(pendingFoodAction)){
                    boolean shifted=MealEngine.snack(this);
                    Toast.makeText(this,shifted?"Перекус записано, основну їжу посунуто.":"Перекус записано, але третій поспіль уже не переносить дедлайн.",Toast.LENGTH_LONG).show();
                }else{
                    MealEngine.startEating(this);
                    Toast.makeText(this,"Фото збережено. Починай їсти.",Toast.LENGTH_SHORT).show();
                }
            }else if(pendingPhotoUri!=null){
                getContentResolver().delete(pendingPhotoUri,null,null);
            }
            pendingPhotoUri=null;
            pendingFoodAction=null;
            renderAll();
            return;
        }

        if((req==REQ_FOOD_SOUND||req==REQ_WATER_SOUND)&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{
                int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri,flags);
            }catch(Exception ignored){}
            if(req==REQ_FOOD_SOUND)AppSettings.setFoodSound(this,uri.toString());
            else AppSettings.setWaterSound(this,uri.toString());
            NotificationHelper.ensureChannels(this);
            Toast.makeText(this,"Звук збережено.",Toast.LENGTH_SHORT).show();
            renderAll();
            return;
        }

        if(req==REQ_DEBUG_EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            boolean ok=DiagnosticStore.copyTo(this,data.getData());
            Toast.makeText(this,ok?"Лог експортовано":"Логів ще немає",Toast.LENGTH_SHORT).show();
            return;
        }

        if(req==REQ_DATA_EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            boolean ok=ExportStore.exportAll(this,data.getData());
            Toast.makeText(this,ok?"Дані експортовано":"Не вдалося експортувати",Toast.LENGTH_SHORT).show();
        }
    }

    private String mark(boolean v){return v?"✓":"✕";}

    private String formatDuration(long ms){
        ms=Math.max(0,ms);
        long sec=ms/1000;
        long h=sec/3600;
        long m=(sec%3600)/60;
        long s=sec%60;
        if(h>0)return String.format(Locale.getDefault(),"%d год %02d хв",h,m);
        return String.format(Locale.getDefault(),"%02d:%02d",m,s);
    }

    private String clock(long ms){
        return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(ms));
    }

    private String daysWord(int d){
        int m10=d%10,m100=d%100;
        if(m10==1&&m100!=11)return "ДЕНЬ";
        if(m10>=2&&m10<=4&&(m100<12||m100>14))return "ДНІ";
        return "ДНІВ";
    }
}
