package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class WatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        SharedPreferences p=MealEngine.prefs(c);
        if(!p.getBoolean(MealEngine.K_DAY,false))return;
        long now=System.currentTimeMillis();
        DiagnosticStore.log(c,"WATCHDOG_FIRED","now="+now);

        if(AppSettings.foodEnabled(c)&&!p.getBoolean(MealEngine.K_EATING,false)){
            long pref=p.getLong(MealEngine.K_PREF,Long.MAX_VALUE);
            long dead=p.getLong(MealEngine.K_DEADLINE,Long.MAX_VALUE);
            if(now>=dead){
                Intent s=new Intent(c,AlarmService.class);s.putExtra("mode",AlarmService.MODE_FINAL);
                if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);
            }else if(now>=pref+45*60_000L){
                PenaltyManager.setBlocked(c,true);
                NotificationHelper.showReminder(c,AlarmScheduler.LATE_45);
            }else if(now>=pref+15*60_000L){
                NotificationHelper.showReminder(c,AlarmScheduler.LATE_15);
            }else if(now>=pref){
                NotificationHelper.showReminder(c,AlarmScheduler.PREFERRED);
            }
        }

        if(AppSettings.waterEnabled(c)&&HydrationEngine.active(c)&&HydrationEngine.status(c)!=HydrationEngine.GREEN){
            NotificationHelper.showWaterReminder(c,HydrationEngine.status(c));
        }

        WatchdogScheduler.schedule(c);
    }
}
