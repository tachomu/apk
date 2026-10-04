package com.mealcoach.app;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ExportStore {
    private ExportStore(){}

    public static boolean exportAll(Context c,Uri destination){
        String[] names={"meal_log.csv","water_log.csv","day_summaries.csv","desyatyi_debug.log"};

        try(OutputStream raw=c.getContentResolver().openOutputStream(destination,"w");
            ZipOutputStream zip=new ZipOutputStream(raw)){

            if(raw==null)return false;
            byte[] buf=new byte[8192];

            for(String name:names){
                File f=new File(c.getFilesDir(),name);
                if(!f.exists())continue;
                zip.putNextEntry(new ZipEntry(name));
                try(FileInputStream in=new FileInputStream(f)){
                    int n;
                    while((n=in.read(buf))>0)zip.write(buf,0,n);
                }
                zip.closeEntry();
            }

            exportMealPhotos(c,zip,buf);

            zip.putNextEntry(new ZipEntry("streak_summary.txt"));
            String streak=
                    "current_days="+StreakEngine.days(c)+"\n"+
                    "best_days="+StreakEngine.best(c)+"\n"+
                    "clean_days_30="+StreakEngine.cleanDaysTracked(c)+"/"+StreakEngine.trackedWindow(c)+"\n"+
                    "rank="+StreakEngine.rank(c)+"\n\n"+
                    "archive:\n"+StreakEngine.archiveText(c,50)+"\n";
            zip.write(streak.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("README.txt"));
            String meta=
                    "десятий v4\n"+
                    "meal_log.csv — історія їжі, час, фото URI\n"+
                    "water_log.csv — окрема історія води\n"+
                    "day_summaries.csv — wake-дні\n"+
                    "desyatyi_debug.log — технічна діагностика нагадувань\n"+
                    "photos/ — реальні фото їжі для ручного аналізу в ChatGPT\n"+
                    "photo_index.csv — відповідність URI до файлу фото\n"+
                    "streak_summary.txt — серія та архів\n";
            zip.write(meta.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.finish();
            return true;
        }catch(Exception e){
            DiagnosticStore.log(c,"EXPORT_ALL_ERROR",e.toString());
            return false;
        }
    }

    private static void exportMealPhotos(Context c,ZipOutputStream zip,byte[] buf){
        File mealLog=new File(c.getFilesDir(),"meal_log.csv");
        if(!mealLog.exists())return;

        Set<String> seen=new HashSet<>();
        List<String> index=new ArrayList<>();
        index.add("zip_file,original_uri");

        try(BufferedReader r=new BufferedReader(new FileReader(mealLog))){
            String line;
            boolean first=true;
            int count=0;

            while((line=r.readLine())!=null){
                if(first){first=false;continue;}
                List<String> cols=parseCsv(line);
                if(cols.size()<6)continue;

                String uriText=cols.get(5).trim();
                if(uriText.isEmpty()||!seen.add(uriText))continue;

                Uri uri;
                try{uri=Uri.parse(uriText);}catch(Exception e){continue;}

                try(InputStream in=c.getContentResolver().openInputStream(uri)){
                    if(in==null)continue;
                    String fileName=String.format(java.util.Locale.US,"photos/meal_%03d.jpg",++count);
                    zip.putNextEntry(new ZipEntry(fileName));
                    int n;
                    while((n=in.read(buf))>0)zip.write(buf,0,n);
                    zip.closeEntry();
                    index.add(csv(fileName)+","+csv(uriText));
                }catch(Exception e){
                    DiagnosticStore.log(c,"PHOTO_EXPORT_ERROR",uriText+" | "+e);
                }
            }
        }catch(Exception e){
            DiagnosticStore.log(c,"PHOTO_INDEX_READ_ERROR",e.toString());
        }

        try{
            zip.putNextEntry(new ZipEntry("photo_index.csv"));
            for(String row:index){
                zip.write((row+"\n").getBytes(StandardCharsets.UTF_8));
            }
            zip.closeEntry();
        }catch(Exception e){
            DiagnosticStore.log(c,"PHOTO_INDEX_WRITE_ERROR",e.toString());
        }
    }

    private static List<String> parseCsv(String line){
        List<String> out=new ArrayList<>();
        StringBuilder cur=new StringBuilder();
        boolean quoted=false;

        for(int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if(ch=='"'){
                if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){
                    cur.append('"');
                    i++;
                }else{
                    quoted=!quoted;
                }
            }else if(ch==','&&!quoted){
                out.add(cur.toString());
                cur.setLength(0);
            }else{
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String csv(String s){
        if(s==null)s="";
        return "\""+s.replace("\"","\"\"")+"\"";
    }
}
