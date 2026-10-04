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
            int delivered=MealEngine.escalationStage(c);

            if(now>=dead){
                // FINAL is intentionally re-asserted by the watchdog so a killed
                // foreground service cannot silently remove the last escalation.
                MealEngine.markEscalationStage(c,AlarmScheduler.FINAL);
                NotificationHelper.clearReminder(c);
                startAlarmService(c,AlarmService.MODE_FINAL);
            }else if(now>=pref+45*60_000L&&delivered<AlarmScheduler.LATE_45){
                MealEngine.markEscalationStage(c,AlarmScheduler.LATE_45);
                PenaltyManager.setBlocked(c,true);
                NotificationHelper.showReminder(c,AlarmScheduler.LATE_45);
            }else if(now>=pref+30*60_000L&&delivered<AlarmScheduler.LATE_30){
                MealEngine.markEscalationStage(c,AlarmScheduler.LATE_30);
                NotificationHelper.clearReminder(c);
                startAlarmService(c,AlarmService.MODE_CONFIRM);
            }else if(now>=pref+15*60_000L&&delivered<AlarmScheduler.LATE_15){
                MealEngine.markEscalationStage(c,AlarmScheduler.LATE_15);
                NotificationHelper.showReminder(c,AlarmScheduler.LATE_15);
            }else if(now>=pref&&delivered<AlarmScheduler.PREFERRED){
                MealEngine.markEscalationStage(c,AlarmScheduler.PREFERRED);
                NotificationHelper.showReminder(c,AlarmScheduler.PREFERRED);
            }
        }

        if(AppSettings.waterEnabled(c)&&HydrationEngine.reminderDue(c)){
            NotificationHelper.showWaterReminder(c,HydrationEngine.status(c));
        }

        WatchdogScheduler.schedule(c);
    }

    private void startAlarmService(Context c,String mode){
        Intent s=new Intent(c,AlarmService.class);
        s.putExtra("mode",mode);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);
        else c.startService(s);
    }
}
