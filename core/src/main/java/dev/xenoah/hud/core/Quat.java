package dev.xenoah.hud.core;

/** Hamilton quaternion (w,x,y,z), active rotation: sensor/head frame -> world. */
public final class Quat {
    public double w=1,x,y,z;
    public Quat set(double w,double x,double y,double z) { this.w=w;this.x=x;this.y=y;this.z=z;return this; }
    public Quat set(Quat q) { return set(q.w,q.x,q.y,q.z); }
    public boolean normalize() {
        double n=Math.sqrt(w*w+x*x+y*y+z*z);
        if (!Double.isFinite(n)||n<1e-9) return false;
        w/=n;x/=n;y/=n;z/=n;return true;
    }
    public Quat conjugate(Quat q) { return set(q.w,-q.x,-q.y,-q.z); }
    public Quat multiply(Quat a,Quat b) {
        return set(a.w*b.w-a.x*b.x-a.y*b.y-a.z*b.z,
            a.w*b.x+a.x*b.w+a.y*b.z-a.z*b.y,
            a.w*b.y-a.x*b.z+a.y*b.w+a.z*b.x,
            a.w*b.z+a.x*b.y-a.y*b.x+a.z*b.w);
    }
    public void rotate(double vx,double vy,double vz,Vec3 out) {
        double tx=2*(y*vz-z*vy),ty=2*(z*vx-x*vz),tz=2*(x*vy-y*vx);
        out.set(vx+w*tx+y*tz-z*ty,vy+w*ty+z*tx-x*tz,vz+w*tz+x*ty-y*tx);
    }
    public void inverseRotate(double vx,double vy,double vz,Vec3 out) {
        double tx=2*(-y*vz+z*vy),ty=2*(-z*vx+x*vz),tz=2*(-x*vy+y*vx);
        out.set(vx+w*tx-y*tz+z*ty,vy+w*ty-z*tx+x*tz,vz+w*tz-x*ty+y*tx);
    }
    public void integrate(double gx,double gy,double gz,double dt) {
        // Exponential map avoids Euler integration drift at high angular rates.
        double speed=Math.sqrt(gx*gx+gy*gy+gz*gz), half=0.5*speed*dt;
        double s=speed<1e-9?0.5*dt:Math.sin(half)/speed;
        double dw=Math.cos(half),dx=gx*s,dy=gy*s,dz=gz*s;
        set(w*dw-x*dx-y*dy-z*dz,w*dx+x*dw+y*dz-z*dy,
            w*dy-x*dz+y*dw+z*dx,w*dz+x*dy-y*dx+z*dw);
        normalize();
    }
    public Quat axis(double ax,double ay,double az,double angle) {
        double n=Math.sqrt(ax*ax+ay*ay+az*az),s=Math.sin(angle/2)/n;
        return set(Math.cos(angle/2),ax*s,ay*s,az*s);
    }
    /** Shortest rotation taking measured gravity direction to world +Y. */
    public void alignGravity(Vec3 a) {
        double n=a.norm();
        if(n<1e-9) { set(1,0,0,0); return; }
        double dot=a.y/n;
        if(dot<-.999999) set(0,1,0,0);
        else { set(1+dot,-a.z/n,0,a.x/n);normalize(); }
    }
}
