package dev.xenoah.hud.core;

/** Mutable, reused render/log snapshot. Never shared without Exchange. */
public final class Snapshot {
    public enum Status { CALIBRATING, READY, LIVE, RECENTERED, WAIT_STILL, SENSOR_ERROR }
    public enum CalibrationReason { COLLECTING, WAIT_GYRO, POSE_DELAY, GYRO_MOVING,
        ACCEL_MOVING, ACCEL_SCALE, POSE_MISMATCH, ACCEL_BIAS, COMPLETE, ATTITUDE_MOVING, GYRO_BIAS, POSE_UNSTABLE }
    public long timeNs,accTimeNs,gyroTimeNs,attTimeNs;
    public double pitch,roll,lat,longitudinal,peak,rawLat,rawLong;
    public double accHz,gyroHz,attHz,progress;
    public double ax,ay,az,gx,gy,gz,abx,aby,abz,gbx,gby,gbz;
    public double calibrationAccStd,calibrationGyroStd,calibrationPoseRange,calibrationGyroMean,calibrationTiltRange;
    public CalibrationReason calibrationReason=CalibrationReason.COLLECTING;
    public boolean nativeRejected;
    public boolean nativePose,gravityCorrection,valid;
    public int mode=1;
    public Status status=Status.CALIBRATING;
    public void copyFrom(Snapshot s) {
        timeNs=s.timeNs;accTimeNs=s.accTimeNs;gyroTimeNs=s.gyroTimeNs;attTimeNs=s.attTimeNs;
        pitch=s.pitch;roll=s.roll;lat=s.lat;longitudinal=s.longitudinal;peak=s.peak;
        rawLat=s.rawLat;rawLong=s.rawLong;accHz=s.accHz;gyroHz=s.gyroHz;attHz=s.attHz;
        progress=s.progress;nativePose=s.nativePose;gravityCorrection=s.gravityCorrection;
        ax=s.ax;ay=s.ay;az=s.az;gx=s.gx;gy=s.gy;gz=s.gz;
        abx=s.abx;aby=s.aby;abz=s.abz;gbx=s.gbx;gby=s.gby;gbz=s.gbz;
        calibrationAccStd=s.calibrationAccStd;calibrationGyroStd=s.calibrationGyroStd;
        calibrationPoseRange=s.calibrationPoseRange;calibrationGyroMean=s.calibrationGyroMean;
        calibrationTiltRange=s.calibrationTiltRange;
        calibrationReason=s.calibrationReason;nativeRejected=s.nativeRejected;
        valid=s.valid;mode=s.mode;status=s.status;
    }
    public boolean fresh(long now) {
        return valid&&now>=accTimeNs&&now-accTimeNs<Config.FRESH_NS
            &&now-gyroTimeNs<Config.FRESH_NS&&now-attTimeNs<Config.FRESH_NS;
    }
}
