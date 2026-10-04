package com.mealcoach.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class HydrationTankView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int total=0,goal=2500,status=HydrationEngine.GREEN,expected=0;
    private float displayRatio=0f;

    public HydrationTankView(Context c){super(c);}
    public HydrationTankView(Context c,AttributeSet a){super(c,a);}
    public HydrationTankView(Context c,AttributeSet a,int s){super(c,a,s);}

    public void setData(int total,int goal,int expected,int status){
        int safeGoal=Math.max(1,goal);
        float target=Math.min(1f,Math.max(0,total)/(float)safeGoal);
        float start=displayRatio;
        this.total=Math.max(0,total);
        this.goal=safeGoal;
        this.expected=Math.max(0,expected);
        this.status=status;
        if(Math.abs(target-start)<0.005f){
            displayRatio=target;
            invalidate();
            return;
        }
        ValueAnimator a=ValueAnimator.ofFloat(start,target);
        a.setDuration(420);
        a.addUpdateListener(v->{displayRatio=(float)v.getAnimatedValue();invalidate();});
        a.start();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        RectF tank=new RectF(w*0.12f,h*0.05f,w*0.88f,h*0.95f);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF10161D);
        c.drawRoundRect(tank,28,28,p);

        float ratio=displayRatio;
        float top=tank.bottom-(tank.height()*ratio);
        p.setColor(status==HydrationEngine.GREEN?0xFF59D1B5:status==HydrationEngine.ORANGE?0xFFFF9F43:0xFFFF6B81);
        c.drawRoundRect(new RectF(tank.left,top,tank.right,tank.bottom),28,28,p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setColor(0xFF3A4654);
        c.drawRoundRect(tank,28,28,p);

        float er=Math.min(1f,expected/(float)goal);
        float ey=tank.bottom-tank.height()*er;
        p.setStrokeWidth(3);
        p.setColor(0xFFF2F5F7);
        c.drawLine(tank.left-10,ey,tank.right+10,ey,p);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(28);
        p.setColor(0xFFF2F5F7);
        c.drawText(Math.round(ratio*100)+"%",w/2,h/2+10,p);
    }
}
