package dev.xenoah.hud.core;

/** Reused Welford mean/variance accumulator; one independent stream per instance. */
final class RunningStats3 {
    final Vec3 mean=new Vec3();
    int count;
    long firstNs;
    private double m2x,m2y,m2z;
    void clear(){count=0;firstNs=0;mean.set(0,0,0);m2x=0;m2y=0;m2z=0;}
    void add(long t,double x,double y,double z){
        if(count==0)firstNs=t;
        count++;
        double dx=x-mean.x,dy=y-mean.y,dz=z-mean.z;
        mean.x+=dx/count;mean.y+=dy/count;mean.z+=dz/count;
        m2x+=dx*(x-mean.x);m2y+=dy*(y-mean.y);m2z+=dz*(z-mean.z);
    }
    double varianceSum(){return count<2?0:Math.max(0,(m2x+m2y+m2z)/(count-1));}
}
