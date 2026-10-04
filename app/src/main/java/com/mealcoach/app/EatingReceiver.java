package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class EatingReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        SharedPreferences p=MealEngine.prefs(c);
        if(!p.getBoolean(MealEngine.K_DAY,false)||!p.getBoolean(MealEngine.K_EATING,false))return;
        int type=i.getIntExtra("type",0);
        DiagnosticStore.log(c,"EATING_ALARM_FIRED","type="+type);
        if(type==EatingScheduler.CHECK){
            NotificationHelper.showEatingCheck(c);
        }else if(type==EatingScheduler.AUTO_FINISH){
            MealEngine.ate(c,true);
        }
    }
}
