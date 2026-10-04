package com.mealcoach.app;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ExportStore {
    private ExportStore(){}

    public static boolean exportAll(Context c,Uri destination){
        String[] names={"meal_log.csv","water_log.csv","day_summaries.csv","mealcoach_debug.log"};
        try(OutputStream raw=c.getContentResolver().openOutputStream(destination,"w");
            ZipOutputStream zip=new ZipOutputStream(raw)){
            byte[] buf=new byte[8192];
            for(String name:names){
                File f=new File(c.getFilesDir(),name);
                if(!f.exists())continue;
                zip.putNextEntry(new ZipEntry(name));
                try(FileInputStream in=new FileInputStream(f)){
                    int n;while((n=in.read(buf))>0)zip.write(buf,0,n);
                }
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("README.txt"));
            String meta="десятий v4\nmeal_log.csv — їжа/фото\nwater_log.csv — вода\nday_summaries.csv — wake-дні\nmealcoach_debug.log — технічна діагностика\n";
            zip.write(meta.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return true;
        }catch(Exception e){
            DiagnosticStore.log(c,"EXPORT_ALL_ERROR",e.toString());
            return false;
        }
    }
}
