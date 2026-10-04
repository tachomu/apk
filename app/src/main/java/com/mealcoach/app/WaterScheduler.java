package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class WaterScheduler {
    private static final int REQ=6201;
    private WaterScheduler(){}

    public static void scheduleNext(Context c,long delayMs){
        cancel(c);
        if(!HydrationEngine.active(c))return;
        long at=System.currentTimeMillis()+Math.max(5*60_000L,delayMs);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()){
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        }else{
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        }
        DiagnosticStore.log(c,"WATER_ALARM_SCHEDULED","at="+at);
    }

    public static void cancel(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)am.cancel(pending(c));
    }

    private static PendingIntent pending(Context c){
        Intent i=new Intent(c,WaterReceiver.class);
        return PendingIntent.getBroadcast(c,REQ,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
