package dev.xenoah.hud.core;

/** Single-writer engine. All event times use the Android elapsedRealtimeNanos clock. */
public final class HudEngine {
    private final Fusion fusion=new Fusion();
    private final Quat pose=new Quat(),reference=new Quat(),candidate=new Quat();
    private final Vec3 acc=new Vec3(),gyro=new Vec3(),up=new Vec3(),world=new Vec3(),ref=new Vec3();
    private final Vec3 accBias=new Vec3(),gyroBias=new Vec3(),anchor=new Vec3();
    private final Vec3 sumAcc=new Vec3(),sumGyro=new Vec3(),sumGravity=new Vec3();
    private final Vec3 sumSquare=new Vec3();
    private final Rate accRate=new Rate(),gyroRate=new Rate(),poseRate=new Rate();
    private final Snapshot out=new Snapshot();
    private long accTime,gyroTime,nativeTime,poseTime,filterTime,calStart,stillStart,noticeUntil;
    private int accCount,gyroCount;
    private double refPitch,refRoll;
    private boolean nativeActive,initialized,calibrating=true,hasReference,recenter;
    private final Exchange exchange;

    public HudEngine(Exchange exchange,long now) { this.exchange=exchange;calStart=now; publish(now); }

    /** OS world up is +Z; fallback world up is +Y. Keep the two world frames separate. */
    public void onRotation(long t,double w,double x,double y,double z) {
        if(t<=nativeTime||!candidate.set(w,x,y,z).normalize()) return;
        nativeTime=t;poseRate.add(t);
        if(!nativeActive) {
            nativeActive=true;
            requestRecenter(t); // Never reuse a reference from a different world frame.
            recenter=hasReference;
        }
        pose.set(candidate);poseTime=t;initialized=true;
        updateTilt(0);publish(t);
    }
    public void useFallback(long now) {
        if(!nativeActive) return;
        nativeActive=false;initialized=false;nativeTime=0;
        requestRecenter(now);
    }
    public void onGyro(long t,double x,double y,double z) {
        if(t<=gyroTime||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        double dt=gyroTime==0?0:(t-gyroTime)*1e-9;
        gyroTime=t;gyro.set(x,y,z);gyroRate.add(t);
        if(calibrating) { sumGyro.x+=x;sumGyro.y+=y;sumGyro.z+=z;gyroCount++; }
        if(!nativeActive&&initialized&&dt>0&&dt<0.1&&t-accTime<Config.FRESH_NS) {
            fusion.update(gyro,gyroBias,acc,accBias,dt);pose.set(fusion.q);poseTime=t;
            updateTilt(dt);
        } else if(!nativeActive&&dt>=0.1) { requestRecenter(t);initialized=false; }
        publish(t);
    }
    public void onAccel(long t,double x,double y,double z) {
        if(t<=accTime||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        long previous=accTime;accTime=t;acc.set(x,y,z);accRate.add(t);
        if(!nativeActive&&!initialized&&acc.norm()>1) {
            fusion.q.alignGravity(acc);pose.set(fusion.q);poseTime=t;initialized=true;
        }
        if(calibrating) { calibrate(t);publish(t);return; }
        if(t-gyroTime>Config.FRESH_NS||Math.abs(t-poseTime)>Config.MAX_PAIR_SKEW_NS) {
            out.valid=false;publish(t);return;
        }
        // Remove estimated DEVICE-frame bias BEFORE rotation (bias follows the sensor).
        pose.rotate(x-accBias.x,y-accBias.y,z-accBias.z,world);
        if(nativeActive)world.z-=Config.G;else world.y-=Config.G;
        reference.inverseRotate(world.x,world.y,world.z,ref);
        out.rawLat=ref.x/Config.G;out.rawLong=-ref.z/Config.G;
        double dt=(t-filterTime)*1e-9;filterTime=t;
        if(previous==0||dt>0.1||dt<=0) {out.lat=out.rawLat;out.longitudinal=out.rawLong;}
        else {
            double alpha=1-Math.exp(-2*Math.PI*Config.G_FILTER_CUTOFF_HZ*dt);
            out.lat+=alpha*(out.rawLat-out.lat);out.longitudinal+=alpha*(out.rawLong-out.longitudinal);
        }
        out.peak=Math.max(out.peak,Math.hypot(out.lat,out.longitudinal));
        out.valid=true;publish(t);
    }
    public void requestRecenter(long now) {
        if(calibrating&&now-calStart<250_000_000L)return;
        calibrating=true;calStart=now;recenter=hasReference;resetWindow();
        out.status=Snapshot.Status.CALIBRATING;out.valid=false;out.progress=0;
        publish(now);
    }
    private void resetWindow() {
        stillStart=0;accCount=0;gyroCount=0;
        sumAcc.set(0,0,0);sumGyro.set(0,0,0);sumGravity.set(0,0,0);sumSquare.set(0,0,0);
    }
    private void calibrate(long t) {
        if(!initialized||Math.abs(t-gyroTime)>Config.MAX_PAIR_SKEW_NS
            ||(nativeActive&&Math.abs(t-nativeTime)>Config.MAX_PAIR_SKEW_NS)) {resetWindow();return;}
        double n=acc.norm();
        double dx=acc.x-anchor.x,dy=acc.y-anchor.y,dz=acc.z-anchor.z;
        boolean still=gyro.norm()<Config.STILL_GYRO_RAD_S&&Math.abs(n-Config.G)<0.6
            &&(stillStart==0||Math.sqrt(dx*dx+dy*dy+dz*dz)<Config.STILL_ACCEL_DELTA);
        if(!still){resetWindow();out.progress=0;return;}
        if(stillStart==0){stillStart=t;anchor.set(acc);}
        sumAcc.x+=acc.x;sumAcc.y+=acc.y;sumAcc.z+=acc.z;
        sumSquare.x+=acc.x*acc.x;sumSquare.y+=acc.y*acc.y;sumSquare.z+=acc.z*acc.z;
        pose.inverseRotate(0,nativeActive?0:Config.G,nativeActive?Config.G:0,up);
        sumGravity.x+=up.x;sumGravity.y+=up.y;sumGravity.z+=up.z;
        accCount++;out.progress=Math.min(1,(t-stillStart)/(double)Config.CALIBRATION_NS);
        if(t-stillStart<Config.CALIBRATION_NS||accCount<30||gyroCount<30)return;
        double ax=sumAcc.x/accCount,ay=sumAcc.y/accCount,az=sumAcc.z/accCount;
        double variance=Math.max(0,sumSquare.x/accCount-ax*ax)
            +Math.max(0,sumSquare.y/accCount-ay*ay)+Math.max(0,sumSquare.z/accCount-az*az);
        if(variance>3*Config.STILL_ACCEL_STD*Config.STILL_ACCEL_STD){resetWindow();return;}
        gyroBias.set(sumGyro.x/gyroCount,sumGyro.y/gyroCount,sumGyro.z/gyroCount);
        if(nativeActive) accBias.set(ax-sumGravity.x/accCount,ay-sumGravity.y/accCount,az-sumGravity.z/accCount);
        else {
            // One stationary pose cannot identify transverse accelerometer bias independently
            // of tilt. Only radial residual is observable without an external attitude reference.
            double norm=Math.sqrt(ax*ax+ay*ay+az*az),k=1-Config.G/norm;
            accBias.set(ax*k,ay*k,az*k);
            fusion.q.alignGravity(anchor.set(ax,ay,az));pose.set(fusion.q);poseTime=t;
        }
        if(accBias.norm()>Config.MAX_ACCEL_BIAS){resetWindow();out.status=Snapshot.Status.WAIT_STILL;return;}
        reference.set(pose);readTilt();refPitch=out.pitch;refRoll=out.roll;
        out.pitch=0;out.roll=0;out.lat=0;out.longitudinal=0;out.rawLat=0;out.rawLong=0;out.peak=0;
        filterTime=t;calibrating=false;hasReference=true;out.valid=true;
        out.status=recenter?Snapshot.Status.RECENTERED:Snapshot.Status.READY;
        noticeUntil=t+1_000_000_000L;
    }
    private void readTilt() {
        pose.inverseRotate(0,nativeActive?0:1,nativeActive?1:0,up);
        out.pitch=Math.toDegrees(Math.atan2(-up.z,Math.hypot(up.x,up.y)));
        out.roll=Math.toDegrees(Math.atan2(-up.x,up.y));
    }
    private void updateTilt(double dt) {
        if(calibrating||!hasReference)return;
        double oldPitch=out.pitch,oldRoll=out.roll;
        readTilt();double pitch=out.pitch-refPitch,roll=wrap(out.roll-refRoll);
        // Native samples use their measured interval; dt==0 means immediate tracking.
        double alpha=dt<=0?1:1-Math.exp(-dt/Config.HUD_FILTER_TAU_S);
        out.pitch=oldPitch+alpha*(pitch-oldPitch);out.roll=wrap(oldRoll+alpha*wrap(roll-oldRoll));
    }
    static double wrap(double a){while(a>180)a-=360;while(a<-180)a+=360;return a;}
    public void setMode(int mode){out.mode=Math.max(1,Math.min(3,mode));publish(out.timeNs);}
    public void error(long now){out.status=Snapshot.Status.SENSOR_ERROR;out.valid=false;publish(now);}
    private void publish(long t) {
        if(calibrating&&out.status!=Snapshot.Status.SENSOR_ERROR)
            out.status=t-calStart>Config.CALIBRATION_TIMEOUT_NS?Snapshot.Status.WAIT_STILL:Snapshot.Status.CALIBRATING;
        if(!calibrating&&out.status!=Snapshot.Status.SENSOR_ERROR&&t>noticeUntil)out.status=Snapshot.Status.LIVE;
        out.timeNs=t;out.accTimeNs=accTime;out.gyroTimeNs=gyroTime;out.attTimeNs=poseTime;
        out.accHz=accRate.hz;out.gyroHz=gyroRate.hz;out.attHz=nativeActive?poseRate.hz:gyroRate.hz;
        out.nativePose=nativeActive;out.gravityCorrection=!nativeActive&&fusion.correctionUsed;
        out.ax=acc.x;out.ay=acc.y;out.az=acc.z;out.gx=gyro.x;out.gy=gyro.y;out.gz=gyro.z;
        out.abx=accBias.x;out.aby=accBias.y;out.abz=accBias.z;
        out.gbx=gyroBias.x;out.gby=gyroBias.y;out.gbz=gyroBias.z;
        exchange.publish(out);
    }
    public void copyGyroBias(Vec3 target){target.set(gyroBias);}
    public void copyAccelBias(Vec3 target){target.set(accBias);}
}
