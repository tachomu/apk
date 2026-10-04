package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WaterReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!HydrationEngine.active(c))return;
        int status=HydrationEngine.status(c);
        DiagnosticStore.log(c,"WATER_ALARM_FIRED","status="+status+" total="+HydrationEngine.total(c));
        if(status!=HydrationEngine.GREEN){
            NotificationHelper.showWaterReminder(c,status);
        }
        long next=status==HydrationEngine.RED?25*60_000L:status==HydrationEngine.ORANGE?45*60_000L:75*60_000L;
        WaterScheduler.scheduleNext(c,next);
    }
}
