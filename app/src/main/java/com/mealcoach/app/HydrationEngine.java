package com.mealcoach.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HydrationEngine {
    private static final String PREFS="hydration_v4";
    private static final String K_ACTIVE="active",K_WAKE="wake",K_TOTAL="total",K_LAST_TS="last_ts",K_LAST_AMOUNT="last_amount",K_UNDO_VALID="undo_valid";
    private static final String K_GOAL="goal",K_Q1="q1",K_Q2="q2",K_Q3="q3",K_AWAKE_AVG="awake_avg";
    private static final String FILE="water_log.csv";
    private static final long DEFAULT_DAY=16L*60L*60L*1000L;
    public static final int GREEN=0,ORANGE=1,RED=2;

    private HydrationEngine(){}

    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static int goal(Context c){return prefs(c).getInt(K_GOAL,2500);}
    public static int quick(Context c,int i){SharedPreferences p=prefs(c);return i==1?p.getInt(K_Q1,250):i==2?p.getInt(K_Q2,350):p.getInt(K_Q3,500);}
    public static void setGoal(Context c,int v){prefs(c).edit().putInt(K_GOAL,Math.max(500,v)).apply();}
    public static void setQuick(Context c,int i,int v){v=Math.max(50,Math.min(2000,v));SharedPreferences.Editor e=prefs(c).edit();if(i==1)e.putInt(K_Q1,v);else if(i==2)e.putInt(K_Q2,v);else e.putInt(K_Q3,v);e.apply();}
    public static boolean active(Context c){return prefs(c).getBoolean(K_ACTIVE,false);}
    public static int total(Context c){return prefs(c).getInt(K_TOTAL,0);}
    public static long wakeTime(Context c){return prefs(c).getLong(K_WAKE,System.currentTimeMillis());}
    public static long lastDrinkTime(Context c){return prefs(c).getLong(K_LAST_TS,0L);}
    public static long expectedAwakeMs(Context c){return prefs(c).getLong(K_AWAKE_AVG,DEFAULT_DAY);}

    public static void wake(Context c){
        if(!AppSettings.waterEnabled(c))return;
        long now=System.currentTimeMillis();
        prefs(c).edit().putBoolean(K_ACTIVE,true).putLong(K_WAKE,now).putInt(K_TOTAL,0).putLong(K_LAST_TS,0L)
                .putInt(K_LAST_AMOUNT,0).putBoolean(K_UNDO_VALID,false).apply();
        log(c,"WAKE",0,0);
        WaterScheduler.scheduleNext(c,60*60_000L);
    }

    public static void sleep(Context c){
        SharedPreferences p=prefs(c);
        long now=System.currentTimeMillis();
        long wake=p.getLong(K_WAKE,now);
        long dur=Math.max(6*60*60_000L,Math.min(22*60*60_000L,now-wake));
        long old=p.getLong(K_AWAKE_AVG,DEFAULT_DAY);
        long next=(old*3+dur)/4;
        p.edit().putBoolean(K_ACTIVE,false).putBoolean(K_UNDO_VALID,false).putLong(K_AWAKE_AVG,next).apply();
        WaterScheduler.cancel(c);
        log(c,"SLEEP",0,total(c));
    }

    public static int add(Context c,int amount){
        if(!active(c)||amount<=0)return total(c);
        SharedPreferences p=prefs(c);int next=p.getInt(K_TOTAL,0)+amount;long now=System.currentTimeMillis();
        p.edit().putInt(K_TOTAL,next).putLong(K_LAST_TS,now).putInt(K_LAST_AMOUNT,amount).putBoolean(K_UNDO_VALID,true).apply();
        log(c,"DRINK",amount,next);
        WaterScheduler.scheduleNext(c,75*60_000L);
        return next;
    }

    public static boolean undo(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_UNDO_VALID,false))return false;
        int a=p.getInt(K_LAST_AMOUNT,0);if(a<=0)return false;
        int next=Math.max(0,p.getInt(K_TOTAL,0)-a);
        p.edit().putInt(K_TOTAL,next).putBoolean(K_UNDO_VALID,false).apply();
        log(c,"UNDO",-a,next);WaterScheduler.scheduleNext(c,30*60_000L);return true;
    }

    public static int expectedNow(Context c){
        long elapsed=Math.max(0,System.currentTimeMillis()-wakeTime(c));
        double ratio=Math.min(1.0,elapsed/(double)Math.max(8*60*60_000L,expectedAwakeMs(c)));
        return (int)Math.round(goal(c)*ratio);
    }

    public static int status(Context c){
        int expected=expectedNow(c);
        if(expected<250)return GREEN;
        double ratio=total(c)/(double)Math.max(1,expected);
        if(ratio>=0.90)return GREEN;
        if(ratio>=0.62)return ORANGE;
        return RED;
    }

    public static int projectedTotal(Context c){
        long elapsed=Math.max(20*60_000L,System.currentTimeMillis()-wakeTime(c));
        double perMs=total(c)/(double)elapsed;
        return (int)Math.round(Math.min(goal(c)*1.6,perMs*expectedAwakeMs(c)));
    }

    public static String statusLabel(Context c){
        int s=status(c);
        if(s==ORANGE)return "ТРОХИ НЕ ВСТИГАЄШ";
        if(s==RED)return "МАЛО ВОДИ";
        return "ГІДРАЦІЯ ХОРОША";
    }

    public static String warning(Context c){
        String[] orange={
                "Ти нижче свого темпу. Додай воду протягом наступної години, без залпового «наздоганяння».",
                "Темп просів. Краще кілька нормальних ковтків зараз і ще трохи пізніше.",
                "Води менше, ніж очікувалось на цей момент. Поступово повернись до темпу."
        };
        String[] red={
                "Гідрація сильно відстає. Недостатнє пиття може супроводжуватись втомою, сухістю в роті або головним болем.",
                "Ти суттєво нижче свого темпу. Зневоднення може погіршувати концентрацію та самопочуття — пий поступово.",
                "Води мало для поточного моменту дня. Не намагайся випити весь дефіцит одразу; краще вирівняй темп поступово."
        };
        if(status(c)==GREEN)return "Все в темпі. Просто продовжуй так само.";
        int idx=(int)((System.currentTimeMillis()/60000L)%3);
        return status(c)==ORANGE?orange[idx]:red[idx];
    }

    public static List<long[]> drinkPoints(Context c){
        List<long[]> out=new ArrayList<>();
        long wake=wakeTime(c);
        out.add(new long[]{wake,0});
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return out;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] p=line.split(",");
                if(p.length<4)continue;
                long ts=Long.parseLong(p[0]);
                if(ts<wake)continue;
                if("DRINK".equals(p[1])||"UNDO".equals(p[1])){
                    int total=Integer.parseInt(p[3]);
                    out.add(new long[]{ts,total});
                }
            }
        }catch(Exception ignored){}
        return out;
    }

    public static long longestGapMinutes(Context c){
        List<long[]> pts=drinkPoints(c);
        long prev=wakeTime(c),max=0;
        for(int i=1;i<pts.size();i++){
            long t=pts.get(i)[0];
            max=Math.max(max,t-prev);
            prev=t;
        }
        max=Math.max(max,System.currentTimeMillis()-prev);
        return Math.max(0,max/60_000L);
    }

    private static void log(Context c,String event,int amount,int total){
        try{
            File f=new File(c.getFilesDir(),FILE);boolean fresh=!f.exists();
            try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f,true),StandardCharsets.UTF_8))){
                if(fresh)w.write("timestamp,event,amount_ml,total_ml\n");
                w.write(System.currentTimeMillis()+","+event+","+amount+","+total+"\n");
            }
        }catch(Exception ignored){}
    }

    public static String recent(Context c,int max){
        File f=new File(c.getFilesDir(),FILE);if(!f.exists())return "Поки порожньо";
        List<String> lines=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;boolean first=true;while((line=r.readLine())!=null){if(first){first=false;continue;}lines.add(line);}
        }catch(Exception e){return "Не вдалося прочитати історію";}
        if(lines.isEmpty())return "Поки порожньо";
        int start=Math.max(0,lines.size()-max);List<String> tail=new ArrayList<>(lines.subList(start,lines.size()));Collections.reverse(tail);
        StringBuilder b=new StringBuilder();
        for(String line:tail){String[] p=line.split(",");if(p.length>=4){b.append("• ");if("DRINK".equals(p[1]))b.append("+").append(p[2]).append(" мл");else if("UNDO".equals(p[1]))b.append("скасовано ").append(Math.abs(Integer.parseInt(p[2]))).append(" мл");else b.append(p[1]);b.append("\n");}}
        return b.toString().trim();
    }
}
