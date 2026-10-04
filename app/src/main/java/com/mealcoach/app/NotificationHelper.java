package com.mealcoach.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;

public final class NotificationHelper {
    public static final String CH_PREP="meal_prep_v4";
    public static final String CH_ALARM="meal_alarm_v4";
    public static final String CH_TEST="test_v4";
    public static final int REMINDER_ID=501;
    public static final int WATER_ID=601;
    public static final int EATING_ID=701;
    public static final int SERVICE_ID=900;

    private NotificationHelper(){}

    private static Uri foodUri(Context c){
        String custom=AppSettings.foodSound(c);
        if(custom!=null&&!custom.isEmpty())return Uri.parse(custom);
        return Uri.parse("android.resource://"+c.getPackageName()+"/raw/food");
    }
    private static Uri waterUri(Context c){
        String custom=AppSettings.waterSound(c);
        if(custom!=null&&!custom.isEmpty())return Uri.parse(custom);
        return Uri.parse("android.resource://"+c.getPackageName()+"/raw/water");
    }
    private static String foodCh(Context c,boolean urgent){
        return (urgent?"food_urgent_v4_":"food_v4_")+Math.abs(foodUri(c).toString().hashCode());
    }
    private static String eatingCh(Context c){return "eating_v4_"+Math.abs(foodUri(c).toString().hashCode());}
    private static String waterCh(Context c){return "water_v4_"+Math.abs(waterUri(c).toString().hashCode());}

