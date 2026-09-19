package dev.xenoah.hud.core;

/** Single-writer engine. All event times use the Android elapsedRealtimeNanos clock. */
public final class HudEngine {
    private final Fusion fusion=new Fusion();
    private final Quat pose=new Quat(),reference=new Quat(),candidate=new Quat();
    private final Vec3 acc=new Vec3(),gyro=new Vec3(),up=new Vec3(),world=new Vec3(),ref=new Vec3();
    private final Vec3 accBias=new Vec3(),gyroBias=new Vec3(),candidateBias=new Vec3(),candidateGyroBias=new Vec3();
    private final Vec3 sumGravity=new Vec3();
    private final RunningStats3 accStats=new RunningStats3(),gyroStats=new RunningStats3();
    private final NativePoseWindow poseWindow=new NativePoseWindow();
    private final GravityWindow gravityWindow=new GravityWindow();
    private final Rate accRate=new Rate(),gyroRate=new Rate(),poseRate=new Rate();
    private final Snapshot out=new Snapshot();
    private long accTime,gyroTime,nativeTime,poseTime,filterTime,calStart,noticeUntil;
    private double refPitch,refRoll;
    private boolean nativeActive,initialized,calibrating=true,hasReference,recenter;
    private boolean nativeRejected,relaxed;
    private Snapshot.CalibrationReason nativeFailure=Snapshot.CalibrationReason.COLLECTING;
    private final Exchange exchange;

    public HudEngine(Exchange exchange,long now) { this.exchange=exchange;calStart=now; publish(now); }

