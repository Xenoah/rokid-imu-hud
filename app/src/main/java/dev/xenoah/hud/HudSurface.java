package dev.xenoah.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import dev.xenoah.hud.core.*;

final class HudSurface extends SurfaceView implements SurfaceHolder.Callback {
    private final HandlerThread thread=new HandlerThread("hud-render");
    private final Handler handler;
    private final Exchange exchange;
    private final Snapshot snapshot=new Snapshot();
    private final AndroidHudCanvas adapter=new AndroidHudCanvas();
    private final HudRenderer renderer=new HudRenderer();
    private final boolean debug;
    private final Object surfaceLock=new Object();
    private Choreographer choreographer;
    private volatile boolean resumed,surfaceReady,closed;
    private boolean scheduled;
    private volatile int surfaceWidth=480,surfaceHeight=400;
    private long nextFrame,statsAt,totalDrawNs,maxDrawNs;
    private int frames;
    HudSurface(Context context,Exchange exchange,boolean debug){
        super(context);this.exchange=exchange;this.debug=debug;
        getHolder().addCallback(this);setFocusable(false);
        thread.start();handler=new Handler(thread.getLooper());
        handler.post(()->choreographer=Choreographer.getInstance());
    }
    void resume(){resumed=true;handler.post(this::schedule);}
    void pause(){resumed=false;handler.post(this::cancel);}
    void close(){closed=true;resumed=false;handler.post(()->{cancel();thread.quitSafely();});}
    private void schedule(){if(resumed&&surfaceReady&&!closed&&!scheduled){scheduled=true;choreographer.postFrameCallback(frame);}}
    private void cancel(){if(scheduled){choreographer.removeFrameCallback(frame);scheduled=false;}nextFrame=0;}
    private final Choreographer.FrameCallback frame=new Choreographer.FrameCallback(){public void doFrame(long vsync){
        scheduled=false;
        if(!resumed||!surfaceReady||closed)return;
        if(vsync<nextFrame){schedule();return;}
        nextFrame=Math.max(nextFrame+1_000_000_000L/Config.TARGET_FPS,vsync);
        long begin=SystemClock.elapsedRealtimeNanos();
        synchronized(surfaceLock){
            if(!surfaceReady)return;
            Canvas canvas=null;
            try{
                canvas=getHolder().lockHardwareCanvas();
                if(canvas!=null){
                    exchange.read(snapshot);adapter.attach(canvas);
                    renderer.draw(adapter,snapshot,begin,surfaceWidth,surfaceHeight,debug);
                }
            }catch(IllegalStateException|IllegalArgumentException e){Log.w(Diagnostics.TAG,"Surface changed during draw",e);}
            finally{
                if(canvas!=null)try{getHolder().unlockCanvasAndPost(canvas);}
                catch(IllegalStateException|IllegalArgumentException e){Log.w(Diagnostics.TAG,"Surface post failed",e);}
            }
        }
        long elapsed=SystemClock.elapsedRealtimeNanos()-begin;
        frames++;totalDrawNs+=elapsed;maxDrawNs=Math.max(maxDrawNs,elapsed);
        if(debug&&begin-statsAt>5_000_000_000L){
            if(statsAt!=0)Log.i(Diagnostics.TAG,"fps="+(frames*1e9/(begin-statsAt))+" drawMeanMs="+(totalDrawNs*1e-6/frames)+" drawMaxMs="+(maxDrawNs*1e-6));
            statsAt=begin;frames=0;totalDrawNs=0;maxDrawNs=0;
        }
        schedule();
    }};
    @Override public void surfaceCreated(SurfaceHolder holder){
        surfaceReady=true;
        holder.getSurface().setFrameRate(Config.TARGET_FPS,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        handler.post(this::schedule);
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
        surfaceWidth=width;surfaceHeight=height;
        Log.i(Diagnostics.TAG,"surface="+width+"x"+height+" refreshHz="+(getDisplay()==null?0:getDisplay().getRefreshRate()));
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder){
        synchronized(surfaceLock){surfaceReady=false;}
        handler.post(this::cancel);
    }
}
