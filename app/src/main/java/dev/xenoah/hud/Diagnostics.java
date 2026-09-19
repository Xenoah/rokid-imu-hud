package dev.xenoah.hud;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import dev.xenoah.hud.core.Exchange;
import dev.xenoah.hud.core.Snapshot;

/** Optional bounded diagnostic CSV at 10 Hz, on its own I/O thread. */
final class Diagnostics {
    static final String TAG="RokidHUD";
    private final HandlerThread thread=new HandlerThread("hud-log");
    private final Exchange exchange;
    private final Snapshot snapshot=new Snapshot();
    private Handler handler;
    private BufferedWriter writer;
    private int rows;
    private volatile boolean closed;
    Diagnostics(Context context,Exchange exchange,boolean enabled) {
        this.exchange=exchange;
        Log.i(TAG,"model="+Build.MODEL+" sdk="+Build.VERSION.SDK_INT+" fingerprint="+Build.FINGERPRINT);
        if(!enabled)return;
        thread.start();handler=new Handler(thread.getLooper());
        File file=new File(context.getFilesDir(),"hud-debug.csv");
        handler.post(()->{
            try{
                writer=new BufferedWriter(new FileWriter(file,false));
                writer.write("elapsed_ns,pitch_deg,roll_deg,lat_g,long_g,peak_g,raw_lat_g,raw_long_g,acc_hz,gyro_hz,att_hz,acc_age_ms,att_age_ms,native,status,valid,ax,ay,az,gx,gy,gz,acc_bias_x,acc_bias_y,acc_bias_z,gyro_bias_x,gyro_bias_y,gyro_bias_z,cal_reason,cal_acc_std,cal_gyro_std,native_rejected,cal_pose_range_deg,cal_gyro_mean_rad_s,cal_raw_tilt_deg,cal_elapsed_s,cal_relaxed,cal_quality\n");
                handler.post(tick);
            }catch(IOException e){Log.e(TAG,"CSV open failed",e);}
        });
    }
    static void sensors(SensorManager manager) {
        for(Sensor s:manager.getSensorList(Sensor.TYPE_ALL))
            Log.i(TAG,"sensor type="+s.getType()+" stringType="+s.getStringType()+" name="+s.getName()
                +" vendor="+s.getVendor()+" minDelayUs="+s.getMinDelay()+" maxDelayUs="+s.getMaxDelay()
                +" range="+s.getMaximumRange()+" resolution="+s.getResolution()+" powerMa="+s.getPower());
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(closed||writer==null)return;
        exchange.read(snapshot);long now=SystemClock.elapsedRealtimeNanos();
        try{
            // Formatting allocations stay on this opt-in thread, never on sensor/render threads.
            writer.write(String.format(Locale.US,"%d,%.4f,%.4f,"
                +"%.5f,%.5f,%.5f,%.5f,%.5f,"
                +"%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,"
                +"%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
                +"%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%.6f,%.6f,%s,%.4f,%.6f,%.4f,%.3f,%s,%s%n",
                now,snapshot.pitch,snapshot.roll,snapshot.lat,snapshot.longitudinal,snapshot.peak,
                snapshot.rawLat,snapshot.rawLong,snapshot.accHz,snapshot.gyroHz,snapshot.attHz,
                (now-snapshot.accTimeNs)*1e-6,(now-snapshot.attTimeNs)*1e-6,snapshot.nativePose,snapshot.status,snapshot.fresh(now),
                snapshot.ax,snapshot.ay,snapshot.az,snapshot.gx,snapshot.gy,snapshot.gz,
                snapshot.abx,snapshot.aby,snapshot.abz,snapshot.gbx,snapshot.gby,snapshot.gbz,
                snapshot.calibrationReason,snapshot.calibrationAccStd,snapshot.calibrationGyroStd,snapshot.nativeRejected,
                snapshot.calibrationPoseRange,snapshot.calibrationGyroMean,snapshot.calibrationTiltRange,
                snapshot.calibrationElapsedSec,snapshot.calibrationRelaxed,snapshot.calibrationQuality));
            rows++;if(rows%20==0)writer.flush();
            if(rows<36_000)handler.postDelayed(this,100);else{writer.close();writer=null;Log.i(TAG,"CSV one-hour limit reached");}
        }catch(IOException e){Log.e(TAG,"CSV write failed",e);try{writer.close();}catch(IOException ignored){}writer=null;}
    }};
    void close(){
        closed=true;
        if(handler!=null)handler.post(()->{handler.removeCallbacks(tick);try{if(writer!=null)writer.close();}catch(IOException ignored){}thread.quitSafely();});
    }
}
