package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class WatchdogScheduler {
    private static final int REQ=9901;
    private WatchdogScheduler(){}

    public static void schedule(Context c){
        cancel(c);
        if(!MealEngine.prefs(c).getBoolean(MealEngine.K_DAY,false))return;
        long at=System.currentTimeMillis()+12*60_000L;
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c);
        if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        DiagnosticStore.log(c,"WATCHDOG_SCHEDULED","at="+at);
    }

    public static void cancel(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null)am.cancel(pending(c));
    }

    private static PendingIntent pending(Context c){
        Intent i=new Intent(c,WatchdogReceiver.class);
        return PendingIntent.getBroadcast(c,REQ,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
