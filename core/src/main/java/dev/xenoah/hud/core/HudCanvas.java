package dev.xenoah.hud.core;

/** One renderer shared by the Android app and desktop visual verification. Coordinates are px. */
public interface HudCanvas {
    void clear();
    void save();
    void restore();
    void translate(float x,float y);
    void scale(float factor);
    void rotate(float degrees);
    void clip(float left,float top,float right,float bottom);
    void line(float x1,float y1,float x2,float y2,float width,int alpha);
    void circle(float x,float y,float radius,float width,int alpha,boolean fill);
    void text(String text,float x,float baseline,float size,int alpha);
    void text(char[] text,int length,float x,float baseline,float size,int alpha);
}