    /** OS world up is +Z; fallback world up is +Y. Keep the two world frames separate. */
    public void onRotation(long t,double w,double x,double y,double z) {
        if(out.status==Snapshot.Status.SENSOR_ERROR)return;
        if(nativeRejected||t<=nativeTime||!candidate.set(w,x,y,z).normalize()) return;
        nativeTime=t;poseRate.add(t);
        if(!nativeActive) {
            nativeActive=true;
            beginCalibration(t,false); // Change world frame without extending an active deadline.
        }
        pose.set(candidate);poseTime=t;initialized=true;
        if(calibrating){
            if(poseWindow.count==0){accStats.clear();gyroStats.clear();gravityWindow.clear();sumGravity.set(0,0,0);}
            poseWindow.add(t,pose);
            if(out.calibrationReason==Snapshot.CalibrationReason.POSE_DELAY)out.calibrationReason=Snapshot.CalibrationReason.COLLECTING;
        }
        if(!calibrating)advanceNativePose(Math.max(t,gyroTime));
        updateTilt(0);publish(t);
    }
    public void useFallback(long now) {
        if(!nativeActive) return;
        nativeActive=false;initialized=false;nativeTime=0;
        beginCalibration(now,false);
    }
    private void rejectNative(long now,Snapshot.CalibrationReason why){
        nativeRejected=true;nativeFailure=why;useFallback(now);
        out.calibrationReason=why;publish(now);
    }
    public boolean nativePoseRejected(){return nativeRejected;}
    public Snapshot.CalibrationReason nativeFailureReason(){return nativeFailure;}
    public void onGyro(long t,double x,double y,double z) {
        if(t<=gyroTime||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        double dt=gyroTime==0?0:(t-gyroTime)*1e-9;
        gyroTime=t;gyro.set(x,y,z);gyroRate.add(t);
        if(out.status==Snapshot.Status.SENSOR_ERROR){publish(t);return;}
        if(calibrating){
            if(dt>Config.FRESH_NS*1e-9)resetWindow();
            gyroStats.add(t,x,y,z);
        }
        if(nativeActive&&!calibrating){
            if(advanceNativePose(t))updateTilt(0);
        }else if(!nativeActive&&initialized&&dt>0&&dt<0.1&&t-accTime<Config.FRESH_NS) {
            fusion.update(gyro,gyroBias,acc,accBias,dt);pose.set(fusion.q);poseTime=t;
            updateTilt(dt);
        } else if(!nativeActive&&dt>=0.1) {
            if(!calibrating)beginCalibration(t,true);
            initialized=false;
        }
        publish(t);
    }
    public void onAccel(long t,double x,double y,double z) {
        if(t<=accTime||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        long previous=accTime;accTime=t;acc.set(x,y,z);accRate.add(t);
        if(out.status==Snapshot.Status.SENSOR_ERROR){publish(t);return;}
        if(!nativeActive&&!initialized&&acc.norm()>1) {
            fusion.q.alignGravity(acc);pose.set(fusion.q);poseTime=t;initialized=true;
        }
        if(calibrating) {
            if(previous!=0&&t-previous>Config.FRESH_NS)resetWindow();
            calibrate(t);publish(t);return;
        }
        if(nativeActive)advanceNativePose(t);
        if(Math.abs(t-gyroTime)>Config.FRESH_NS||Math.abs(t-poseTime)>Config.MAX_PAIR_SKEW_NS
            ||(nativeActive&&Math.abs(t-nativeTime)>Config.MAX_POSE_PREDICTION_NS)) {
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
        beginCalibration(now,true);
    }
    private void beginCalibration(long now,boolean newRequest){
        if(newRequest||!calibrating){calStart=now;relaxed=false;out.calibrationElapsedSec=0;}
        calibrating=true;recenter=hasReference;resetWindow();
        out.status=Snapshot.Status.CALIBRATING;out.valid=false;out.progress=0;
        out.calibrationQuality=Snapshot.CalibrationQuality.PENDING;
        out.calibrationReason=Snapshot.CalibrationReason.COLLECTING;
        out.calibrationPoseRange=0;out.calibrationGyroMean=0;out.calibrationTiltRange=0;
        publish(now);
    }
    private void resetWindow() {
        accStats.clear();gyroStats.clear();poseWindow.clear();gravityWindow.clear();sumGravity.set(0,0,0);out.progress=0;
    }
    private void calibrate(long t) {
        if(t-calStart>=Config.CALIBRATION_TIMEOUT_NS){finishProvisional(t);return;}
        if(!relaxed&&t-calStart>=Config.CALIBRATION_RELAX_AFTER_NS){
            relaxed=true;resetWindow();out.calibrationReason=Snapshot.CalibrationReason.RELAXED_LIMITS;
        }
        long windowNs=relaxed?Config.RELAXED_CALIBRATION_NS:Config.CALIBRATION_NS;
        // Calibration averages independent streams. Do not apply the live G-meter's
        // tight pairing gate here: a legitimate 20Hz OS pose otherwise restarts forever.
        if(gyroTime==0||Math.abs(t-gyroTime)>Config.FRESH_NS){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.WAIT_GYRO;return;
        }
        if(out.calibrationReason==Snapshot.CalibrationReason.WAIT_GYRO)out.calibrationReason=Snapshot.CalibrationReason.COLLECTING;
        if(nativeActive&&Math.abs(t-nativeTime)>Config.FRESH_NS){
            rejectNative(t,Snapshot.CalibrationReason.POSE_DELAY);return;
        }
        double n=acc.norm();
        if(Math.abs(n-Config.G)>(relaxed?Config.RELAXED_ACCEL_NORM_ERROR:Config.CALIBRATION_ACCEL_NORM_ERROR)){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.ACCEL_SCALE;return;
        }
        if(!initialized){resetWindow();out.calibrationReason=Snapshot.CalibrationReason.POSE_DELAY;return;}
        if(nativeActive&&poseWindow.count==0){out.calibrationReason=Snapshot.CalibrationReason.POSE_DELAY;return;}
        out.calibrationPoseRange=nativeActive?Math.toDegrees(poseWindow.maxAngle):0;
        out.calibrationGyroMean=gyroStats.mean.norm();
        // Do not erase raw measurements because an untrusted OS estimate moved.
        // Decide whether to retain that estimate only after the independent raw window is valid.
        if(gyro.norm()>(relaxed?Config.RELAXED_MAX_GYRO_RAD_S:Config.CALIBRATION_MAX_GYRO_RAD_S)){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.GYRO_MOVING;return;
        }
        // Calibration assumes the user is holding still. A constant bounded raw rate can be
        // bias; rejecting it before estimating bias makes startup impossible on affected IMUs.
        // Constant yaw remains physically indistinguishable from a gyro offset in six axes.
        double motionMean=gyroStats.mean.norm();
        if(!nativeActive&&hasReference){
            // A previously validated bias remains useful during a native-to-fusion handover.
            // Reject motion relative to it; do not mistake the known offset for a new rotation.
            up.set(gyroStats.mean.x-gyroBias.x,gyroStats.mean.y-gyroBias.y,gyroStats.mean.z-gyroBias.z);
            motionMean=up.norm();
        }
        double meanLimit=Config.MAX_STATIONARY_GYRO_BIAS_RAD_S;
        if(gyroStats.count>=5&&gyroTime-gyroStats.firstNs>=Config.CALIBRATION_MOTION_WINDOW_NS
            &&motionMean>meanLimit){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.GYRO_MOVING;return;
        }
        accStats.add(t,acc.x,acc.y,acc.z);
        gravityWindow.add(t,acc);out.calibrationTiltRange=Math.toDegrees(gravityWindow.maxAngle);
        if(gravityWindow.maxAngle>(relaxed?Config.RELAXED_MAX_TILT_RAD:Config.CALIBRATION_MAX_TILT_RAD)){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.ACCEL_MOVING;return;
        }
        pose.inverseRotate(0,nativeActive?0:Config.G,nativeActive?Config.G:0,up);
        sumGravity.x+=up.x;sumGravity.y+=up.y;sumGravity.z+=up.z;
        long start=Math.max(accStats.firstNs,gyroStats.firstNs);
        out.progress=Math.min(1,(t-start)/(double)windowNs);
        out.calibrationAccStd=Math.sqrt(accStats.varianceSum());
        out.calibrationGyroStd=Math.sqrt(gyroStats.varianceSum());
        if(!gravityWindow.ready||t-start<windowNs||accStats.count<Config.CALIBRATION_MIN_SAMPLES
            ||gyroStats.count<Config.CALIBRATION_MIN_SAMPLES)return;
        if(nativeActive&&(poseWindow.count<Config.CALIBRATION_MIN_POSE_SAMPLES
            ||poseWindow.lastNs-poseWindow.firstNs<windowNs
            ||Math.abs(t-poseWindow.lastNs)>Config.MAX_PAIR_SKEW_NS))return;
        double gyroStd=relaxed?Config.RELAXED_GYRO_STD:(nativeActive?Config.NATIVE_CALIBRATION_GYRO_STD:Config.STILL_GYRO_STD);
        if((!nativeActive&&motionMean>Config.MAX_STATIONARY_GYRO_BIAS_RAD_S)
            ||gyroStats.varianceSum()>3*gyroStd*gyroStd){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.GYRO_MOVING;return;
        }
        double accelStd=relaxed?Config.RELAXED_ACCEL_STD:Config.STILL_ACCEL_STD;
        if(accStats.varianceSum()>3*accelStd*accelStd){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.ACCEL_MOVING;return;
        }
        if(nativeActive&&poseWindow.maxAngle>Config.CALIBRATION_MAX_TILT_RAD){
            rejectNative(t,Snapshot.CalibrationReason.POSE_UNSTABLE);return;
        }
        candidateGyroBias.set(gyroStats.mean);
        if(nativeActive){
            // Subtract the OS-observed rotation: small real motion must not become gyro bias.
            // This is a short-window approximation, with every pose excursion limited above.
            poseWindow.meanRate(up);
            candidateGyroBias.x-=up.x;candidateGyroBias.y-=up.y;candidateGyroBias.z-=up.z;
            if(candidateGyroBias.norm()>Config.MAX_NATIVE_GYRO_BIAS_RAD_S){
                rejectNative(t,Snapshot.CalibrationReason.GYRO_BIAS);return;
            }
        }else if(candidateGyroBias.norm()>Config.MAX_STATIONARY_GYRO_BIAS_RAD_S){
            resetWindow();out.calibrationReason=Snapshot.CalibrationReason.GYRO_BIAS;return;
        }
        double ax=accStats.mean.x,ay=accStats.mean.y,az=accStats.mean.z;
        if(nativeActive)candidateBias.set(ax-sumGravity.x/accStats.count,
            ay-sumGravity.y/accStats.count,az-sumGravity.z/accStats.count);
        else {
            // One stationary pose cannot identify transverse accelerometer bias independently
            // of tilt. Only radial residual is observable without an external attitude reference.
            double norm=Math.sqrt(ax*ax+ay*ay+az*az),k=1-Config.G/norm;
            candidateBias.set(ax*k,ay*k,az*k);
        }
        if(candidateBias.norm()>Config.MAX_ACCEL_BIAS){
            if(nativeActive)rejectNative(t,Snapshot.CalibrationReason.POSE_MISMATCH);
            else{resetWindow();out.calibrationReason=Snapshot.CalibrationReason.ACCEL_BIAS;}
            return;
        }
        // Commit biases only after every quality check passes.
        gyroBias.set(candidateGyroBias);accBias.set(candidateBias);
        if(!nativeActive){fusion.q.alignGravity(accStats.mean);pose.set(fusion.q);poseTime=t;}
        completeCalibration(t,relaxed?Snapshot.CalibrationQuality.RELAXED:Snapshot.CalibrationQuality.STANDARD);
    }
    private void completeCalibration(long t,Snapshot.CalibrationQuality quality){
        reference.set(pose);readTilt();refPitch=out.pitch;refRoll=out.roll;
        out.pitch=0;out.roll=0;out.lat=0;out.longitudinal=0;out.rawLat=0;out.rawLong=0;out.peak=0;
        out.calibrationElapsedSec=Math.max(0,(t-calStart)*1e-9);out.calibrationQuality=quality;
        filterTime=t;calibrating=false;hasReference=true;out.valid=true;out.progress=1;
        out.status=recenter?Snapshot.Status.RECENTERED:Snapshot.Status.READY;
        out.calibrationReason=quality==Snapshot.CalibrationQuality.PROVISIONAL
            ?Snapshot.CalibrationReason.DEADLINE:Snapshot.CalibrationReason.COMPLETE;
        noticeUntil=t+1_000_000_000L;
    }
    /** Wall-clock watchdog; also reaches a terminal state if no sensor events arrive. */
    public void tick(long now){
        if(calibrating&&now-calStart>=Config.CALIBRATION_TIMEOUT_NS)finishProvisional(now);
        publish(now);
    }
    private void finishProvisional(long now){
        if(gyroTime==0||Math.abs(now-gyroTime)>Config.FRESH_NS){failCalibration(now,Snapshot.CalibrationReason.WAIT_GYRO);return;}
        if(accTime==0||Math.abs(now-accTime)>Config.FRESH_NS){failCalibration(now,Snapshot.CalibrationReason.WAIT_ACCEL);return;}
        double norm=acc.norm();
        if(!Double.isFinite(norm)||norm<Config.PROVISIONAL_MIN_ACCEL||norm>Config.PROVISIONAL_MAX_ACCEL){
            failCalibration(now,Snapshot.CalibrationReason.ACCEL_SCALE);return;
        }
        if(!Double.isFinite(gyro.norm())||gyro.norm()>Config.PROVISIONAL_MAX_GYRO){
            failCalibration(now,Snapshot.CalibrationReason.GYRO_MOVING);return;
        }
        // Stillness has not been established. Do not learn a new bias from arbitrary motion.
        // Retain committed biases (zero additional bias at first launch) and re-align to the
        // current measured gravity direction. This is explicitly an approximate reference.
        if(nativeActive)rejectNative(now,Snapshot.CalibrationReason.DEADLINE);
        nativeRejected=true;nativeFailure=Snapshot.CalibrationReason.DEADLINE;
        up.set(acc.x-accBias.x,acc.y-accBias.y,acc.z-accBias.z);
        fusion.q.alignGravity(up);pose.set(fusion.q);poseTime=accTime;initialized=true;
        completeCalibration(now,Snapshot.CalibrationQuality.PROVISIONAL);
    }
    private void failCalibration(long now,Snapshot.CalibrationReason reason){
        out.calibrationReason=reason;error(now);
    }
    /** Short gyro extrapolation between accepted OS samples, never unbounded dead reckoning. */
    private boolean advanceNativePose(long t){
        if(!nativeActive||!initialized||t<=poseTime||t-nativeTime>Config.MAX_POSE_PREDICTION_NS
            ||gyroTime==0||Math.abs(t-gyroTime)>Config.MAX_PAIR_SKEW_NS)return false;
        pose.integrate(gyro.x-gyroBias.x,gyro.y-gyroBias.y,gyro.z-gyroBias.z,(t-poseTime)*1e-9);
        poseTime=t;return true;
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
    public void error(long now){
        if(calibrating)out.calibrationElapsedSec=Math.max(0,(now-calStart)*1e-9);
        calibrating=false;out.status=Snapshot.Status.SENSOR_ERROR;out.valid=false;publish(now);
    }
    private void publish(long t) {
        if(calibrating){
            out.status=Snapshot.Status.CALIBRATING;
            out.calibrationElapsedSec=Math.max(0,(t-calStart)*1e-9);
        }
        out.calibrationRelaxed=relaxed;
        if(!calibrating&&out.status!=Snapshot.Status.SENSOR_ERROR&&t>noticeUntil)out.status=Snapshot.Status.LIVE;
        out.timeNs=t;out.accTimeNs=accTime;out.gyroTimeNs=gyroTime;out.attTimeNs=poseTime;
        out.accHz=accRate.hz;out.gyroHz=gyroRate.hz;out.attHz=nativeActive?poseRate.hz:gyroRate.hz;
        out.nativePose=nativeActive;out.gravityCorrection=!nativeActive&&fusion.correctionUsed;
        out.nativeRejected=nativeRejected;
        out.ax=acc.x;out.ay=acc.y;out.az=acc.z;out.gx=gyro.x;out.gy=gyro.y;out.gz=gyro.z;
        out.abx=accBias.x;out.aby=accBias.y;out.abz=accBias.z;
        out.gbx=gyroBias.x;out.gby=gyroBias.y;out.gbz=gyroBias.z;
        exchange.publish(out);
    }
    public void copyGyroBias(Vec3 target){target.set(gyroBias);}
    public void copyAccelBias(Vec3 target){target.set(accBias);}
}
