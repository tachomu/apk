package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class EatingScheduler {
    public static final int CHECK=1;
    public static final int AUTO_FINISH=2;
    private static final int BASE=7300;

    private EatingScheduler(){}

    public static void schedule(Context c,long startMs,int extensionCount){
        cancel(c);
        long now=System.currentTimeMillis();
        long check=startMs+(20L+10L*extensionCount)*60_000L;
        long finish=startMs+(30L+10L*extensionCount)*60_000L;
        scheduleOne(c,CHECK,check,now);
        scheduleOne(c,AUTO_FINISH,finish,now);
        DiagnosticStore.log(c,"EATING_ALARMS_SCHEDULED","check="+check+" finish="+finish+" ext="+extensionCount);
    }

    public static void cancel(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        am.cancel(pending(c,CHECK));
        am.cancel(pending(c,AUTO_FINISH));
    }

    private static void scheduleOne(Context c,int type,long at,long now){
        if(at<=now)at=now+1500L;
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c,type);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()){
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        }else{
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        }
    }

    private static PendingIntent pending(Context c,int type){
        Intent i=new Intent(c,EatingReceiver.class);
        i.putExtra("type",type);
        return PendingIntent.getBroadcast(c,BASE+type,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
