package com.mealcoach.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class WaterTimelineView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<long[]> points=new ArrayList<>();
    private long wake=0,end=1;
    private int goal=2500;

    public WaterTimelineView(Context c){super(c);}
    public WaterTimelineView(Context c,AttributeSet a){super(c,a);}
    public WaterTimelineView(Context c,AttributeSet a,int s){super(c,a,s);}

    public void setData(List<long[]> pts,long wake,long end,int goal){
        this.points=pts==null?new ArrayList<>():pts;
        this.wake=wake;
        this.end=Math.max(wake+1,end);
        this.goal=Math.max(1,goal);
        invalidate();
    }

    private float x(long t,float left,float right){
        double r=(t-wake)/(double)(end-wake);
        return (float)(left+Math.max(0,Math.min(1,r))*(right-left));
    }
    private float y(int ml,float top,float bottom){
        double r=ml/(double)goal;
        return (float)(bottom-Math.max(0,Math.min(1,r))*(bottom-top));
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float left=24,top=18,right=getWidth()-18,bottom=getHeight()-28;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0xFF27303A);
        c.drawLine(left,bottom,right,bottom,p);
        c.drawLine(left,top,left,bottom,p);

        p.setColor(0xFF596675);
        p.setStrokeWidth(2);
        c.drawLine(left,bottom,right,top,p);

        if(!points.isEmpty()){
            Path path=new Path();
            int i=0;
            for(long[] pt:points){
                float px=x(pt[0],left,right);
                float py=y((int)pt[1],top,bottom);
                if(i++==0)path.moveTo(px,py);else path.lineTo(px,py);
            }
            p.setColor(0xFF59D1B5);
            p.setStrokeWidth(5);
            c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);
            for(long[] pt:points)c.drawCircle(x(pt[0],left,right),y((int)pt[1],top,bottom),7,p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setTextSize(22);
        p.setColor(0xFF94A1AE);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("wake",left,bottom+23,p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("sleep≈",right,bottom+23,p);
    }
}
