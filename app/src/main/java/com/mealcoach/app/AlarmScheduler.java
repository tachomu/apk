package com.mealcoach.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.SharedPreferences;

public final class AlarmScheduler {
    public static final int PREP_60=1;
    public static final int PREP_20=2;
    public static final int PREFERRED=3;
    public static final int LATE_15=4;
    public static final int LATE_30=5;
    public static final int LATE_45=6;
    public static final int FINAL=7;

    private static final int BASE_REQ=4100;
    private static final long MIN=60_000L;

    private AlarmScheduler(){}

    public static void scheduleCurrent(Context c){
        cancelAll(c);
        if(!AppSettings.foodEnabled(c))return;
        SharedPreferences p=MealEngine.prefs(c);
        if(!p.getBoolean(MealEngine.K_DAY,false)||p.getBoolean(MealEngine.K_EATING,false))return;

        long now=System.currentTimeMillis();
        long pref=p.getLong(MealEngine.K_PREF,0L);
        long dead=p.getLong(MealEngine.K_DEADLINE,0L);
        boolean first=p.getBoolean(MealEngine.K_FIRST,false);
        if(pref<=0||dead<=0)return;

        // The first meal is only 30 minutes after wake, so a -60 warning is impossible.
        if(!first)scheduleIfFuture(c,PREP_60,pref-60*MIN,dead,now);
        int prepLead=BehaviorLearning.prepLeadMinutes(c);
        scheduleIfFuture(c,PREP_20,pref-prepLead*MIN,dead,now);
        scheduleIfFuture(c,PREFERRED,pref,dead,now);
        scheduleIfFuture(c,LATE_15,pref+15*MIN,dead,now);
        scheduleIfFuture(c,LATE_30,pref+30*MIN,dead,now);
        scheduleIfFuture(c,LATE_45,pref+45*MIN,dead,now);
        scheduleOne(c,FINAL,dead,now,true);

        DiagnosticStore.log(c,"FOOD_SCHEDULE_COMPLETE","first="+first+" pref="+pref+" deadline="+dead+" prep_lead="+prepLead+" exact="+canExact(c));
    }

    private static void scheduleIfFuture(Context c,int type,long at,long deadline,long now){
        if(at>=deadline-500L)return;
        scheduleOne(c,type,at,now,false);
    }

    private static void scheduleOne(Context c,int type,long at,long now,boolean recoverPast){
        if(at<=now){
            if(!recoverPast)return;
            at=now+1500L;
        }
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        PendingIntent pi=pending(c,type);
        String method;
        if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms()){
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            method="inexact_allow_idle";
        }else{
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            method="exact_allow_idle";
        }
        DiagnosticStore.log(c,"FOOD_ALARM_SCHEDULED","type="+type+" at="+at+" method="+method);
    }

    public static boolean canExact(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT<31||(am!=null&&am.canScheduleExactAlarms());
    }

    public static void cancelAll(Context c){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        for(int type=PREP_60;type<=FINAL;type++)am.cancel(pending(c,type));
    }

    private static PendingIntent pending(Context c,int type){
        Intent i=new Intent(c,NotificationReceiver.class);
        i.putExtra("type",type);
        return PendingIntent.getBroadcast(c,BASE_REQ+type,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
