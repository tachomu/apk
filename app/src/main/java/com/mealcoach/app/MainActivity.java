package com.mealcoach.app;

import android.Manifest;
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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
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
import java.util.Random;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA=42;
    private static final int REQ_DEBUG_EXPORT=44;

    private TextView screenTitle,statusChip,cycleLabel,countdown,deadlineText,mealDotsText,mealsText,snacksText;
    private TextView flowTitle,flowSteps,lastEventText,waterStateText,waterTotalText,waterPaceText,waterWarningText,waterHistoryText;
    private TextView streakRankText,streakDaysText,streakSceneText,streakStatsText,streakMarksText,reliabilityText,testStatus;
    private ProgressBar cycleProgress,waterProgress;
    private Button primaryButton,snackButton,delayButton,extendEatingButton,sleepButton;
    private Button waterQuick1,waterQuick2,waterQuick3,waterCustomButton,waterUndoButton;
    private Button streakResetButton,settingsButton,closeSettingsButton,navFood,navWater,navStreak;
    private Button notificationSettingsButton,exactAlarmButton,batteryButton,autostartButton,waterGoalSettingsButton,waterQuickSettingsButton;
    private Button testButton,testNextButton,diagnosticExportButton,memeModeButton;
    private LinearLayout flowHint,secondaryActions,bottomNav;
    private ScrollView foodPage,waterPage,streakPage,settingsPage;

    private final Handler handler=new Handler();
    private Uri pendingPhotoUri;
    private String pendingFoodAction;
    private int currentTab=0;
    private boolean settingsOpen=false;
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
        if(HydrationEngine.active(this))WaterScheduler.scheduleNext(this,60*60_000L);
        showTab(getIntent().getIntExtra("open_tab",0));
        if(getIntent().getBooleanExtra("open_settings",false))openSettings();
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
        screenTitle=findViewById(R.id.screenTitle); statusChip=findViewById(R.id.statusChip);
        cycleLabel=findViewById(R.id.cycleLabel); countdown=findViewById(R.id.countdown); deadlineText=findViewById(R.id.deadlineText);
        mealDotsText=findViewById(R.id.mealDotsText); mealsText=findViewById(R.id.mealsText); snacksText=findViewById(R.id.snacksText);
        flowTitle=findViewById(R.id.flowTitle); flowSteps=findViewById(R.id.flowSteps); lastEventText=findViewById(R.id.lastEventText);
        waterStateText=findViewById(R.id.waterStateText); waterTotalText=findViewById(R.id.waterTotalText); waterPaceText=findViewById(R.id.waterPaceText);
        waterWarningText=findViewById(R.id.waterWarningText); waterHistoryText=findViewById(R.id.waterHistoryText);
        streakRankText=findViewById(R.id.streakRankText); streakDaysText=findViewById(R.id.streakDaysText); streakSceneText=findViewById(R.id.streakSceneText);
        streakStatsText=findViewById(R.id.streakStatsText); streakMarksText=findViewById(R.id.streakMarksText);
        reliabilityText=findViewById(R.id.reliabilityText); testStatus=findViewById(R.id.testStatus);
        cycleProgress=findViewById(R.id.cycleProgress); waterProgress=findViewById(R.id.waterProgress);

        primaryButton=findViewById(R.id.primaryButton); snackButton=findViewById(R.id.snackButton); delayButton=findViewById(R.id.delayButton);
        extendEatingButton=findViewById(R.id.extendEatingButton); sleepButton=findViewById(R.id.sleepButton);
        waterQuick1=findViewById(R.id.waterQuick1); waterQuick2=findViewById(R.id.waterQuick2); waterQuick3=findViewById(R.id.waterQuick3);
        waterCustomButton=findViewById(R.id.waterCustomButton); waterUndoButton=findViewById(R.id.waterUndoButton);
        streakResetButton=findViewById(R.id.streakResetButton); settingsButton=findViewById(R.id.settingsButton); closeSettingsButton=findViewById(R.id.closeSettingsButton);
        navFood=findViewById(R.id.navFood); navWater=findViewById(R.id.navWater); navStreak=findViewById(R.id.navStreak);
        notificationSettingsButton=findViewById(R.id.notificationSettingsButton); exactAlarmButton=findViewById(R.id.exactAlarmButton);
        batteryButton=findViewById(R.id.batteryButton); autostartButton=findViewById(R.id.autostartButton);
        waterGoalSettingsButton=findViewById(R.id.waterGoalSettingsButton); waterQuickSettingsButton=findViewById(R.id.waterQuickSettingsButton);
        testButton=findViewById(R.id.testButton); testNextButton=findViewById(R.id.testNextButton); diagnosticExportButton=findViewById(R.id.diagnosticExportButton);
        memeModeButton=findViewById(R.id.memeModeButton);

        flowHint=findViewById(R.id.flowHint); secondaryActions=findViewById(R.id.secondaryActions); bottomNav=findViewById(R.id.bottomNav);
        foodPage=findViewById(R.id.foodPage); waterPage=findViewById(R.id.waterPage); streakPage=findViewById(R.id.streakPage); settingsPage=findViewById(R.id.settingsPage);
    }

    private void wire(){
        primaryButton.setOnClickListener(v->{
            SharedPreferences p=MealEngine.prefs(this);
            if(!p.getBoolean(MealEngine.K_DAY,false)){MealEngine.wake(this);renderAll();}
            else if(p.getBoolean(MealEngine.K_EATING,false)){MealEngine.ate(this);Toast.makeText(this,"Прийом завершено.",Toast.LENGTH_SHORT).show();renderAll();}
            else beginFoodPhoto("MEAL");
        });
        snackButton.setOnClickListener(v->beginFoodPhoto("SNACK"));
        delayButton.setOnClickListener(v->{boolean ok=MealEngine.delay15(this);Toast.makeText(this,ok?"+15 хв":"Зараз недоступно або ліміт вичерпано",Toast.LENGTH_SHORT).show();renderAll();});
        extendEatingButton.setOnClickListener(v->{boolean ok=MealEngine.extendEating(this);Toast.makeText(this,ok?"Продовжено на 10 хв":"Більше продовжувати не можна",Toast.LENGTH_SHORT).show();renderAll();});
        sleepButton.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Завершити день?")
                .setMessage("Їжа і вода зупиняться до наступного пробудження.")
                .setNegativeButton("Скасувати",null).setPositiveButton("Лягаю спати",(d,w)->{MealEngine.sleep(this);renderAll();}).show());

        waterQuick1.setOnClickListener(v->addWater(HydrationEngine.quick(this,1)));
        waterQuick2.setOnClickListener(v->addWater(HydrationEngine.quick(this,2)));
        waterQuick3.setOnClickListener(v->addWater(HydrationEngine.quick(this,3)));
        waterCustomButton.setOnClickListener(v->askNumber("Скільки мл випив?",0,value->addWater(value)));
        waterUndoButton.setOnClickListener(v->{if(HydrationEngine.undo(this)){Toast.makeText(this,"Останній запис скасовано",Toast.LENGTH_SHORT).show();undoHideAt=0;renderAll();}});

        streakResetButton.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Точно записати?")
                .setMessage("Поточна серія почнеться заново. Історія та рекорд залишаться.")
                .setNegativeButton("Ні",null).setPositiveButton("Так, подрочив",(d,w)->resetStreak()).show());

        navFood.setOnClickListener(v->showTab(0)); navWater.setOnClickListener(v->showTab(1)); navStreak.setOnClickListener(v->showTab(2));
        settingsButton.setOnClickListener(v->{if(settingsOpen)closeSettings();else openSettings();});
        closeSettingsButton.setOnClickListener(v->closeSettings());

        notificationSettingsButton.setOnClickListener(v->openNotificationSettings());
        exactAlarmButton.setOnClickListener(v->openExactAlarmSettings());
        batteryButton.setOnClickListener(v->openBatterySettings());
        autostartButton.setOnClickListener(v->openAutostartSettings());

        waterGoalSettingsButton.setOnClickListener(v->askNumber("Денна ціль води, мл",HydrationEngine.goal(this),value->{HydrationEngine.setGoal(this,value);renderAll();}));
        waterQuickSettingsButton.setOnClickListener(v->chooseQuickButton());
        memeModeButton.setOnClickListener(v->{StreakEngine.toggleMeme(this);renderAll();});

        testButton.setOnClickListener(v->{ReminderTestManager.start(this);Toast.makeText(this,"Тест запущено. Можеш згорнути додаток на 6 хвилин.",Toast.LENGTH_LONG).show();renderAll();});
        testNextButton.setOnClickListener(v->{ReminderTestManager.nextNow(this);renderAll();});
        diagnosticExportButton.setOnClickListener(v->exportDiagnostics());
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){touchDownX=e.getX();touchDownY=e.getY();}
        else if(e.getAction()==MotionEvent.ACTION_UP && !settingsOpen){
            float dx=e.getX()-touchDownX,dy=e.getY()-touchDownY;
            if(Math.abs(dx)>130 && Math.abs(dx)>Math.abs(dy)*1.35f){
                showTab(dx<0?(currentTab+1)%3:(currentTab+2)%3);
            }
        }
        return super.dispatchTouchEvent(e);
    }

    private void showTab(int tab){
        currentTab=((tab%3)+3)%3; settingsOpen=false;
        foodPage.setVisibility(currentTab==0?View.VISIBLE:View.GONE);
        waterPage.setVisibility(currentTab==1?View.VISIBLE:View.GONE);
        streakPage.setVisibility(currentTab==2?View.VISIBLE:View.GONE);
        settingsPage.setVisibility(View.GONE); bottomNav.setVisibility(View.VISIBLE);
        navFood.setTextColor(getColor(currentTab==0?R.color.accent:R.color.muted));
        navWater.setTextColor(currentTab==1?Color.rgb(90,169,255):getColor(R.color.muted));
        navStreak.setTextColor(currentTab==2?Color.rgb(190,120,255):getColor(R.color.muted));
        screenTitle.setText(currentTab==0?"ЇЖА":currentTab==1?"ВОДА":"СЕРІЯ");
        renderAll();
    }

    private void openSettings(){
        settingsOpen=true;
        foodPage.setVisibility(View.GONE);waterPage.setVisibility(View.GONE);streakPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.VISIBLE);bottomNav.setVisibility(View.GONE);
        screenTitle.setText("НАЛАШТУВАННЯ");statusChip.setText("⚙");
        renderSettings();
    }

    private void closeSettings(){showTab(currentTab);}

    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);setIntent(i);
        if(i.getBooleanExtra("open_settings",false))openSettings();
        else if(i.hasExtra("open_tab"))showTab(i.getIntExtra("open_tab",0));
        handler.postDelayed(()->consumeForceCamera(i),200L);
    }

    private void consumeForceCamera(Intent i){
        if(i!=null&&i.getBooleanExtra("force_camera",false)){
            i.removeExtra("force_camera");showTab(0);
            if(MealEngine.prefs(this).getBoolean(MealEngine.K_DAY,false)&&!MealEngine.prefs(this).getBoolean(MealEngine.K_EATING,false))beginFoodPhoto("MEAL");
        }
    }

    @Override protected void onResume(){super.onResume();renderAll();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(ticker);}

    private void renderAll(){
        renderFood();renderWater();renderStreak();
        if(settingsOpen)renderSettings();
    }

    private void renderFood(){
        SharedPreferences p=MealEngine.prefs(this);
        boolean day=p.getBoolean(MealEngine.K_DAY,false);
        int meals=p.getInt(MealEngine.K_MEALS,0),snacks=p.getInt(MealEngine.K_SNACKS,0);
        mealDotsText.setText((meals>=1?"●":"○")+"  "+(meals>=2?"●":"○")+"  "+(meals>=3?"●":"○"));
        mealsText.setText(meals+" / 3+ прийомів"+(meals>=3?"  ✓":""));snacksText.setText("ПЕРЕКУСИ "+snacks);
        lastEventText.setText(LogStore.recent(this,1));
        if(!day){
            if(!settingsOpen&&currentTab==0){statusChip.setText("НЕ ЗАПУЩЕНО");statusChip.setTextColor(getColor(R.color.muted));}
            cycleLabel.setText("ДЕНЬ ЩЕ НЕ ЗАПУЩЕНО");countdown.setText("ПОЧАТИ ДЕНЬ");countdown.setTextColor(getColor(R.color.text));
            deadlineText.setText("Пробудження запускає їжу і воду");cycleProgress.setProgress(0);flowHint.setVisibility(View.GONE);
            secondaryActions.setVisibility(View.GONE);extendEatingButton.setVisibility(View.GONE);sleepButton.setVisibility(View.GONE);primaryButton.setText("Я ПРОКИНУВСЯ");return;
        }
        sleepButton.setVisibility(View.VISIBLE);
        boolean eating=p.getBoolean(MealEngine.K_EATING,false);
        long now=System.currentTimeMillis(),start=p.getLong(MealEngine.K_START,now),pref=p.getLong(MealEngine.K_PREF,start),dead=p.getLong(MealEngine.K_DEADLINE,pref);
        cycleLabel.setText(p.getBoolean(MealEngine.K_FIRST,false)?"ПЕРША ЇЖА":"НАСТУПНА ЇЖА");
        if(eating){
            long es=p.getLong(MealEngine.K_EATING_START,now);int ext=p.getInt(MealEngine.K_EATING_EXT,0);
            if(!settingsOpen&&currentTab==0){statusChip.setText("ЇМ ЗАРАЗ");statusChip.setTextColor(getColor(R.color.accent));}
            countdown.setText(formatDuration(now-es));countdown.setTextColor(getColor(R.color.accent));deadlineText.setText("Автозавершення через "+formatDuration(Math.max(0,es+(30L+10L*ext)*60_000L-now)));
            cycleProgress.setProgress(1000);flowHint.setVisibility(View.VISIBLE);flowTitle.setText("ЗАРАЗ ТИ ЇСИ");
            flowSteps.setText("Якщо забудеш завершити — через 20 хв буде питання, через 30 хв прийом закриється автоматично.");
            primaryButton.setText("ЗАКІНЧИВ ЇСТИ");secondaryActions.setVisibility(View.GONE);extendEatingButton.setVisibility(ext<2?View.VISIBLE:View.GONE);return;
        }
        flowHint.setVisibility(View.VISIBLE);flowTitle.setText("КОЛИ СІДАЄШ ЇСТИ");
        flowSteps.setText("1  Сфотографуй їжу\n2  Після фото починай їсти\n3  Додаток сам підстрахує, якщо забудеш завершити");
        primaryButton.setText("СФОТОГРАФУВАТИ ЇЖУ → ПОЧАТИ");secondaryActions.setVisibility(View.VISIBLE);extendEatingButton.setVisibility(View.GONE);
        delayButton.setVisibility(now>=pref?View.VISIBLE:View.GONE);
        int prog=(int)Math.max(0,Math.min(1000,((now-start)*1000L)/Math.max(1,dead-start)));cycleProgress.setProgress(prog);
        if(now<pref){if(!settingsOpen&&currentTab==0){statusChip.setText("ВСЕ ДОБРЕ");statusChip.setTextColor(getColor(R.color.accent));}countdown.setText(formatDuration(pref-now));countdown.setTextColor(getColor(R.color.text));deadlineText.setText("До бажаного часу • максимум "+clock(dead));}
        else if(now<dead){if(!settingsOpen&&currentTab==0){statusChip.setText("ВЖЕ ПОРА");statusChip.setTextColor(getColor(R.color.warn));}countdown.setText(formatDuration(dead-now));countdown.setTextColor(getColor(R.color.warn));deadlineText.setText("До максимальної межі • "+clock(dead));}
        else{if(!settingsOpen&&currentTab==0){statusChip.setText("ПРОСТРОЧЕНО");statusChip.setTextColor(getColor(R.color.danger));}countdown.setText("+"+formatDuration(now-dead));countdown.setTextColor(getColor(R.color.danger));deadlineText.setText("Максимальна межа пройдена");}
    }

    private void renderWater(){
        boolean active=HydrationEngine.active(this);int total=HydrationEngine.total(this),goal=HydrationEngine.goal(this);
        waterProgress.setMax(goal);waterProgress.setProgress(Math.min(goal,total));
        waterTotalText.setText(total+" / "+goal+" мл");
        waterQuick1.setText("+"+HydrationEngine.quick(this,1));waterQuick2.setText("+"+HydrationEngine.quick(this,2));waterQuick3.setText("+"+HydrationEngine.quick(this,3));
        waterHistoryText.setText(HydrationEngine.recent(this,5));
        waterUndoButton.setVisibility(System.currentTimeMillis()<undoHideAt?View.VISIBLE:View.GONE);
        if(!active){
            waterStateText.setText("ДЕНЬ НЕ ЗАПУЩЕНО");waterStateText.setTextColor(getColor(R.color.muted));
            waterPaceText.setText("Натисни «Я прокинувся» на вкладці їжі — графік води стартує від цього часу.");
            waterWarningText.setText("Ціль за замовчуванням: "+goal+" мл.");if(!settingsOpen&&currentTab==1){statusChip.setText("НЕ ЗАПУЩЕНО");statusChip.setTextColor(getColor(R.color.muted));}return;
        }
        int st=HydrationEngine.status(this),expected=HydrationEngine.expectedNow(this),projected=HydrationEngine.projectedTotal(this);
        int color=st==HydrationEngine.GREEN?Color.rgb(90,169,255):st==HydrationEngine.YELLOW?getColor(R.color.warn):st==HydrationEngine.ORANGE?Color.rgb(255,159,67):getColor(R.color.danger);
        waterStateText.setText(HydrationEngine.statusLabel(this));waterStateText.setTextColor(color);waterWarningText.setText(HydrationEngine.warning(this));
        waterPaceText.setText("На цей момент орієнтир ≈ "+expected+" мл • прогноз за темпом ≈ "+projected+" мл");
        if(!settingsOpen&&currentTab==1){statusChip.setText(HydrationEngine.statusLabel(this));statusChip.setTextColor(color);}
    }

    private void renderStreak(){
        StreakEngine.ensure(this);int d=StreakEngine.days(this);
        streakDaysText.setText(d+" "+daysWord(d));streakRankText.setText(StreakEngine.rank(this));streakSceneText.setText(StreakEngine.scene(this));
        streakStatsText.setText("Рекорд "+StreakEngine.best(this)+" • чистих днів "+StreakEngine.cleanDaysTracked(this)+"/"+StreakEngine.trackedWindow(this));
        streakMarksText.setText(StreakEngine.dayMarks(this,10));
        if(!settingsOpen&&currentTab==2){statusChip.setText(StreakEngine.rank(this));statusChip.setTextColor(Color.rgb(190,120,255));}
    }

    private void renderSettings(){
        boolean notif=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);boolean exact=Build.VERSION.SDK_INT<31||(am!=null&&am.canScheduleExactAlarms());
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);boolean battery=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());
        int score=(notif?1:0)+(exact?1:0)+(battery?1:0);
        reliabilityText.setText("Сповіщення "+(notif?"✓":"✕")+"\nТочні будильники "+(exact?"✓":"✕")+"\nБатарея без обмежень "+(battery?"✓":"✕")+"\nАвтозапуск Xiaomi — перевір вручну\n\nНадійність: "+score+"/3");
        waterGoalSettingsButton.setText("ЦІЛЬ: "+HydrationEngine.goal(this)+" МЛ");
        waterQuickSettingsButton.setText("ШВИДКІ: "+HydrationEngine.quick(this,1)+" / "+HydrationEngine.quick(this,2)+" / "+HydrationEngine.quick(this,3));
        memeModeButton.setText("RESET-АНІМАЦІЯ: "+(StreakEngine.meme(this)?"МЕМНА":"СПОКІЙНА"));
        int mask=ReminderTestManager.mask(this),got=Integer.bitCount(mask);long rem=ReminderTestManager.nextRemaining(this);
        StringBuilder b=new StringBuilder();
        b.append(ReminderTestManager.running(this)?"ТЕСТ АКТИВНИЙ":"ТЕСТ НЕ АКТИВНИЙ").append("\n");
        b.append("Отримано: ").append(got).append("/6");
        if(ReminderTestManager.running(this))b.append(" • наступний через ").append(formatDuration(rem));
        b.append("\n");
        for(int i=1;i<=6;i++)b.append((mask&(1<<(i-1)))!=0?"✓ ":"○ ").append("Етап ").append(i).append(i<6?"   ":"");
        testStatus.setText(b.toString());
    }

    private void addWater(int amount){
        if(!HydrationEngine.active(this)){Toast.makeText(this,"Спочатку запусти день через «Я прокинувся».",Toast.LENGTH_SHORT).show();return;}
        HydrationEngine.add(this,amount);undoHideAt=System.currentTimeMillis()+15_000L;Toast.makeText(this,"+"+amount+" мл",Toast.LENGTH_SHORT).show();renderAll();
    }

    private interface NumberCallback{void onValue(int v);}
    private void askNumber(String title,int initial,NumberCallback cb){
        EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);if(initial>0)input.setText(String.valueOf(initial));input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Скасувати",null).setPositiveButton("OK",(d,w)->{
            try{int v=Integer.parseInt(input.getText().toString().trim());if(v>0)cb.onValue(v);}catch(Exception e){Toast.makeText(this,"Некоректне число",Toast.LENGTH_SHORT).show();}
        }).show();
    }

    private void chooseQuickButton(){
        String[] items={"Кнопка 1: "+HydrationEngine.quick(this,1)+" мл","Кнопка 2: "+HydrationEngine.quick(this,2)+" мл","Кнопка 3: "+HydrationEngine.quick(this,3)+" мл"};
        new AlertDialog.Builder(this).setTitle("Яку кнопку змінити?").setItems(items,(d,which)->{
            int idx=which+1;askNumber("Нове значення, мл",HydrationEngine.quick(this,idx),v->{HydrationEngine.setQuick(this,idx,v);renderAll();});
        }).show();
    }

    private void resetStreak(){
        StreakEngine.reset(this);
        if(StreakEngine.meme(this))showBetrayal();
        else{Toast.makeText(this,"Серія почалась заново.",Toast.LENGTH_SHORT).show();renderAll();}
    }

    private void showBetrayal(){
        final Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.argb(225,5,7,10));
        TextView title=new TextView(this);title.setText("ЗРАДА");title.setTextColor(getColor(R.color.danger));title.setTextSize(48);title.setGravity(Gravity.CENTER);title.setTypeface(null,android.graphics.Typeface.BOLD);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,220);tp.gravity=Gravity.CENTER;frame.addView(title,tp);
        Random rnd=new Random();
        for(int i=0;i<22;i++){
            TextView e=new TextView(this);e.setText("😢");e.setTextSize(22+rnd.nextInt(18));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(80,80);p.leftMargin=rnd.nextInt(Math.max(1,getResources().getDisplayMetrics().widthPixels-80));p.topMargin=-100-rnd.nextInt(600);frame.addView(e,p);
            e.animate().translationY(getResources().getDisplayMetrics().heightPixels+900).rotationBy(rnd.nextInt(180)-90).setStartDelay(rnd.nextInt(700)).setDuration(1400+rnd.nextInt(1000)).start();
        }
        dialog.setContentView(frame);Window w=dialog.getWindow();if(w!=null)w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.show();
        handler.postDelayed(()->{if(dialog.isShowing())dialog.dismiss();renderAll();},2300L);
    }

    private void beginFoodPhoto(String action){
        if(!MealEngine.prefs(this).getBoolean(MealEngine.K_DAY,false))return;
        pendingFoodAction=action;
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"Food_"+System.currentTimeMillis()+".jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT>=29)values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/V_Sashyni_Trusiki");
        pendingPhotoUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(pendingPhotoUri==null){Toast.makeText(this,"Не вдалося створити фото.",Toast.LENGTH_SHORT).show();return;}
        Intent cam=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);cam.putExtra(MediaStore.EXTRA_OUTPUT,pendingPhotoUri);
        if(cam.resolveActivity(getPackageManager())!=null)startActivityForResult(cam,REQ_CAMERA);else{getContentResolver().delete(pendingPhotoUri,null,null);pendingPhotoUri=null;Toast.makeText(this,"Камеру не знайдено.",Toast.LENGTH_SHORT).show();}
    }

    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1001);}
    private void openNotificationSettings(){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}
    private void openExactAlarmSettings(){try{if(Build.VERSION.SDK_INT>=31)startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else Toast.makeText(this,"Окремий дозвіл не потрібен.",Toast.LENGTH_SHORT).show();}catch(Exception e){openAppDetails();}}
    private void openBatterySettings(){try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){openAppDetails();}}
    private void openAutostartSettings(){try{Intent i=new Intent();i.setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity"));startActivity(i);}catch(Exception e){openAppDetails();}}
    private void openAppDetails(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private void exportDiagnostics(){DiagnosticStore.log(this,"DIAGNOSTIC_EXPORT_REQUEST","user");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"v_sashyni_trusiki_debug.log");startActivityForResult(i,REQ_DEBUG_EXPORT);}

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==REQ_CAMERA){
            if(result==RESULT_OK&&pendingPhotoUri!=null){
                MealEngine.savePhoto(this,pendingPhotoUri.toString());
                if("SNACK".equals(pendingFoodAction)){boolean shifted=MealEngine.snack(this);Toast.makeText(this,shifted?"Перекус записано, основну їжу посунуто.":"Перекус записано, але третій поспіль уже не переносить дедлайн.",Toast.LENGTH_LONG).show();}
                else{MealEngine.startEating(this);Toast.makeText(this,"Фото збережено. Починай їсти.",Toast.LENGTH_SHORT).show();}
            }else if(pendingPhotoUri!=null)getContentResolver().delete(pendingPhotoUri,null,null);
            pendingPhotoUri=null;pendingFoodAction=null;renderAll();return;
        }
        if(req==REQ_DEBUG_EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            boolean ok=DiagnosticStore.copyTo(this,data.getData());Toast.makeText(this,ok?"Лог експортовано":"Логів ще немає",Toast.LENGTH_SHORT).show();
        }
    }

    private String formatDuration(long ms){ms=Math.max(0,ms);long sec=ms/1000,h=sec/3600,m=(sec%3600)/60,s=sec%60;if(h>0)return String.format(Locale.getDefault(),"%d год %02d хв",h,m);return String.format(Locale.getDefault(),"%02d:%02d",m,s);}
    private String clock(long ms){return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(ms));}
    private String daysWord(int d){int m10=d%10,m100=d%100;if(m10==1&&m100!=11)return "ДЕНЬ";if(m10>=2&&m10<=4&&(m100<12||m100>14))return "ДНІ";return "ДНІВ";}
}
