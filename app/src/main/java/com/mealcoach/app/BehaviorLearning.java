package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class BehaviorLearning {
    private static final String PREFS="behavior_learning_v4";
    private static final String FOOD_AVG="food_late_avg";
    private static final String FOOD_N="food_n";

    private BehaviorLearning(){}

    public static void recordMealStart(Context c,long preferred,long actual){
        if(preferred<=0)return;
        float late=(actual-preferred)/60_000f;
        late=Math.max(-60f,Math.min(120f,late));
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        int n=p.getInt(FOOD_N,0);
        float old=p.getFloat(FOOD_AVG,0f);
        float alpha=n<5?1f/(n+1):0.22f;
        float next=old+(late-old)*alpha;
        p.edit().putFloat(FOOD_AVG,next).putInt(FOOD_N,n+1).apply();
        DiagnosticStore.log(c,"LEARN_FOOD_RESPONSE","late_min="+late+" avg="+next);
    }

    public static float foodLatenessAvg(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getFloat(FOOD_AVG,0f);
    }

    public static String prep20Text(Context c){
        float avg=foodLatenessAvg(c);
        if(avg>25f)return "Не починай нову катку. Ти часто затягуєш після бажаного часу — зараз реально відкладай справи й готуй їжу.";
        if(avg>10f)return "Не починай нову катку. Відкладай справи й готуй їжу — останнім часом ти трохи запізнюєшся.";
        return "Не починай нову катку. Відкладай справи й готуй їжу.";
    }
}
