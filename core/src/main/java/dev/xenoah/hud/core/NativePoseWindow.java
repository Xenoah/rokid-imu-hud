package dev.xenoah.hud.core;

/** Allocation-free OS attitude history for a short, small-motion calibration window. */
final class NativePoseWindow {
    private final Quat first=new Quat(),last=new Quat(),inverse=new Quat(),delta=new Quat();
    private final Vec3 rotationSum=new Vec3();
    int count;
    long firstNs,lastNs;
    double maxAngle;
    void clear(){count=0;firstNs=0;lastNs=0;maxAngle=0;rotationSum.set(0,0,0);}
    void add(long t,Quat q){
        if(count==0){first.set(q);last.set(q);firstNs=t;lastNs=t;count=1;return;}
        if(t<=lastNs)return;
        inverse.conjugate(last);delta.multiply(inverse,q);
        // q and -q encode the same pose: use the shortest relative rotation.
        double sign=delta.w<0?-1:1;
        double n=Math.sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z);
        double scale=n<1e-10?2:2*Math.atan2(n,Math.abs(delta.w))/n;
        rotationSum.x+=sign*scale*delta.x;
        rotationSum.y+=sign*scale*delta.y;
        rotationSum.z+=sign*scale*delta.z;
        double dot=Math.abs(first.w*q.w+first.x*q.x+first.y*q.y+first.z*q.z);
        maxAngle=Math.max(maxAngle,2*Math.acos(Math.min(1,dot)));
        last.set(q);lastNs=t;count++;
    }
    void meanRate(Vec3 out){
        double duration=(lastNs-firstNs)*1e-9;
        if(duration<=0){out.set(0,0,0);return;}
        out.set(rotationSum.x/duration,rotationSum.y/duration,rotationSum.z/duration);
    }
}
