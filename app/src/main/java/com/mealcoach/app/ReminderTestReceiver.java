package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        int stage=i.getIntExtra("stage",0);
        if(stage<1||stage>7)return;
        ReminderTestManager.mark(c,stage);
        NotificationHelper.showTestStage(c,stage);
    }
}
