package com.mealcoach.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;

public final class SoundStore {
    private SoundStore(){}

    public static String importForNotifications(Context c,Uri source,String kind){
        if(source==null)return "";
        if(Build.VERSION.SDK_INT<29)return source.toString();

        ContentResolver r=c.getContentResolver();
        ContentValues v=new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME,"desyatyi_"+kind+"_"+System.currentTimeMillis()+".audio");
        v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/*");
        v.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_NOTIFICATIONS+"/Desyatyi");
        v.put(MediaStore.Audio.Media.IS_NOTIFICATION,1);
        v.put(MediaStore.Audio.Media.IS_PENDING,1);

        Uri dest=null;
        try{
            dest=r.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);
            if(dest==null)return source.toString();

            try(InputStream in=r.openInputStream(source);OutputStream out=r.openOutputStream(dest,"w")){
                if(in==null||out==null)throw new IllegalStateException("sound stream unavailable");
                byte[] buf=new byte[8192];
                int n;
                while((n=in.read(buf))>0)out.write(buf,0,n);
                out.flush();
            }

            ContentValues done=new ContentValues();
            done.put(MediaStore.Audio.Media.IS_PENDING,0);
            r.update(dest,done,null,null);
            return dest.toString();
        }catch(Exception e){
            DiagnosticStore.log(c,"SOUND_IMPORT_ERROR",kind+" | "+e);
            if(dest!=null){
                try{r.delete(dest,null,null);}catch(Exception ignored){}
            }
            return source.toString();
        }
    }
}
