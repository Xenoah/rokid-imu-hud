package dev.xenoah.hud.core;

/** Gated Mahony-style proportional gravity correction; yaw has no gravity feedback. */
public final class Fusion {
    public final Quat q=new Quat();
    private final Vec3 up=new Vec3();
    public boolean correctionUsed;
    public void update(Vec3 gyro,Vec3 gyroBias,Vec3 acc,Vec3 accBias,double dt) {
        double gx=gyro.x-gyroBias.x,gy=gyro.y-gyroBias.y,gz=gyro.z-gyroBias.z;
        double ax=acc.x-accBias.x,ay=acc.y-accBias.y,az=acc.z-accBias.z;
        double n=Math.sqrt(ax*ax+ay*ay+az*az);
        q.inverseRotate(0,1,0,up);
        double dot=n>0?(ax*up.x+ay*up.y+az*up.z)/n:-1;
        correctionUsed=Math.abs(n/Config.G-1)<Config.FUSION_NORM_GATE
            &&dot>Math.cos(Config.FUSION_ANGLE_GATE_RAD);
        if(correctionUsed) {
            ax/=n;ay/=n;az/=n;
            gx+=Config.FUSION_KP*(ay*up.z-az*up.y);
            gy+=Config.FUSION_KP*(az*up.x-ax*up.z);
            gz+=Config.FUSION_KP*(ax*up.y-ay*up.x);
        }
        q.integrate(gx,gy,gz,dt);
    }
}
