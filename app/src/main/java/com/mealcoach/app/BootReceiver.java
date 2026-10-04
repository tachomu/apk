package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent==null?"":intent.getAction();
        NotificationHelper.ensureChannels(context);
        AlarmScheduler.scheduleCurrent(context);
        WatchdogScheduler.schedule(context);
        if(HydrationEngine.active(context))WaterScheduler.scheduleNext(context,10*60_000L);

        SharedPreferences p=MealEngine.prefs(context);
        if(p.getBoolean(MealEngine.K_EATING,false)){
            EatingScheduler.schedule(context,p.getLong(MealEngine.K_EATING_START,System.currentTimeMillis()),p.getInt(MealEngine.K_EATING_EXT,0));
        }
        DiagnosticStore.log(context,"SCHEDULE_RESTORE","action="+action);
    }
}
