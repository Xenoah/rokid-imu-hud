package dev.xenoah.hud.core;

/** Raw accelerometer direction check, independent of the OS or uncalibrated gyro attitude. */
final class GravityWindow {
    private final Vec3 filtered=new Vec3(),anchor=new Vec3();
    private long firstNs,lastNs;
    private int count;
    boolean ready;
    double maxAngle;
    void clear(){firstNs=0;lastNs=0;count=0;ready=false;maxAngle=0;}
    void add(long t,Vec3 a){
        if(count++==0){firstNs=t;lastNs=t;filtered.set(a);return;}
        double alpha=1-Math.exp(-(t-lastNs)*1e-9/Config.CALIBRATION_GRAVITY_TAU_S);lastNs=t;
        filtered.x+=alpha*(a.x-filtered.x);filtered.y+=alpha*(a.y-filtered.y);filtered.z+=alpha*(a.z-filtered.z);
        if(!ready){
            if(t-firstNs<Config.CALIBRATION_GRAVITY_WARMUP_NS||count<3)return;
            anchor.set(filtered);ready=true;
        }
        double denominator=anchor.norm()*filtered.norm();
        if(denominator<1e-9)return;
        double dot=(anchor.x*filtered.x+anchor.y*filtered.y+anchor.z*filtered.z)/denominator;
        maxAngle=Math.max(maxAngle,Math.acos(Math.max(-1,Math.min(1,dot))));
    }
}
