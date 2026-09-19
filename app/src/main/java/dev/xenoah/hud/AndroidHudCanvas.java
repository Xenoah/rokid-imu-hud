package dev.xenoah.hud;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import dev.xenoah.hud.core.HudCanvas;

final class AndroidHudCanvas implements HudCanvas {
    private Canvas canvas;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    AndroidHudCanvas(){paint.setTypeface(Typeface.MONOSPACE);paint.setStrokeCap(Paint.Cap.ROUND);}
    void attach(Canvas c){canvas=c;}
    private void style(float width,int alpha,boolean fill){
        paint.setColor(Color.GREEN);paint.setAlpha(alpha);paint.setStrokeWidth(width);
        paint.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);
    }
    public void clear(){canvas.drawColor(Color.BLACK);}
    public void save(){canvas.save();}
    public void restore(){canvas.restore();}
    public void translate(float x,float y){canvas.translate(x,y);}
    public void scale(float factor){canvas.scale(factor,factor);}
    public void rotate(float degrees){canvas.rotate(degrees);}
    public void clip(float l,float t,float r,float b){canvas.clipRect(l,t,r,b);}
    public void line(float x1,float y1,float x2,float y2,float w,int a){style(w,a,false);canvas.drawLine(x1,y1,x2,y2,paint);}
    public void circle(float x,float y,float r,float w,int a,boolean fill){style(w,a,fill);canvas.drawCircle(x,y,r,paint);}
    public void text(String s,float x,float y,float size,int a){style(1,a,true);paint.setTextSize(size);canvas.drawText(s,x,y,paint);}
    public void text(char[] s,int len,float x,float y,float size,int a){style(1,a,true);paint.setTextSize(size);canvas.drawText(s,0,len,x,y,paint);}
}
