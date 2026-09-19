package dev.xenoah.hud.core;

public final class Viewport {
    public float x,y,scale;
    public void fit(int width,int height) {
        // Official 480x640 display: preserve 80px top/bottom safe bands.
        float margin=height>=width*1.25f?80f*width/480f:0;
        scale=Math.min(width/480f,(height-2*margin)/400f);
        x=(width-480*scale)/2;y=(height-400*scale)/2;
    }
}
