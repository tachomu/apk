package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.mealcoach.SIT".equals(action)) {
            MealEngine.startEating(context);
        } else if ("com.mealcoach.ATE".equals(action)) {
            MealEngine.ate(context);
        } else if ("com.mealcoach.DELAY".equals(action)) {
            MealEngine.delay15(context);
        }
    }
}
