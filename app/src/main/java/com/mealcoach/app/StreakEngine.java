package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class StreakEngine {
    private static final String PREFS="streak_v3";
    private static final String K_START="start";
    private static final String K_FIRST="first";
    private static final String K_BEST="best";
    private static final String K_RESETS="resets";
    private static final String K_MEME="meme";
    private static final long DAY=24L*60L*60L*1000L;

    private StreakEngine(){}

    public static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public static void ensure(Context c){
        SharedPreferences p=prefs(c);
        if(!p.contains(K_START)){
            long now=System.currentTimeMillis();
            p.edit().putLong(K_START,now).putLong(K_FIRST,now).putInt(K_BEST,0).putBoolean(K_MEME,true).apply();
        }
    }

    public static int days(Context c){
        ensure(c);
        long start=prefs(c).getLong(K_START,System.currentTimeMillis());
        return (int)Math.max(0,(System.currentTimeMillis()-start)/DAY);
    }

    public static int best(Context c){
        ensure(c);
        return Math.max(prefs(c).getInt(K_BEST,0),days(c));
    }

    public static boolean meme(Context c){
        ensure(c);
        return prefs(c).getBoolean(K_MEME,true);
    }

    public static void toggleMeme(Context c){
        prefs(c).edit().putBoolean(K_MEME,!meme(c)).apply();
    }

    public static void reset(Context c){
        ensure(c);
        SharedPreferences p=prefs(c);
        int cur=days(c);
        int best=Math.max(p.getInt(K_BEST,0),cur);
        long now=System.currentTimeMillis();
        String old=p.getString(K_RESETS,"");
        String next=old.isEmpty()?String.valueOf(now):old+","+now;
        p.edit().putInt(K_BEST,best).putString(K_RESETS,next).putLong(K_START,now).apply();
        DiagnosticStore.log(c,"STREAK_RESET","previous_days="+cur+" best="+best);
    }

    public static int cleanDaysTracked(Context c){
        ensure(c);
        long first=prefs(c).getLong(K_FIRST,System.currentTimeMillis());
        int tracked=(int)Math.min(30,Math.max(1,(System.currentTimeMillis()-first)/DAY+1));
        Set<Long> resetDays=new LinkedHashSet<>();
        String raw=prefs(c).getString(K_RESETS,"");
        if(!raw.isEmpty()){
            long cutoff=System.currentTimeMillis()-30*DAY;
            for(String s:raw.split(",")){
                try{
                    long t=Long.parseLong(s);
                    if(t>=cutoff)resetDays.add(t/DAY);
                }catch(Exception ignored){}
            }
        }
        return Math.max(0,tracked-resetDays.size());
    }

    public static int trackedWindow(Context c){
        ensure(c);
        long first=prefs(c).getLong(K_FIRST,System.currentTimeMillis());
        return (int)Math.min(30,Math.max(1,(System.currentTimeMillis()-first)/DAY+1));
    }

    public static String scene(Context c){
        int d=days(c);
        if(d>=100)return "👑  🖥️  🪴  🛋️  🎧  🌌";
        if(d>=60)return "🏠  🖥️  🪴  🛋️  🎧";
        if(d>=30)return "🖥️  🪴  🛋️  🎧";
        if(d>=14)return "🖥️  🪴  🎧";
        if(d>=7)return "🖥️  🪴";
        if(d>=3)return "💡  🪑";
        if(d>=1)return "💡";
        return "·";
    }

    public static String rank(Context c){
        int d=days(c);
        if(d>=100)return "ЛЕГЕНДА";
        if(d>=60)return "ФОРТЕЦЯ";
        if(d>=30)return "БАЗА ГОТОВА";
        if(d>=14)return "СТАБІЛЬНО";
        if(d>=7)return "ТИЖДЕНЬ";
        if(d>=3)return "СЕРІЯ ПОЧАЛАСЬ";
        if(d>=1)return "ХАРОШ";
        return "СТАРТ";
    }

    public static String dayMarks(Context c,int max){
        ensure(c);
        int d=days(c);
        if(d<=0)return "Ще немає завершених днів";
        long start=prefs(c).getLong(K_START,System.currentTimeMillis());
        StringBuilder b=new StringBuilder();
        int from=Math.max(1,d-max+1);
        SimpleDateFormat fmt=new SimpleDateFormat("dd.MM",Locale.getDefault());
        for(int i=d;i>=from;i--){
            long when=start+i*DAY;
            String label=i==1?"харош":i==3?"серія почалась":i==7?"тиждень":i==14?"стабільно":i==30?"місяць":"тримаєшся";
            b.append(i).append(" день — ").append(label).append(" — ").append(fmt.format(new Date(when))).append("\n");
        }
        return b.toString().trim();
    }
}