    public static void ensureChannels(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        AudioAttributes notif=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        AudioAttributes alarmAttrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();

        NotificationChannel prep=new NotificationChannel(CH_PREP,"Підготовка до їжі",NotificationManager.IMPORTANCE_DEFAULT);
        prep.setSound(null,null);prep.enableVibration(false);

        NotificationChannel food=new NotificationChannel(foodCh(c,false),"Їжа",NotificationManager.IMPORTANCE_HIGH);
        food.setSound(foodUri(c),notif);food.enableVibration(true);food.setVibrationPattern(new long[]{0,180,120,180});

        NotificationChannel urgent=new NotificationChannel(foodCh(c,true),"Прострочена їжа",NotificationManager.IMPORTANCE_HIGH);
        urgent.setSound(foodUri(c),notif);urgent.enableVibration(true);urgent.setVibrationPattern(new long[]{0,350,180,350,180,700});

        NotificationChannel eating=new NotificationChannel(eatingCh(c),"Тривалість прийому їжі",NotificationManager.IMPORTANCE_HIGH);
        eating.setSound(foodUri(c),notif);eating.enableVibration(true);

        NotificationChannel water=new NotificationChannel(waterCh(c),"Вода",NotificationManager.IMPORTANCE_HIGH);
        water.setSound(waterUri(c),notif);water.enableVibration(true);water.setVibrationPattern(new long[]{0,130});

        NotificationChannel alarm=new NotificationChannel(CH_ALARM,"Критичний сигнал їжі",NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI,alarmAttrs);alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0,700,300,700,300});

        NotificationChannel test=new NotificationChannel(CH_TEST,"Тест нагадувань",NotificationManager.IMPORTANCE_HIGH);
        test.enableVibration(true);

        nm.createNotificationChannel(prep);nm.createNotificationChannel(food);nm.createNotificationChannel(urgent);
        nm.createNotificationChannel(eating);nm.createNotificationChannel(water);nm.createNotificationChannel(alarm);nm.createNotificationChannel(test);
    }

    public static void showReminder(Context c,int type){
        ensureChannels(c);
        String title,body,channel;boolean ongoing=false;
        switch(type){
            case AlarmScheduler.PREP_60:
                title="Через годину — їжа";
                body="Починай планувати: що їстимеш і чи треба щось приготувати.";
                channel=CH_PREP;break;
            case AlarmScheduler.PREP_20:
                title="Через 20 хв — їжа";
                body="Не починай нову катку. Відкладай справи й готуй їжу.";
                channel=CH_PREP;break;
            case AlarmScheduler.PREFERRED:
                title="Пора їсти";
                body="Сфотографуй їжу й починай прийом.";
                channel=foodCh(c,false);break;
            case AlarmScheduler.LATE_15:
                title="Ти вже пропустив 15 хв";
                body="Їжа вже прострочена. Наступний рівень буде жорсткішим.";
                channel=foodCh(c,true);ongoing=true;break;
            case AlarmScheduler.LATE_45:
                title="+45 хв: штраф активний";
                body="Соцмережі заблоковані до завершення прийому їжі. Далі — фінальний alarm.";
                channel=foodCh(c,true);ongoing=true;break;
            default:return;
        }

        Notification.Builder b=new Notification.Builder(c,channel)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(false).setOngoing(ongoing).setOnlyAlertOnce(false)
                .setPriority(Notification.PRIORITY_HIGH).setCategory(Notification.CATEGORY_REMINDER)
                .setColor(type>=AlarmScheduler.LATE_15?Color.rgb(255,107,129):Color.rgb(89,209,181))
                .setContentIntent(openApp(c));

        if(type>=AlarmScheduler.PREFERRED)b.addAction(new Notification.Action.Builder(null,"ЇСТИ → ФОТО",startMeal(c,611)).build());
        if(type>=AlarmScheduler.PREFERRED&&type<AlarmScheduler.LATE_45)b.addAction(new Notification.Action.Builder(null,"+15 ХВ",action(c,"DELAY",612)).build());

        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null){nm.cancel(REMINDER_ID);nm.notify(REMINDER_ID,b.build());}
        DiagnosticStore.log(c,"MEAL_NOTIFICATION_SHOWN","type="+type);
    }

    public static void showEatingCheck(Context c){
        ensureChannels(c);
        Notification n=new Notification.Builder(c,eatingCh(c)).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Ти ще їси?")
                .setContentText("Через 10 хв прийом завершиться автоматично.")
                .setStyle(new Notification.BigTextStyle().bigText("Якщо вже закінчив — натисни «Закінчив». Якщо ще їси — продовж на 10 хв."))
                .setPriority(Notification.PRIORITY_HIGH).setAutoCancel(false).setContentIntent(openApp(c))
                .addAction(new Notification.Action.Builder(null,"ЗАКІНЧИВ",action(c,"ATE",721)).build())
                .addAction(new Notification.Action.Builder(null,"ЩЕ ЇМ +10",action(c,"EXTEND_EATING",722)).build()).build();
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.notify(EATING_ID,n);
    }

    public static void showWaterReminder(Context c,int status){
        ensureChannels(c);
        if(status==HydrationEngine.GREEN)return;
        String title=status==HydrationEngine.RED?"Мало води":"Трохи не встигаєш по воді";
        String body=HydrationEngine.warning(c);
        int color=status==HydrationEngine.RED?Color.rgb(255,107,129):Color.rgb(255,159,67);
        Notification n=new Notification.Builder(c,waterCh(c)).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setPriority(Notification.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(openWater(c))
                .setColor(color)
                .addAction(new Notification.Action.Builder(null,"+"+HydrationEngine.quick(c,1)+" МЛ",waterAdd(c,HydrationEngine.quick(c,1),751)).build())
                .addAction(new Notification.Action.Builder(null,"ВІДКРИТИ",openWater(c)).build()).build();
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null){nm.cancel(WATER_ID);nm.notify(WATER_ID,n);}
        DiagnosticStore.log(c,"WATER_NOTIFICATION_SHOWN","status="+status);
    }

    public static void showTestStage(Context c,int stage){
        ensureChannels(c);
        String[] names={"","−60 хв","−20 хв","Пора їсти","+15 хв","+30 хв fullscreen","+45 хв + блок","FINAL alarm"};
        int s=Math.max(1,Math.min(7,stage));
        String body=names[s]+" • подія реально доставлена Android.";
        Notification n=new Notification.Builder(c,CH_TEST).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("ТЕСТ · етап "+s+"/7").setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setPriority(Notification.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(openSettings(c)).build();
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.notify(820+s,n);
    }

    public static Notification buildEscalationNotification(Context c,String mode){
        ensureChannels(c);
        boolean finalMode=AlarmService.MODE_FINAL.equals(mode);
        Intent full=new Intent(c,finalMode?AlarmActivity.class:ConfirmActivity.class);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,finalMode?730:720,full,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String title=finalMode?"МАКСИМАЛЬНА МЕЖА":"ТИ ПРОПУСТИВ ЩЕ 30 ХВ";
        String body=finalMode
                ?"Сигнал триватиме, доки ти не сфотографуєш їжу і не почнеш прийом."
                :"Звук поступово посилюється. Введи «ПІДТВЕРДЖУЮ». Наступний рівень активує блок соцмереж.";
        return new Notification.Builder(c,CH_ALARM).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true).setAutoCancel(false).setColor(Color.rgb(255,107,129))
                .setContentIntent(pi).setFullScreenIntent(pi,true).build();
    }

    public static void clearWaterReminder(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.cancel(WATER_ID);
    }

    public static void clearReminder(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.cancel(REMINDER_ID);
    }

    private static PendingIntent openApp(Context c){
        Intent i=new Intent(c,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,700,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent openSettings(Context c){
        Intent i=new Intent(c,MainActivity.class);i.putExtra("open_settings",true);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,702,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent openWater(Context c){
        Intent i=new Intent(c,MainActivity.class);i.putExtra("open_tab",1);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,701,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent startMeal(Context c,int req){
        Intent i=new Intent(c,MainActivity.class);i.putExtra("force_camera",true);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent waterAdd(Context c,int amount,int req){
        Intent i=new Intent(c,ActionReceiver.class);
        i.setAction("com.mealcoach.WATER_ADD");
        i.putExtra("amount",amount);
        return PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent action(Context c,String action,int req){
        Intent i=new Intent(c,ActionReceiver.class);i.setAction("com.mealcoach."+action);
        return PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
