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
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HydrationEngine {
    private static final String PREFS="hydration_v4";
    private static final String K_ACTIVE="active",K_WAKE="wake",K_TOTAL="total",K_LAST_TS="last_ts",K_LAST_AMOUNT="last_amount",K_UNDO_VALID="undo_valid";
    private static final String K_GOAL="goal",K_Q1="q1",K_Q2="q2",K_Q3="q3",K_AWAKE_AVG="awake_avg";
    private static final String K_LAST_REMINDER="last_reminder",K_WARNING_INDEX="warning_index";
    private static final String FILE="water_log.csv";
    private static final long DEFAULT_DAY=16L*60L*60L*1000L;

    public static final int GREEN=0,ORANGE=1,RED=2;

    private HydrationEngine(){}

    public static SharedPreferences prefs(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.getBoolean("_migrated",false)){
            SharedPreferences old=c.getSharedPreferences("hydration_v3",Context.MODE_PRIVATE);
            SharedPreferences.Editor e=p.edit();
            String[] ints={K_TOTAL,K_LAST_AMOUNT,K_GOAL,K_Q1,K_Q2,K_Q3};
            for(String k:ints)if(old.contains(k))e.putInt(k,old.getInt(k,0));
            String[] longs={K_WAKE,K_LAST_TS};
            for(String k:longs)if(old.contains(k))e.putLong(k,old.getLong(k,0L));
            if(old.contains(K_ACTIVE))e.putBoolean(K_ACTIVE,old.getBoolean(K_ACTIVE,false));
            if(old.contains(K_UNDO_VALID))e.putBoolean(K_UNDO_VALID,old.getBoolean(K_UNDO_VALID,false));
            e.putBoolean("_migrated",true).apply();
        }
        return p;
    }

    public static int goal(Context c){return prefs(c).getInt(K_GOAL,2500);}
    public static int quick(Context c,int i){
        SharedPreferences p=prefs(c);
        return i==1?p.getInt(K_Q1,250):i==2?p.getInt(K_Q2,350):p.getInt(K_Q3,500);
    }
    public static void setGoal(Context c,int v){prefs(c).edit().putInt(K_GOAL,Math.max(500,v)).apply();}
    public static void setQuick(Context c,int i,int v){
        v=Math.max(50,Math.min(2000,v));
        SharedPreferences.Editor e=prefs(c).edit();
        if(i==1)e.putInt(K_Q1,v);else if(i==2)e.putInt(K_Q2,v);else e.putInt(K_Q3,v);
        e.apply();
    }

    public static boolean active(Context c){return prefs(c).getBoolean(K_ACTIVE,false);}
    public static int total(Context c){return prefs(c).getInt(K_TOTAL,0);}
    public static long wakeTime(Context c){return prefs(c).getLong(K_WAKE,System.currentTimeMillis());}
    public static long lastDrinkTime(Context c){return prefs(c).getLong(K_LAST_TS,0L);}
    public static long expectedAwakeMs(Context c){return prefs(c).getLong(K_AWAKE_AVG,DEFAULT_DAY);}
    public static long lastReminderTime(Context c){return prefs(c).getLong(K_LAST_REMINDER,0L);}

    public static void wake(Context c){
        if(!AppSettings.waterEnabled(c))return;
        long now=System.currentTimeMillis();
        prefs(c).edit()
                .putBoolean(K_ACTIVE,true)
                .putLong(K_WAKE,now)
                .putInt(K_TOTAL,0)
                .putLong(K_LAST_TS,0L)
                .putInt(K_LAST_AMOUNT,0)
                .putBoolean(K_UNDO_VALID,false)
                .putLong(K_LAST_REMINDER,now)
                .putInt(K_WARNING_INDEX,0)
                .apply();
        log(c,"WAKE",0,0);
        WaterScheduler.scheduleNext(c,60*60_000L);
    }

    public static void sleep(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_ACTIVE,false)){
            WaterScheduler.cancel(c);
            return;
        }
        long now=System.currentTimeMillis();
        long wake=p.getLong(K_WAKE,now);
        long dur=Math.max(6*60*60_000L,Math.min(22*60*60_000L,now-wake));
        long old=p.getLong(K_AWAKE_AVG,DEFAULT_DAY);
        long next=(old*3+dur)/4;
        p.edit()
                .putBoolean(K_ACTIVE,false)
                .putBoolean(K_UNDO_VALID,false)
                .putLong(K_AWAKE_AVG,next)
                .apply();
        WaterScheduler.cancel(c);
        log(c,"SLEEP",0,total(c));
    }

    public static int add(Context c,int amount){
        if(!active(c)||amount<=0)return total(c);
        SharedPreferences p=prefs(c);
        int next=p.getInt(K_TOTAL,0)+amount;
        long now=System.currentTimeMillis();
        p.edit()
                .putInt(K_TOTAL,next)
                .putLong(K_LAST_TS,now)
                .putInt(K_LAST_AMOUNT,amount)
                .putBoolean(K_UNDO_VALID,true)
                .putLong(K_LAST_REMINDER,now)
                .apply();
        log(c,"DRINK",amount,next);
        NotificationHelper.clearWaterReminder(c);
        WaterScheduler.scheduleNext(c,75*60_000L);
        return next;
    }

    public static boolean undo(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean(K_UNDO_VALID,false))return false;
        int a=p.getInt(K_LAST_AMOUNT,0);
        if(a<=0)return false;
        int next=Math.max(0,p.getInt(K_TOTAL,0)-a);
        p.edit()
                .putInt(K_TOTAL,next)
                .putBoolean(K_UNDO_VALID,false)
                .putLong(K_LAST_REMINDER,System.currentTimeMillis())
                .apply();
        log(c,"UNDO",-a,next);
        NotificationHelper.clearWaterReminder(c);
        WaterScheduler.scheduleNext(c,30*60_000L);
        return true;
    }

    public static int expectedNow(Context c){
        long elapsed=Math.max(0,System.currentTimeMillis()-wakeTime(c));
        double ratio=Math.min(1.0,elapsed/(double)Math.max(8*60*60_000L,expectedAwakeMs(c)));
        return (int)Math.round(goal(c)*ratio);
    }

    public static int status(Context c){
        int expected=expectedNow(c);
        int actual=total(c);
        if(expected<150)return GREEN;

        double ratio=actual/(double)Math.max(1,expected);
        if(expected<350){
            return ratio>=0.75?GREEN:ORANGE;
        }

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

    public static String nextPlan(Context c){
        int s=status(c);
        int deficit=Math.max(0,expectedNow(c)-total(c));
        if(s==GREEN)return "Наступний м'який орієнтир: "+quick(c,1)+" мл приблизно протягом 60–90 хв.";
        int suggested=Math.max(150,Math.min(350,deficit>0?Math.max(150,deficit/2):quick(c,1)));
        if(s==ORANGE)return "План: приблизно "+suggested+" мл протягом наступних 30–45 хв, потім перевіримо темп ще раз.";
        return "План: почни з приблизно "+suggested+" мл зараз/найближчим часом і вирівнюй дефіцит поступово, не залпом.";
    }

    public static void markReminderShown(Context c){
        SharedPreferences p=prefs(c);
        int next=(p.getInt(K_WARNING_INDEX,0)+1)%3;
        p.edit().putLong(K_LAST_REMINDER,System.currentTimeMillis()).putInt(K_WARNING_INDEX,next).apply();
    }

    public static long reminderIntervalMs(Context c){
        int s=status(c);
        return s==RED?25*60_000L:s==ORANGE?45*60_000L:75*60_000L;
    }

    public static boolean reminderDue(Context c){
        if(!active(c)||status(c)==GREEN)return false;
        long last=lastReminderTime(c);
        return System.currentTimeMillis()-last>=reminderIntervalMs(c);
    }

    public static String warning(Context c){
        String[] orange={
                "Ти нижче свого темпу. Додай воду протягом наступної години, без залпового «наздоганяння».",
                "Темп просів. Краще кілька нормальних ковтків зараз і ще трохи пізніше.",
                "Води менше, ніж очікувалось на цей момент. Поступово повернись до темпу."
        };
        String[] red={
                "Споживання води сильно відстає від твого плану. Це може супроводжуватись втомою, сухістю в роті або головним болем.",
                "Ти суттєво нижче свого темпу. Недостатнє пиття може погіршувати концентрацію та самопочуття — пий поступово.",
                "Води мало для поточного моменту дня. Не намагайся випити весь дефіцит одразу; краще вирівняй темп поступово."
        };
        if(status(c)==GREEN)return "Все в темпі. Просто продовжуй так само.";
        int idx=Math.floorMod(prefs(c).getInt(K_WARNING_INDEX,0),3);
        return status(c)==ORANGE?orange[idx]:red[idx];
    }

    public static List<long[]> drinkPoints(Context c){
        List<long[]> out=new ArrayList<>();
        long wake=wakeTime(c);
        out.add(new long[]{wake,0});
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return out;

        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;
            boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] p=line.split(",");
                if(p.length<4)continue;
                long ts=parseTimestamp(p[0]);
                if(ts<=0||ts<wake)continue;
                if("DRINK".equals(p[1])||"UNDO".equals(p[1])){
                    out.add(new long[]{ts,Integer.parseInt(p[3])});
                }
            }
        }catch(Exception ignored){}
        return out;
    }

    public static List<long[]> effectiveDrinks(Context c){
        List<long[]> stack=new ArrayList<>();
        long wake=wakeTime(c);
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return stack;

        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;
            boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] p=line.split(",");
                if(p.length<4)continue;
                long ts=parseTimestamp(p[0]);
                if(ts<=0||ts<wake)continue;
                if("DRINK".equals(p[1])){
                    stack.add(new long[]{ts,Integer.parseInt(p[2])});
                }else if("UNDO".equals(p[1])&&!stack.isEmpty()){
                    stack.remove(stack.size()-1);
                }
            }
        }catch(Exception ignored){}
        return stack;
    }

    public static long longestGapMinutes(Context c){
        List<long[]> drinks=effectiveDrinks(c);
        long prev=wakeTime(c),max=0;
        for(long[] d:drinks){
            max=Math.max(max,d[0]-prev);
            prev=d[0];
        }
        max=Math.max(max,System.currentTimeMillis()-prev);
        return Math.max(0,max/60_000L);
    }

    public static synchronized boolean deleteDrink(Context c,long timestamp){
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return false;

        List<String[]> rows=new ArrayList<>();
        boolean removed=false;

        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;
            boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] p=line.split(",");
                if(p.length<4)continue;
                long ts=parseTimestamp(p[0]);
                if(!removed&&ts==timestamp&&"DRINK".equals(p[1])){
                    removed=true;
                    continue;
                }
                rows.add(new String[]{p[0],p[1],p[2],p[3]});
            }
        }catch(Exception e){
            DiagnosticStore.log(c,"WATER_DELETE_READ_ERROR",e.toString());
            return false;
        }

        if(!removed)return false;

        int running=0;
        try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f,false),StandardCharsets.UTF_8))){
            w.write("timestamp,event,amount_ml,total_ml\n");
            for(String[] row:rows){
                String event=row[1];
                int amount=0;
                try{amount=Integer.parseInt(row[2]);}catch(Exception ignored){}

                if("WAKE".equals(event))running=0;
                else if("DRINK".equals(event))running=Math.max(0,running+Math.max(0,amount));
                else if("UNDO".equals(event))running=Math.max(0,running+Math.min(0,amount));

                w.write(row[0]+","+event+","+amount+","+running+"\n");
            }
        }catch(Exception e){
            DiagnosticStore.log(c,"WATER_DELETE_WRITE_ERROR",e.toString());
            return false;
        }

        if(active(c)){
            List<long[]> effective=effectiveDrinks(c);
            int newTotal=0;
            long last=0L;
            for(long[] d:effective){
                newTotal+=Math.max(0,(int)d[1]);
                last=d[0];
            }
            prefs(c).edit()
                    .putInt(K_TOTAL,newTotal)
                    .putLong(K_LAST_TS,last)
                    .putBoolean(K_UNDO_VALID,false)
                    .putLong(K_LAST_REMINDER,System.currentTimeMillis())
                    .apply();
            NotificationHelper.clearWaterReminder(c);
            WaterScheduler.scheduleNext(c,30*60_000L);
        }

        DiagnosticStore.log(c,"WATER_DRINK_DELETED","timestamp="+timestamp);
        return true;
    }

    private static long parseTimestamp(String raw){
        if(raw==null||raw.isEmpty())return -1L;
        try{return Long.parseLong(raw.trim());}catch(Exception ignored){}
        try{
            SimpleDateFormat old=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
            Date d=old.parse(raw.trim());
            return d==null?-1L:d.getTime();
        }catch(ParseException ignored){}
        return -1L;
    }

    private static void log(Context c,String event,int amount,int total){
        try{
            File f=new File(c.getFilesDir(),FILE);
            boolean fresh=!f.exists();
            try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f,true),StandardCharsets.UTF_8))){
                if(fresh)w.write("timestamp,event,amount_ml,total_ml\n");
                w.write(System.currentTimeMillis()+","+event+","+amount+","+total+"\n");
            }
        }catch(Exception ignored){}
    }

    public static String recent(Context c,int max){
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return "Поки порожньо";
        List<String> lines=new ArrayList<>();

        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;
            boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] p=line.split(",");
                if(p.length>=4&&("DRINK".equals(p[1])||"UNDO".equals(p[1])))lines.add(line);
            }
        }catch(Exception e){
            return "Не вдалося прочитати історію";
        }

        if(lines.isEmpty())return "Поки порожньо";
        int start=Math.max(0,lines.size()-max);
        List<String> tail=new ArrayList<>(lines.subList(start,lines.size()));
        Collections.reverse(tail);

        StringBuilder b=new StringBuilder();
        SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.getDefault());
        for(String line:tail){
            String[] p=line.split(",");
            if(p.length>=4){
                long ts=parseTimestamp(p[0]);
                if(ts>0)b.append(fmt.format(new Date(ts))).append("  •  ");else b.append("• ");
                if("DRINK".equals(p[1]))b.append("+").append(p[2]).append(" мл");
                else b.append("скасовано ").append(Math.abs(Integer.parseInt(p[2]))).append(" мл");
                b.append("\n");
            }
        }
        return b.toString().trim();
    }
}
