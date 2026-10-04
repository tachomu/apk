package com.mealcoach.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        int stage=i.getIntExtra("stage",0);
        if(stage<1||stage>7)return;

        ReminderTestManager.mark(c,stage);

        if(stage==5||stage==7){
            Intent service=new Intent(c,AlarmService.class);
            service.putExtra("mode",stage==7?AlarmService.MODE_TEST_FINAL:AlarmService.MODE_TEST_CONFIRM);
            service.putExtra("test_stage",stage);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(service);
            else c.startService(service);
        }else{
            NotificationHelper.showTestStage(c,stage);
        }
    }
}
