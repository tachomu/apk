package com.mealcoach.app;

import android.content.Context;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DaySummaryStore {
    private static final String FILE="day_summaries.csv";
    private DaySummaryStore(){}

    public static synchronized void closeDay(Context c,long wake,long sleep,int meals,int snacks,int water,int goal,int lateMeals,int autoMeals){
        try{
            File f=new File(c.getFilesDir(),FILE);
            boolean fresh=!f.exists();
            try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f,true),StandardCharsets.UTF_8))){
                if(fresh)w.write("wake,sleep,meals,snacks,water,goal,late_meals,auto_meals\n");
                w.write(wake+","+sleep+","+meals+","+snacks+","+water+","+goal+","+lateMeals+","+autoMeals+"\n");
            }
        }catch(Exception ignored){}
    }

    public static String stats(Context c){
        File f=new File(c.getFilesDir(),FILE);
        if(!f.exists())return "Ще немає завершених wake-днів.";
        List<String[]> rows=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line; boolean first=true;
            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                String[] p=line.split(",");
                if(p.length>=8)rows.add(p);
            }
        }catch(Exception e){return "Не вдалося прочитати статистику.";}
        if(rows.isEmpty())return "Ще немає завершених wake-днів.";
        int from=Math.max(0,rows.size()-7), n=rows.size()-from;
        int meals=0,water=0,goal=0,late=0,auto=0,hitFood=0,hitWater=0;
        long awake=0;
        for(int i=from;i<rows.size();i++){
            String[] p=rows.get(i);
            try{
                long w=Long.parseLong(p[0]),s=Long.parseLong(p[1]);
                int m=Integer.parseInt(p[2]),wa=Integer.parseInt(p[4]),g=Integer.parseInt(p[5]),l=Integer.parseInt(p[6]),a=Integer.parseInt(p[7]);
                awake+=Math.max(0,s-w);meals+=m;water+=wa;goal+=g;late+=l;auto+=a;
                if(m>=3)hitFood++;if(wa>=g)hitWater++;
            }catch(Exception ignored){}
        }
        long avgAwake=n>0?awake/n:0;
        return "ОСТАННІ "+n+" WAKE-ДНІВ\n"+
                "Їжа: "+hitFood+"/"+n+" днів із 3+ прийомами • середнє "+round1(meals/(double)n)+"\n"+
                "Вода: "+hitWater+"/"+n+" днів досягнута ціль • середнє "+(water/n)+" мл\n"+
                "Прострочені прийоми: "+late+" • автозавершення: "+auto+"\n"+
                "Середній активний день: "+formatHours(avgAwake);
    }

    private static String round1(double v){return String.format(Locale.getDefault(),"%.1f",v);}
    private static String formatHours(long ms){
        long min=ms/60_000L;return (min/60)+" год "+(min%60)+" хв";
    }
}
