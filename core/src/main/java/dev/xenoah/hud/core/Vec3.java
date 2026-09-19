package dev.xenoah.hud.core;

public final class Vec3 {
    public double x,y,z;
    public Vec3() {}
    public Vec3(double x,double y,double z) { set(x,y,z); }
    public Vec3 set(double x,double y,double z) { this.x=x; this.y=y; this.z=z; return this; }
    public Vec3 set(Vec3 v) { return set(v.x,v.y,v.z); }
    public double norm() { return Math.sqrt(x*x+y*y+z*z); }
    public boolean finite() { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z); }
}
