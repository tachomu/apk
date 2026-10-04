package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public final class ReminderTestManager {
    private static final String PREFS="reminder_test_v3";
    private static final String K_RUNNING="running";
    private static final String K_START="start";
    private static final String K_MASK="mask";
    private static final String K_LAST="last";
    private static final int BASE=8400;
    private static final long MIN=60_000L;

    private ReminderTestManager(){}

    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public static void start(Context c){
        stop(c);
        long now=System.currentTimeMillis();
        prefs(c).edit().putBoolean(K_RUNNING,true).putLong(K_START,now).putInt(K_MASK,0).putInt(K_LAST,0).apply();
        for(int s=1;s<=6;s++)schedule(c,s,now+s*MIN);
        DiagnosticStore.log(c,"TEST_START","background 6-minute test");
    }

    public static void stop(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)for(int s=1;s<=6;s++)am.cancel(pending(c,s));
        prefs(c).edit().putBoolean(K_RUNNING,false).apply();
    }

    public static boolean running(Context c){return prefs(c).getBoolean(K_RUNNING,false);}
    public static long startTime(Context c){return prefs(c).getLong(K_START,0L);}
    public static int mask(Context c){return prefs(c).getInt(K_MASK,0);}
    public static int lastStage(Context c){return prefs(c).getInt(K_LAST,0);}

    public static void mark(Context c,int stage){
        SharedPreferences p=prefs(c);
        int mask=p.getInt(K_MASK,0)|(1<<(stage-1));
        SharedPreferences.Editor e=p.edit().putInt(K_MASK,mask).putInt(K_LAST,stage);
        if(stage>=6)e.putBoolean(K_RUNNING,false);
        e.apply();
        DiagnosticStore.log(c,"TEST_STAGE_RECEIVED","stage="+stage);
    }

    public static void nextNow(Context c){
        int next=Math.min(6,lastStage(c)+1);
        if(next<=0)next=1;
        mark(c,next);
        NotificationHelper.showTestStage(c,next);
    }

    public static long nextRemaining(Context c){
        if(!running(c))return 0L;
        int next=Math.min(6,lastStage(c)+1);
        long target=startTime(c)+next*MIN;
        return Math.max(0L,target-System.currentTimeMillis());
    }

    private static void schedule(Context c,int stage,long at){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c,stage);
        if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
    }

    private static PendingIntent pending(Context c,int stage){
        Intent i=new Intent(c,ReminderTestReceiver.class);i.putExtra("stage",stage);
        return PendingIntent.getBroadcast(c,BASE+stage,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
