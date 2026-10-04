package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public final class ReminderTestManager {
    private static final String PREFS="reminder_test_v4";
    private static final String K_RUNNING="running",K_START="start",K_MASK="mask",K_LAST="last",K_FULLSCREEN5="fullscreen5",K_FULLSCREEN7="fullscreen7",K_SERVICE5="service5",K_SERVICE7="service7",K_FOOD_SOUND="food_sound",K_SOUND5="sound5",K_SOUND7="sound7";
    private static final int BASE=8400;
    private static final long[] OFFSETS={0,30_000L,75_000L,120_000L,180_000L,240_000L,300_000L,360_000L};

    private ReminderTestManager(){}

    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public static void start(Context c){
        stop(c);
        long now=System.currentTimeMillis();
        SharedPreferences.Editor e=prefs(c).edit().clear().putBoolean(K_RUNNING,true).putLong(K_START,now).putInt(K_MASK,0).putInt(K_LAST,0)
                .putBoolean(K_FULLSCREEN5,false).putBoolean(K_FULLSCREEN7,false).putBoolean(K_SERVICE5,false).putBoolean(K_SERVICE7,false)
                .putBoolean(K_FOOD_SOUND,false).putBoolean(K_SOUND5,false).putBoolean(K_SOUND7,false);
        for(int s=1;s<=7;s++)e.putLong("expected_"+s,now+OFFSETS[s]).putLong("delay_"+s,-1L);
        e.apply();
        for(int s=1;s<=7;s++)schedule(c,s,now+OFFSETS[s]);
        DiagnosticStore.log(c,"TEST_START","7 stages over 6 minutes");
    }

    public static void stop(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)for(int s=1;s<=7;s++)am.cancel(pending(c,s));
        prefs(c).edit().putBoolean(K_RUNNING,false).apply();
    }

    public static boolean running(Context c){return prefs(c).getBoolean(K_RUNNING,false);}
    public static long startTime(Context c){return prefs(c).getLong(K_START,0L);}
    public static int mask(Context c){return prefs(c).getInt(K_MASK,0);}
    public static int lastStage(Context c){return prefs(c).getInt(K_LAST,0);}

    public static void mark(Context c,int stage){
        if(stage<1||stage>7)return;
        SharedPreferences p=prefs(c);
        int mask=p.getInt(K_MASK,0)|(1<<(stage-1));
        long expected=p.getLong("expected_"+stage,System.currentTimeMillis());
        long delay=Math.max(0L,System.currentTimeMillis()-expected);
        SharedPreferences.Editor e=p.edit().putInt(K_MASK,mask).putInt(K_LAST,Math.max(stage,p.getInt(K_LAST,0))).putLong("delay_"+stage,delay);
        if(stage>=7)e.putBoolean(K_RUNNING,false);
        e.apply();
        DiagnosticStore.log(c,"TEST_STAGE_RECEIVED","stage="+stage+" delay_ms="+delay);
    }

    public static void nextNow(Context c){
        int next=1;
        int m=mask(c);
        while(next<=7&&(m&(1<<(next-1)))!=0)next++;
        if(next>7)return;
        cancelStage(c,next);
        long now=System.currentTimeMillis();
        prefs(c).edit().putLong("expected_"+next,now).apply();
        Intent fire=new Intent(c,ReminderTestReceiver.class);
        fire.putExtra("stage",next);
        c.sendBroadcast(fire);
    }

    public static long nextRemaining(Context c){
        if(!running(c))return 0L;
        int m=mask(c);
        for(int s=1;s<=7;s++){
            if((m&(1<<(s-1)))==0){
                return Math.max(0L,prefs(c).getLong("expected_"+s,System.currentTimeMillis())-System.currentTimeMillis());
            }
        }
        return 0L;
    }

    public static void markFullScreenSeen(Context c,int stage){
        SharedPreferences.Editor e=prefs(c).edit();
        if(stage==5)e.putBoolean(K_FULLSCREEN5,true);
        if(stage==7)e.putBoolean(K_FULLSCREEN7,true);
        e.apply();
        DiagnosticStore.log(c,"TEST_FULLSCREEN_SEEN","stage="+stage);
    }

    public static void markServiceStarted(Context c,int stage){
        SharedPreferences.Editor e=prefs(c).edit();
        if(stage==5)e.putBoolean(K_SERVICE5,true);
        if(stage==7)e.putBoolean(K_SERVICE7,true);
        e.apply();
        DiagnosticStore.log(c,"TEST_SERVICE_STARTED","stage="+stage);
    }

    public static boolean fullScreen5(Context c){return prefs(c).getBoolean(K_FULLSCREEN5,false);}
    public static boolean fullScreen7(Context c){return prefs(c).getBoolean(K_FULLSCREEN7,false);}
    public static boolean service5(Context c){return prefs(c).getBoolean(K_SERVICE5,false);}
    public static boolean service7(Context c){return prefs(c).getBoolean(K_SERVICE7,false);}

    public static void markFoodSoundPath(Context c){
        prefs(c).edit().putBoolean(K_FOOD_SOUND,true).apply();
        DiagnosticStore.log(c,"TEST_FOOD_SOUND_PATH","notification sent");
    }

    public static void markAlarmSoundStarted(Context c,int stage){
        SharedPreferences.Editor e=prefs(c).edit();
        if(stage==5)e.putBoolean(K_SOUND5,true);
        if(stage==7)e.putBoolean(K_SOUND7,true);
        e.apply();
        DiagnosticStore.log(c,"TEST_ALARM_SOUND_STARTED","stage="+stage);
    }

    public static boolean foodSoundPath(Context c){return prefs(c).getBoolean(K_FOOD_SOUND,false);}
    public static boolean sound5(Context c){return prefs(c).getBoolean(K_SOUND5,false);}
    public static boolean sound7(Context c){return prefs(c).getBoolean(K_SOUND7,false);}

    public static long maxDelay(Context c){
        long max=0L;
        SharedPreferences p=prefs(c);
        for(int s=1;s<=7;s++)max=Math.max(max,p.getLong("delay_"+s,-1L));
        return max;
    }

    public static String stageName(int s){
        String[] n={"","−60","−20","пора","+15","+30 fullscreen","+45 блок","FINAL"};
        return n[Math.max(1,Math.min(7,s))];
    }

    private static void schedule(Context c,int stage,long at){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c,stage);
        if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        DiagnosticStore.log(c,"TEST_STAGE_SCHEDULED","stage="+stage+" at="+at);
    }

    private static void cancelStage(Context c,int stage){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)am.cancel(pending(c,stage));
    }

    private static PendingIntent pending(Context c,int stage){
        Intent i=new Intent(c,ReminderTestReceiver.class);i.putExtra("stage",stage);
        return PendingIntent.getBroadcast(c,BASE+stage,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
