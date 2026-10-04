package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS="app_settings_v4";
    private static final String FOOD="food_enabled";
    private static final String WATER="water_enabled";
    private static final String STREAK="streak_enabled";
    private static final String FOOD_SOUND="food_sound";
    private static final String WATER_SOUND="water_sound";

    private AppSettings(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public static boolean foodEnabled(Context c){return p(c).getBoolean(FOOD,true);}
    public static boolean waterEnabled(Context c){return p(c).getBoolean(WATER,true);}
    public static boolean streakEnabled(Context c){return p(c).getBoolean(STREAK,true);}

    public static void setFoodEnabled(Context c,boolean v){p(c).edit().putBoolean(FOOD,v).apply();}
    public static void setWaterEnabled(Context c,boolean v){p(c).edit().putBoolean(WATER,v).apply();}
    public static void setStreakEnabled(Context c,boolean v){p(c).edit().putBoolean(STREAK,v).apply();}

    public static String foodSound(Context c){return p(c).getString(FOOD_SOUND,"");}
    public static String waterSound(Context c){return p(c).getString(WATER_SOUND,"");}
    public static void setFoodSound(Context c,String uri){p(c).edit().putString(FOOD_SOUND,uri==null?"":uri).apply();}
    public static void setWaterSound(Context c,String uri){p(c).edit().putString(WATER_SOUND,uri==null?"":uri).apply();}
}
