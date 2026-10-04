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

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}

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
        float radius=dp(14);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF10161D);
        c.drawRoundRect(tank,radius,radius,p);

        float ratio=displayRatio;
        float top=tank.bottom-(tank.height()*ratio);
        p.setColor(status==HydrationEngine.GREEN?0xFF59D1B5:status==HydrationEngine.ORANGE?0xFFFF9F43:0xFFFF6B81);
        c.drawRoundRect(new RectF(tank.left,top,tank.right,tank.bottom),radius,radius,p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0xFF3A4654);
        c.drawRoundRect(tank,radius,radius,p);

        float er=Math.min(1f,expected/(float)goal);
        float ey=tank.bottom-tank.height()*er;
        p.setStrokeWidth(dp(1.5f));
        p.setColor(0xFFF2F5F7);
        c.drawLine(tank.left-dp(5),ey,tank.right+dp(5),ey,p);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sp(17));
        p.setColor(0xFFF2F5F7);
        Paint.FontMetrics fm=p.getFontMetrics();
        float centered=h/2f-(fm.ascent+fm.descent)/2f;
        c.drawText(Math.round(ratio*100)+"%",w/2f,centered,p);
    }
}
