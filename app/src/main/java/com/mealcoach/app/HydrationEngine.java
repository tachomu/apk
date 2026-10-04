package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HydrationEngine {
    private static final String PREFS = "hydration_v3";
    private static final String K_ACTIVE = "active";
    private static final String K_WAKE = "wake";
    private static final String K_TOTAL = "total";
    private static final String K_LAST_TS = "last_ts";
    private static final String K_LAST_AMOUNT = "last_amount";
    private static final String K_UNDO_VALID = "undo_valid";
    private static final String K_GOAL = "goal";
    private static final String K_Q1 = "q1";
    private static final String K_Q2 = "q2";
    private static final String K_Q3 = "q3";
    private static final String FILE = "water_log.csv";
    private static final long DEFAULT_DAY_MS = 16L * 60L * 60L * 1000L;

    public static final int GREEN = 0;
    public static final int YELLOW = 1;
    public static final int ORANGE = 2;
    public static final int RED = 3;

    private HydrationEngine(){}

    public static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int goal(Context c){
        return prefs(c).getInt(K_GOAL, 2500);
    }

    public static int quick(Context c, int index){
        SharedPreferences p=prefs(c);
        if(index==1) return p.getInt(K_Q1, 250);
        if(index==2) return p.getInt(K_Q2, 350);
        return p.getInt(K_Q3, 500);
    }

    public static void setGoal(Context c,int goal){
        prefs(c).edit().putInt(K_GOAL, Math.max(500, goal)).apply();
    }

    public static void setQuick(Context c,int index,int value){
        value=Math.max(50, Math.min(2000, value));
        SharedPreferences.Editor e=prefs(c).edit();
        if(index==1)e.putInt(K_Q1,value);
        else if(index==2)e.putInt(K_Q2,value);
        else e.putInt(K_Q3,value);
        e.apply();
    }

    public static void wake(Context c){
        long now=System.currentTimeMillis();
        prefs(c).edit()
                .putBoolean(K_ACTIVE,true)
                .putLong(K_WAKE,now)
                .putInt(K_TOTAL,0)
                .putLong(K_LAST_TS,0L)
                .putInt(K_LAST_AMOUNT,0)
                .putBoolean(K_UNDO_VALID,false)
                .apply();
        log(c,"WAKE",0,0);
        WaterScheduler.scheduleNext(c,60*60_000L);
    }

    public static void sleep(Context c){
        prefs(c).edit().putBoolean(K_ACTIVE,false).putBoolean(K_UNDO_VALID,false).apply();
        WaterScheduler.cancel(c);
        log(c,"SLEEP",0,total(c));
    }

    public static boolean active(Context c){
        return prefs(c).getBoolean(K_ACTIVE,false);
    }

    public static int total(Context c){
        return prefs(c).getInt(K_TOTAL,0);
    }

    public static long wakeTime(Context c){
        return prefs(c).getLong(K_WAKE,System.currentTimeMillis());
    }

    public static long lastDrinkTime(Context c){
        return prefs(c).getLong(K_LAST_TS,0L);
    }

    public static int add(Context c,int amount){
        if(!active(c) || amount<=0) return total(c);
        SharedPreferences p=prefs(c);
        int next=p.getInt(K_TOTAL,0)+amount;
        long now=System.currentTimeMillis();
        p.edit().putInt(K_TOTAL,next)
                .putLong(K_LAST_TS,now)
                .putInt(K_LAST_AMOUNT,amount)
                .putBoolean(K_UNDO_VALID,true)
                .apply();
        log(c,"DRINK",amount,next);
        WaterScheduler.scheduleNext(c,75*60_000L);
        return next;
    }

    public static boolean undo(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_UNDO_VALID,false))return false;
        int amount=p.getInt(K_LAST_AMOUNT,0);
        if(amount<=0)return false;
        int next=Math.max(0,p.getInt(K_TOTAL,0)-amount);
        p.edit().putInt(K_TOTAL,next).putBoolean(K_UNDO_VALID,false).apply();
        log(c,"UNDO",-amount,next);
        WaterScheduler.scheduleNext(c,30*60_000L);
        return true;
    }

    public static int expectedNow(Context c){
        long elapsed=Math.max(0,System.currentTimeMillis()-wakeTime(c));
        double ratio=Math.min(1.0, elapsed/(double)DEFAULT_DAY_MS);
        return (int)Math.round(goal(c)*ratio);
    }

    public static int status(Context c){
        int expected=expectedNow(c);
        if(expected<=250)return GREEN;
        int actual=total(c);
        double ratio=actual/(double)Math.max(1,expected);
        if(ratio>=0.9)return GREEN;
        if(ratio>=0.75)return YELLOW;
        if(ratio>=0.55)return ORANGE;
        return RED;
    }

    public static int projectedTotal(Context c){
        long elapsed=Math.max(15*60_000L,System.currentTimeMillis()-wakeTime(c));
        double perMs=total(c)/(double)elapsed;
        return (int)Math.round(Math.min(goal(c)*1.6, perMs*DEFAULT_DAY_MS));
    }

    public static String statusLabel(Context c){
        switch(status(c)){
            case YELLOW:return "ТРОХИ ВІДСТАЄШ";
            case ORANGE:return "ТРЕБА НАЗДОГНАТИ";
            case RED:return "МАЛО ВОДИ";
            default:return "ГІДРАЦІЯ ДОБРА";
        }
    }

    public static String warning(Context c){
        String[] yellow={
                "Темп трохи нижчий за план. Кілька ковтків зараз вирівняють день.",
                "Ти трохи відстаєш від свого темпу. Не треба пити залпом — просто додай воду зараз."
        };
        String[] orange={
                "Тривале недопивання може супроводжуватись сухістю в роті, втомою або гіршою концентрацією.",
                "Води помітно менше, ніж очікувалось на цей момент. Краще поступово наздогнати протягом наступної години."
        };
        String[] red={
                "Ти суттєво відстаєш. Недостатнє пиття може сприяти головному болю, втомі та сухості в роті.",
                "Гідрація сильно нижче твого темпу. Пий поступово; не намагайся закрити весь дефіцит одним великим об'ємом."
        };
        int idx=(int)((System.currentTimeMillis()/60000L)%2);
        int s=status(c);
        if(s==YELLOW)return yellow[idx];
        if(s==ORANGE)return orange[idx];
        if(s==RED)return red[idx];
        return "Все в темпі. Просто продовжуй так само.";
    }

    private static void log(Context c,String event,int amount,int total){
        try{
            File f=new File(c.getFilesDir(),FILE);
            boolean fresh=!f.exists();
            try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f,true), StandardCharsets.UTF_8))){
                if(fresh)w.write("timestamp,event,amount_ml,total_ml\n");
                String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                w.write(ts+","+event+","+amount+","+total+"\n");
            }
        }catch(Exception ignored){}
    }

    public static String recent(Context c,int max){
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return "Поки порожньо";
        List<String> lines=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line; boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                lines.add(line);
            }
        }catch(Exception e){return "Не вдалося прочитати історію";}
        if(lines.isEmpty())return "Поки порожньо";
        int start=Math.max(0,lines.size()-max);
        List<String> tail=new ArrayList<>(lines.subList(start,lines.size()));
        Collections.reverse(tail);
        StringBuilder b=new StringBuilder();
        for(String line:tail){
            String[] p=line.split(",");
            if(p.length>=4){
                b.append(p[0]).append("  •  ");
                if("DRINK".equals(p[1])) b.append("+").append(p[2]).append(" мл");
                else if("UNDO".equals(p[1])) b.append("скасовано ").append(Math.abs(Integer.parseInt(p[2]))).append(" мл");
                else b.append(p[1]);
                b.append("\n");
            }
        }
        return b.toString().trim();
    }
}
