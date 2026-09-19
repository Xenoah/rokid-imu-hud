package dev.xenoah.hud;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import dev.xenoah.hud.core.*;

final class SensorController implements SensorEventListener {
    private final SensorManager manager;
    private final HandlerThread thread=new HandlerThread("hud-imu",Process.THREAD_PRIORITY_MORE_FAVORABLE);
    private final Handler handler;
    private final Exchange exchange;
    private final AxisMap axes=new AxisMap(1,2,3); // Official bare-metal axes. Change only after real-axis verification.
    private final Vec3 vector=new Vec3();
    private final Quat quaternion=new Quat();
    private final Snapshot diagnostic=new Snapshot();
    private HudEngine engine;
    private Sensor poseSensor,accSensor,gyroSensor;
    private long started,lastPose,lastAccel,lastGyro,recenterAt,unreliableSince,lastCalibrationLog;
    private boolean running,forceFusion,sensorFault;
    private int mode=1;
    SensorController(Context context,Exchange exchange,boolean forceFusion) {
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        this.exchange=exchange;this.forceFusion=forceFusion;
        thread.start();handler=new Handler(thread.getLooper());
    }
    void start(){handler.post(()->{
        if(running)return;running=true;started=SystemClock.elapsedRealtimeNanos();
        lastPose=0;lastAccel=0;lastGyro=0;recenterAt=0;unreliableSince=0;lastCalibrationLog=0;sensorFault=false;
        engine=new HudEngine(exchange,started);engine.setMode(mode);
        Diagnostics.sensors(manager);
        accSensor=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor=manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(accSensor==null||gyroSensor==null){engine.error(started);Log.e(Diagnostics.TAG,"Required accel/gyro missing");return;}
        if(!register(accSensor)||!register(gyroSensor)){
            manager.unregisterListener(this);engine.error(started);return;
        }
        poseSensor=null;
        if(!forceFusion){
            poseSensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if(poseSensor==null)poseSensor=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if(poseSensor!=null&&!register(poseSensor))poseSensor=null;
        }
        Log.i(Diagnostics.TAG,"poseCandidate="+(poseSensor==null?"MAHONY":poseSensor.getName())+" requestUs="+Config.SENSOR_PERIOD_US);
        handler.postDelayed(watchdog,500);
    });}
    private boolean register(Sensor sensor){
        int period=Math.max(Config.SENSOR_PERIOD_US,sensor.getMinDelay());
        try{
            boolean ok=manager.registerListener(this,sensor,period,0,handler);
            Log.i(Diagnostics.TAG,"register type="+sensor.getType()+" requestedPeriodUs="+period+" ok="+ok);
            return ok;
        }catch(RuntimeException e){Log.e(Diagnostics.TAG,"Sensor registration failed",e);return false;}
    }
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!running)return;
        long now=SystemClock.elapsedRealtimeNanos();
        if(poseSensor!=null&&engine.nativePoseRejected()){
            manager.unregisterListener(SensorController.this,poseSensor);poseSensor=null;
            Log.w(Diagnostics.TAG,"Native quaternion rejected: "+engine.nativeFailureReason()+"; using raw IMU fusion");
        }
        if(poseSensor!=null&&now-started>2_000_000_000L&&(lastPose==0||now-lastPose>Config.FRESH_NS
                ||(unreliableSince!=0&&now-unreliableSince>2_000_000_000L))){
            manager.unregisterListener(SensorController.this,poseSensor);poseSensor=null;engine.useFallback(now);
            Log.w(Diagnostics.TAG,"Native quaternion timed out; recalibrating Mahony fallback");
        }
        boolean missing=now-started>3_000_000_000L&&(now-lastAccel>2_000_000_000L||now-lastGyro>2_000_000_000L);
        if(missing&&!sensorFault){sensorFault=true;engine.error(now);Log.e(Diagnostics.TAG,"IMU stream stopped");}
        else if(!missing&&sensorFault){sensorFault=false;engine.requestRecenter(now);Log.i(Diagnostics.TAG,"IMU recovered; recalibration required");}
        exchange.read(diagnostic);
        if((diagnostic.status==Snapshot.Status.CALIBRATING||diagnostic.status==Snapshot.Status.WAIT_STILL)
            &&now-lastCalibrationLog>=3_000_000_000L){
            lastCalibrationLog=now;
            Log.i(Diagnostics.TAG,"calibration reason="+diagnostic.calibrationReason+" progress="+diagnostic.progress
                +" accHz="+diagnostic.accHz+" gyroHz="+diagnostic.gyroHz+" poseHz="+diagnostic.attHz
                +" accNorm="+Math.sqrt(diagnostic.ax*diagnostic.ax+diagnostic.ay*diagnostic.ay+diagnostic.az*diagnostic.az)
                +" gyroNorm="+Math.sqrt(diagnostic.gx*diagnostic.gx+diagnostic.gy*diagnostic.gy+diagnostic.gz*diagnostic.gz)
                +" accStd="+diagnostic.calibrationAccStd+" gyroStd="+diagnostic.calibrationGyroStd
                +" gyroMeanRadS="+diagnostic.calibrationGyroMean+" poseRangeDeg="+diagnostic.calibrationPoseRange);
        }
        handler.postDelayed(this,500);
    }};
    void stop(){handler.post(()->{running=false;manager.unregisterListener(this);handler.removeCallbacks(watchdog);});}
    void close(){handler.post(()->{running=false;manager.unregisterListener(this);handler.removeCallbacks(watchdog);thread.quitSafely();});}
    void recenter(){handler.post(()->{
        long now=SystemClock.elapsedRealtimeNanos();
        if(!running||engine==null||now-recenterAt<750_000_000L)return;
        recenterAt=now;engine.requestRecenter(now);Log.i(Diagnostics.TAG,"RECENTER requested; hold still for 1 second");
    });}
    void nextMode(){handler.post(()->{mode=mode%3+1;if(engine!=null)engine.setMode(mode);});}
    @Override public void onSensorChanged(SensorEvent event){
        if(!running||engine==null||event.values.length<3)return;
        int type=event.sensor.getType();long t=event.timestamp;
        if(type==Sensor.TYPE_GAME_ROTATION_VECTOR||type==Sensor.TYPE_ROTATION_VECTOR){
            if(poseSensor!=event.sensor)return;
            if(event.accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){if(unreliableSince==0)unreliableSince=SystemClock.elapsedRealtimeNanos();}
            else unreliableSince=0;
            double x=event.values[0],y=event.values[1],z=event.values[2];
            double w=event.values.length>=4?event.values[3]:Math.sqrt(Math.max(0,1-x*x-y*y-z*z));
            if(!Double.isFinite(w)||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
            axes.quaternion(w,x,y,z,quaternion);
            if(!quaternion.normalize())return;
            lastPose=t;engine.onRotation(t,quaternion.w,quaternion.x,quaternion.y,quaternion.z);
        }else{
            axes.vector(event.values[0],event.values[1],event.values[2],vector);
            if(!vector.finite())return;
            if(type==Sensor.TYPE_ACCELEROMETER){lastAccel=t;engine.onAccel(t,vector.x,vector.y,vector.z);}
            else if(type==Sensor.TYPE_GYROSCOPE){lastGyro=t;engine.onGyro(t,vector.x,vector.y,vector.z);}
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){Log.i(Diagnostics.TAG,"accuracy type="+sensor.getType()+" value="+accuracy);}
}
