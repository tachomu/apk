package com.mealcoach.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class StreakSceneView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int days=0;

    public StreakSceneView(Context c){super(c);}
    public StreakSceneView(Context c,AttributeSet a){super(c,a);}
    public StreakSceneView(Context c,AttributeSet a,int s){super(c,a,s);}

    public void setDays(int d){days=Math.max(0,d);invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        p.setColor(0xFF111821);c.drawRect(0,0,w,h,p);
        p.setColor(0xFF1C2631);c.drawRect(0,h*0.63f,w,h,p);
        p.setColor(0xFF273442);c.drawRect(0,h*0.61f,w,h*0.64f,p);

        // window
        p.setColor(0xFF0B1016);c.drawRoundRect(new RectF(w*0.68f,h*0.12f,w*0.92f,h*0.46f),12,12,p);
        p.setColor(days>=60?0xFF7867E8:0xFF24425C);c.drawRect(w*0.705f,h*0.16f,w*0.885f,h*0.42f,p);

        if(days>=1){
            // lamp
            p.setColor(0xFFFFD56A);c.drawCircle(w*0.18f,h*0.34f,18,p);
            p.setStrokeWidth(8);c.drawLine(w*0.18f,h*0.36f,w*0.18f,h*0.65f,p);
        }
        if(days>=3){
            // desk
            p.setColor(0xFF845D42);c.drawRoundRect(new RectF(w*0.23f,h*0.61f,w*0.77f,h*0.70f),12,12,p);
            c.drawRect(w*0.28f,h*0.69f,w*0.32f,h*0.91f,p);c.drawRect(w*0.68f,h*0.69f,w*0.72f,h*0.91f,p);
        }
        if(days>=7){
            // monitor
            p.setColor(0xFF070A0D);c.drawRoundRect(new RectF(w*0.39f,h*0.34f,w*0.62f,h*0.59f),10,10,p);
            p.setColor(0xFF59D1B5);c.drawRoundRect(new RectF(w*0.415f,h*0.37f,w*0.595f,h*0.555f),6,6,p);
            p.setColor(0xFF303A45);c.drawRect(w*0.49f,h*0.59f,w*0.52f,h*0.64f,p);
        }
        if(days>=14){
            // plant
            p.setColor(0xFF6F4E37);c.drawRoundRect(new RectF(w*0.78f,h*0.56f,w*0.88f,h*0.69f),8,8,p);
            p.setColor(0xFF56B870);c.drawOval(new RectF(w*0.75f,h*0.43f,w*0.83f,h*0.59f),p);
            c.drawOval(new RectF(w*0.83f,h*0.40f,w*0.91f,h*0.58f),p);
        }
        if(days>=30){
            // chair
            p.setColor(0xFF3A4654);c.drawRoundRect(new RectF(w*0.43f,h*0.70f,w*0.60f,h*0.95f),20,20,p);
            c.drawRoundRect(new RectF(w*0.39f,h*0.82f,w*0.64f,h*0.90f),18,18,p);
        }
        if(days>=60){
            // neon strip
            p.setColor(0xFF9B7BFF);c.drawRoundRect(new RectF(w*0.08f,h*0.08f,w*0.55f,h*0.105f),8,8,p);
        }
        if(days>=100){
            // trophy
            p.setColor(0xFFFFD166);c.drawCircle(w*0.10f,h*0.56f,24,p);c.drawRect(w*0.09f,h*0.58f,w*0.11f,h*0.66f,p);
        }

        if(days==0){
            p.setColor(0xFF94A1AE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(34);
            c.drawText("Почни будувати серію",w/2,h/2,p);
        }
    }
}
