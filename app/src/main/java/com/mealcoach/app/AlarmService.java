package com.mealcoach.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

public class AlarmService extends Service {
    public static final String MODE_CONFIRM="confirm";
    public static final String MODE_FINAL="final";
    public static final String MODE_TEST_CONFIRM="test_confirm";
    public static final String MODE_TEST_FINAL="test_final";

    private static final String STATE_PREFS="alarm_service_v4";
    private static final String K_LAST_MODE="last_mode";

    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler=new Handler();
    private float volume=0.15f;
    private String mode=MODE_CONFIRM;
    private int testStage=0;

    private final Runnable ramp=new Runnable(){
        @Override public void run(){
            if(player==null||isFinalMode())return;
            volume=Math.min(1.0f,volume+0.12f);
            try{player.setVolume(volume,volume);}catch(Exception ignored){}
            if(volume<1.0f)handler.postDelayed(this,10_000L);
        }
    };

    private final Runnable stopTest=this::stopSelf;

    @Override public void onCreate(){
        super.onCreate();
        NotificationHelper.ensureChannels(this);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        boolean test=intent!=null&&isTestMode(intent.getStringExtra("mode"));

        if(test){
            mode=intent.getStringExtra("mode");
            testStage=intent.getIntExtra("test_stage",MODE_TEST_FINAL.equals(mode)?7:5);
        }else{
            SharedPreferences state=getSharedPreferences(STATE_PREFS,MODE_PRIVATE);
            String incoming=intent!=null?intent.getStringExtra("mode"):null;
            mode=incoming!=null?incoming:state.getString(K_LAST_MODE,MODE_CONFIRM);
            testStage=0;

            android.content.SharedPreferences p=MealEngine.prefs(this);
            if(!AppSettings.foodEnabled(this)||!p.getBoolean(MealEngine.K_DAY,false)||p.getBoolean(MealEngine.K_EATING,false)){
                stopSelf();
                return START_NOT_STICKY;
            }
            state.edit().putString(K_LAST_MODE,mode).apply();
        }

        startForeground(NotificationHelper.SERVICE_ID,
                NotificationHelper.buildEscalationNotification(this,mode,testStage));

        startSoundAndVibration();
        handler.removeCallbacks(stopTest);

        if(test){
            ReminderTestManager.markServiceStarted(this,testStage);
            handler.postDelayed(stopTest,7500L);
        }

        DiagnosticStore.log(this,"ALARM_SERVICE_START","mode="+mode+" test_stage="+testStage);
        return test?START_NOT_STICKY:START_REDELIVER_INTENT;
    }

    private boolean isTestMode(String m){
        return MODE_TEST_CONFIRM.equals(m)||MODE_TEST_FINAL.equals(m);
    }

    private boolean isFinalMode(){
        return MODE_FINAL.equals(mode)||MODE_TEST_FINAL.equals(mode);
    }

    private void startSoundAndVibration(){
        stopMediaOnly();

        Uri alarm=Settings.System.DEFAULT_ALARM_ALERT_URI;
        if(alarm==null)alarm=Settings.System.DEFAULT_NOTIFICATION_URI;

        try{
            player=new MediaPlayer();
            player.setDataSource(this,alarm);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setLooping(true);
            player.prepare();

            volume=isFinalMode()?1.0f:0.15f;
            player.setVolume(volume,volume);
            player.start();
            if(testStage==5||testStage==7)ReminderTestManager.markAlarmSoundStarted(this,testStage);

            handler.removeCallbacks(ramp);
            if(!isFinalMode())handler.postDelayed(ramp,10_000L);
        }catch(Exception e){
            stopMediaOnly();
            DiagnosticStore.log(this,"ALARM_SOUND_ERROR",e.toString());
        }

        vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        if(vibrator!=null&&vibrator.hasVibrator()){
            long[] pattern=isFinalMode()
                    ?new long[]{0,900,250,900,250}
                    :new long[]{0,450,350,450,900};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern,0));
        }
    }

    private void stopMediaOnly(){
        handler.removeCallbacks(ramp);
        if(player!=null){
            try{player.stop();}catch(Exception ignored){}
            player.release();
            player=null;
        }
    }

    @Override public void onDestroy(){
        handler.removeCallbacks(stopTest);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopMediaOnly();
        if(vibrator!=null)vibrator.cancel();
        DiagnosticStore.log(this,"ALARM_SERVICE_STOP","mode="+mode+" test_stage="+testStage);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}

    public static void stop(Context context){
        context.stopService(new Intent(context,AlarmService.class));
    }
}
