package com.mealcoach.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

public final class MealEngine {
    public static final String PREFS = "mealcoach";
    public static final String K_DAY = "day";
    public static final String K_MEALS = "meals";
    public static final String K_SNACKS = "snacks";
    public static final String K_CONSEC_SNACKS = "consecutive_snacks";
    public static final String K_EATING = "eating";
    public static final String K_EATING_START = "eating_start";
    public static final String K_EATING_EXT = "eating_ext";
    public static final String K_START = "cycle_start";
    public static final String K_PREF = "preferred";
    public static final String K_DEADLINE = "deadline";
    public static final String K_FIRST = "first_cycle";
    public static final String K_DELAY_USED = "delay_used";
    public static final String K_PHOTO = "photo_uri";
    public static final String K_TEST = "test_mode";
    public static final String K_LATE_MEALS = "late_meals";
    public static final String K_AUTO_MEALS = "auto_meals";
    private static final long MIN = 60_000L;

    private MealEngine(){}

    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public static void wake(Context c){
        resetFoodSignals(c);
        PenaltyManager.setBlocked(c,false);
        long now=System.currentTimeMillis();
        prefs(c).edit().clear()
                .putBoolean(K_DAY,true).putBoolean(K_TEST,false)
                .putInt(K_MEALS,0).putInt(K_SNACKS,0).putInt(K_CONSEC_SNACKS,0)
                .putBoolean(K_EATING,false).putInt(K_EATING_EXT,0).putInt(K_LATE_MEALS,0).putInt(K_AUTO_MEALS,0)
                .putLong(K_START,now).putLong(K_PREF,now+30*MIN).putLong(K_DEADLINE,now+60*MIN)
                .putBoolean(K_FIRST,true).putInt(K_DELAY_USED,0).apply();
        HydrationEngine.wake(c);
        LogStore.log(c,"WAKE",0,0,"","");
        DiagnosticStore.log(c,"WAKE","food + hydration day started");
        AlarmScheduler.scheduleCurrent(c);
        WatchdogScheduler.schedule(c);
    }

