package com.mealcoach.app;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticStore {
    private static final String FILE="desyatyi_debug.log";
    private static final String LEGACY_FILE="mealcoach_debug.log";

    private DiagnosticStore(){}

    private static File file(Context c){
        File current=new File(c.getFilesDir(),FILE);
        if(!current.exists()){
            File legacy=new File(c.getFilesDir(),LEGACY_FILE);
            if(legacy.exists()){
                try{
                    if(!legacy.renameTo(current)){
                        try(FileInputStream in=new FileInputStream(legacy);
                            FileOutputStream out=new FileOutputStream(current)){
                            byte[] buf=new byte[8192];
                            int n;
                            while((n=in.read(buf))>0)out.write(buf,0,n);
                        }
                    }
                }catch(Exception ignored){}
            }
        }
        return current;
    }

    public static synchronized void log(Context context,String event,String details){
        try{
            File file=file(context);
            try(BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file,true),StandardCharsets.UTF_8))){
                String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());
                writer.write(ts+" | "+event+" | "+(details==null?"":details)+"\n");
            }
        }catch(Exception ignored){}
    }

    public static boolean copyTo(Context context,Uri destination){
        File file=file(context);
        if(!file.exists())return false;
        try(FileInputStream in=new FileInputStream(file);
            OutputStream out=context.getContentResolver().openOutputStream(destination,"w")){
            if(out==null)return false;
            byte[] buf=new byte[8192];
            int n;
            while((n=in.read(buf))>0)out.write(buf,0,n);
            out.flush();
            return true;
        }catch(Exception e){
            return false;
        }
    }
}
