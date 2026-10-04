package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class PenaltyManager {
    private static final String PREFS="penalty_v4";
    private static final String BLOCK="social_block";
    private static final String BLOCK_UNTIL="social_block_until";

    private PenaltyManager(){}

    public static void setBlocked(Context c,boolean v){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit()
                .putBoolean(BLOCK,v)
                .putLong(BLOCK_UNTIL,0L)
                .apply();
        DiagnosticStore.log(c,"SOCIAL_BLOCK",String.valueOf(v));
    }

    public static void setTemporaryBlocked(Context c,long durationMs){
        long until=System.currentTimeMillis()+Math.max(1_000L,durationMs);
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit()
                .putBoolean(BLOCK,true)
                .putLong(BLOCK_UNTIL,until)
                .apply();
        DiagnosticStore.log(c,"SOCIAL_BLOCK_TEST","until="+until);
    }

    public static boolean blocked(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.getBoolean(BLOCK,false))return false;

        long until=p.getLong(BLOCK_UNTIL,0L);
        if(until>0L&&System.currentTimeMillis()>=until){
            p.edit().putBoolean(BLOCK,false).putLong(BLOCK_UNTIL,0L).apply();
            return false;
        }
        return true;
    }
}