    public static void startEating(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_DAY,false))return;
        resetFoodSignals(c);
        PenaltyManager.setBlocked(c,false);
        long now=System.currentTimeMillis();
        int late=p.getInt(K_LATE_MEALS,0);
        if(now>p.getLong(K_PREF,Long.MAX_VALUE))late++;
        p.edit().putBoolean(K_EATING,true).putLong(K_EATING_START,now).putInt(K_EATING_EXT,0).putInt(K_LATE_MEALS,late).apply();
        LogStore.log(c,"SIT_EAT",p.getInt(K_MEALS,0),p.getInt(K_SNACKS,0),"",p.getString(K_PHOTO,""));
        DiagnosticStore.log(c,"SIT_EAT","auto-finish in 30m");
        EatingScheduler.schedule(c,now,0);
    }

    public static boolean extendEating(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_EATING,false))return false;
        int ext=p.getInt(K_EATING_EXT,0);
        if(ext>=2)return false;
        ext++;
        p.edit().putInt(K_EATING_EXT,ext).apply();
        EatingScheduler.schedule(c,p.getLong(K_EATING_START,System.currentTimeMillis()),ext);
        DiagnosticStore.log(c,"EATING_EXTEND","count="+ext);
        return true;
    }

    public static void ate(Context c){ate(c,false);}

    public static void ate(Context c,boolean automatic){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_DAY,false)||!p.getBoolean(K_EATING,false))return;
        int meals=p.getInt(K_MEALS,0)+1;
        int snacks=p.getInt(K_SNACKS,0);
        long started=p.getLong(K_EATING_START,0L);
        long now=System.currentTimeMillis();
        String photo=p.getString(K_PHOTO,"");
        String duration=started>0?"duration_min="+Math.max(0,(now-started)/MIN):"";
        if(automatic){
            duration+=(duration.isEmpty()?"":";")+"auto_finish=true";
            p.edit().putInt(K_AUTO_MEALS,p.getInt(K_AUTO_MEALS,0)+1).apply();
        }

        resetFoodSignals(c);
        PenaltyManager.setBlocked(c,false);
        EatingScheduler.cancel(c);

        p.edit().putInt(K_MEALS,meals).putInt(K_CONSEC_SNACKS,0)
                .putBoolean(K_EATING,false).putLong(K_EATING_START,0L).putInt(K_EATING_EXT,0)
                .putLong(K_START,now).putLong(K_PREF,now+3*60*MIN).putLong(K_DEADLINE,now+4*60*MIN)
                .putBoolean(K_FIRST,false).putInt(K_DELAY_USED,0).putString(K_PHOTO,"").apply();

        LogStore.log(c,"FULL_MEAL",meals,snacks,duration,photo);
        DiagnosticStore.log(c,automatic?"FULL_MEAL_AUTO":"FULL_MEAL","meals="+meals);
        AlarmScheduler.scheduleCurrent(c);
    }

    public static boolean snack(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_DAY,false)||p.getBoolean(K_EATING,false))return false;
        int snacks=p.getInt(K_SNACKS,0)+1;
        int consecutive=p.getInt(K_CONSEC_SNACKS,0);
        boolean canExtend=consecutive<2;
        long extension=60*MIN;
        String photo=p.getString(K_PHOTO,"");
        SharedPreferences.Editor e=p.edit().putInt(K_SNACKS,snacks)
                .putInt(K_CONSEC_SNACKS,consecutive+1).putString(K_PHOTO,"");
        if(canExtend)e.putLong(K_PREF,p.getLong(K_PREF,System.currentTimeMillis())+extension)
                .putLong(K_DEADLINE,p.getLong(K_DEADLINE,System.currentTimeMillis())+extension)
                .putInt(K_DELAY_USED,0);
        e.apply();
        LogStore.log(c,"SNACK",p.getInt(K_MEALS,0),snacks,canExtend?"deadline_shifted":"no_more_extension",photo);
        if(canExtend){resetFoodSignals(c);AlarmScheduler.scheduleCurrent(c);}
        return canExtend;
    }

    public static boolean delay15(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_DAY,false)||p.getBoolean(K_EATING,false))return false;
        long now=System.currentTimeMillis();
        if(now<p.getLong(K_PREF,Long.MAX_VALUE))return false;
        int used=p.getInt(K_DELAY_USED,0);
        if(used>=2)return false;
        p.edit()
                .putLong(K_PREF,p.getLong(K_PREF,now)+15*MIN)
                .putLong(K_DEADLINE,p.getLong(K_DEADLINE,now)+15*MIN)
                .putInt(K_DELAY_USED,used+1).apply();
        resetFoodSignals(c);
        LogStore.log(c,"DELAY_15",p.getInt(K_MEALS,0),p.getInt(K_SNACKS,0),"used="+(used+1),"");
        AlarmScheduler.scheduleCurrent(c);
        return true;
    }

    public static void sleep(Context c){
        SharedPreferences p=prefs(c);
        int meals=p.getInt(K_MEALS,0), snacks=p.getInt(K_SNACKS,0);
        long now=System.currentTimeMillis();
        DaySummaryStore.closeDay(c,
                HydrationEngine.wakeTime(c),
                now,
                meals,
                snacks,
                HydrationEngine.total(c),
                HydrationEngine.goal(c),
                p.getInt(K_LATE_MEALS,0),
                p.getInt(K_AUTO_MEALS,0));
        resetFoodSignals(c); EatingScheduler.cancel(c); HydrationEngine.sleep(c); PenaltyManager.setBlocked(c,false);
        p.edit().putBoolean(K_DAY,false).putBoolean(K_EATING,false).putLong(K_EATING_START,0L)
                .putInt(K_EATING_EXT,0).putString(K_PHOTO,"").apply();
        WatchdogScheduler.cancel(c);
        LogStore.log(c,"SLEEP",meals,snacks,"","");
        DiagnosticStore.log(c,"SLEEP","food + hydration stopped");
    }

    public static void savePhoto(Context c,String uri){
        SharedPreferences p=prefs(c);
        p.edit().putString(K_PHOTO,uri==null?"":uri).apply();
        LogStore.log(c,"PHOTO",p.getInt(K_MEALS,0),p.getInt(K_SNACKS,0),"",uri);
    }

    private static void resetFoodSignals(Context c){
        AlarmScheduler.cancelAll(c);
        AlarmService.stop(c);
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null){
            nm.cancel(NotificationHelper.REMINDER_ID);
            nm.cancel(NotificationHelper.EATING_ID);
            nm.cancel(NotificationHelper.SERVICE_ID);
        }
    }
}
