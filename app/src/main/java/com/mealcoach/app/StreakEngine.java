package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class StreakEngine {
    private static final String PREFS="streak_v4";
    private static final String K_START="start",K_FIRST="first",K_BEST="best",K_RESETS="resets",K_ARCHIVE="archive",K_MEME="meme";
    private static final long DAY=24L*60L*60L*1000L;

    private StreakEngine(){}

    public static SharedPreferences prefs(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.getBoolean("_migrated",false)){
            SharedPreferences old=c.getSharedPreferences("streak_v3",Context.MODE_PRIVATE);
            SharedPreferences.Editor e=p.edit();
            if(old.contains("start"))e.putLong(K_START,old.getLong("start",System.currentTimeMillis()));
            if(old.contains("first"))e.putLong(K_FIRST,old.getLong("first",System.currentTimeMillis()));
            if(old.contains("best"))e.putInt(K_BEST,old.getInt("best",0));
            if(old.contains("resets"))e.putString(K_RESETS,old.getString("resets",""));
            if(old.contains("meme"))e.putBoolean(K_MEME,old.getBoolean("meme",true));
            e.putBoolean("_migrated",true).apply();
        }
        return p;
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

    public static int best(Context c){ensure(c);return Math.max(prefs(c).getInt(K_BEST,0),days(c));}
    public static boolean meme(Context c){ensure(c);return prefs(c).getBoolean(K_MEME,true);}
    public static void toggleMeme(Context c){prefs(c).edit().putBoolean(K_MEME,!meme(c)).apply();}

    public static void reset(Context c){
        ensure(c);
        SharedPreferences p=prefs(c);
        int cur=days(c);
        int best=Math.max(p.getInt(K_BEST,0),cur);
        long now=System.currentTimeMillis();
        String resets=p.getString(K_RESETS,"");
        String archive=p.getString(K_ARCHIVE,"");
        resets=resets.isEmpty()?String.valueOf(now):resets+","+now;
        archive=archive.isEmpty()?now+"|"+cur:archive+";"+now+"|"+cur;
        p.edit().putInt(K_BEST,best).putString(K_RESETS,resets).putString(K_ARCHIVE,archive).putLong(K_START,now).apply();
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
                try{long t=Long.parseLong(s);if(t>=cutoff)resetDays.add(t/DAY);}catch(Exception ignored){}
            }
        }
        return Math.max(0,tracked-resetDays.size());
    }

    public static int trackedWindow(Context c){
        ensure(c);
        long first=prefs(c).getLong(K_FIRST,System.currentTimeMillis());
        return (int)Math.min(30,Math.max(1,(System.currentTimeMillis()-first)/DAY+1));
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

    public static int nextMilestoneValue(Context c){
        int d=days(c);
        int[] m={1,3,7,14,30,60,100};
        for(int v:m)if(d<v)return v;
        return Math.max(100,d);
    }

    public static String nextMilestone(Context c){
        int d=days(c);
        int[] m={1,3,7,14,30,60,100};
        for(int v:m)if(d<v)return "До наступного апгрейду: "+(v-d)+" дн.  •  ціль "+v;
        return "Усі базові апгрейди відкрито.";
    }

    public static String badges(Context c){
        int d=best(c);
        StringBuilder b=new StringBuilder();
        if(d>=3)b.append("BRONZE 3  ");
        if(d>=7)b.append("SILVER 7  ");
        if(d>=14)b.append("GOLD 14  ");
        if(d>=30)b.append("PLATINUM 30  ");
        if(d>=60)b.append("MASTER 60  ");
        if(d>=100)b.append("LEGEND 100");
        return b.length()==0?"Бейджі відкриються на 3 дні":b.toString().trim();
    }

    public static String dayMarks(Context c,int max){
        ensure(c);int d=days(c);if(d<=0)return "Ще немає завершених днів";
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

    public static String archiveText(Context c,int max){
        ensure(c);
        String raw=prefs(c).getString(K_ARCHIVE,"");
        if(raw.isEmpty())return "Попередніх серій ще немає";
        String[] items=raw.split(";");
        SimpleDateFormat fmt=new SimpleDateFormat("dd.MM.yy",Locale.getDefault());
        StringBuilder b=new StringBuilder();
        int shown=0;
        for(int i=items.length-1;i>=0&&shown<max;i--,shown++){
            String[] p=items[i].split("\\|");
            if(p.length<2)continue;
            try{
                long end=Long.parseLong(p[0]);int days=Integer.parseInt(p[1]);
                long start=end-Math.max(0,days)*DAY;
                b.append(days).append(" дн.  •  ")
                        .append(fmt.format(new Date(start)))
                        .append("–")
                        .append(fmt.format(new Date(end)))
                        .append("\n");
            }catch(Exception ignored){}
        }
        return b.toString().trim();
    }
}
