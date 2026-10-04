package com.mealcoach.app;

import android.content.Context;

public final class PenaltyManager {
    private static final String PREFS="penalty_v4";
    private static final String BLOCK="social_block";
    private PenaltyManager(){}

    public static void setBlocked(Context c,boolean v){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(BLOCK,v).apply();
        DiagnosticStore.log(c,"SOCIAL_BLOCK",String.valueOf(v));
    }
    public static boolean blocked(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(BLOCK,false);
    }
}
