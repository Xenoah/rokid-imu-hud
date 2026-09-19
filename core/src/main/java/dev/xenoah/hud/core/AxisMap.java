package dev.xenoah.hud.core;

/** Proper signed permutation from SensorManager axes to the documented head axes. */
public final class AxisMap {
    private final int[] axes;
    private final Quat bodyToSensor=new Quat();
    private final Quat raw=new Quat();
    public AxisMap(int x,int y,int z) {
        axes=new int[]{x,y,z};
        double[][] m=new double[3][3];
        for(int row=0;row<3;row++) {
            int index=Math.abs(axes[row])-1;
            if(index<0||index>2)throw new IllegalArgumentException("Axis must be +/-1..3");
            m[row][index]=Math.signum(axes[row]);
        }
        double det=m[0][0]*(m[1][1]*m[2][2]-m[1][2]*m[2][1])
            -m[0][1]*(m[1][0]*m[2][2]-m[1][2]*m[2][0])
            +m[0][2]*(m[1][0]*m[2][1]-m[1][1]*m[2][0]);
        if(det!=1)throw new IllegalArgumentException("Axis map must preserve right handedness");
        // Quaternion for transpose(M): new head coordinates -> native sensor coordinates.
        double a=m[0][0],b=m[1][0],c=m[2][0],d=m[0][1],e=m[1][1],f=m[2][1],g=m[0][2],h=m[1][2],i=m[2][2];
        double s;
        if(a+e+i>0){s=2*Math.sqrt(1+a+e+i);bodyToSensor.set(s/4,(h-f)/s,(c-g)/s,(d-b)/s);}
        else if(a>e&&a>i){s=2*Math.sqrt(1+a-e-i);bodyToSensor.set((h-f)/s,s/4,(b+d)/s,(c+g)/s);}
        else if(e>i){s=2*Math.sqrt(1+e-a-i);bodyToSensor.set((c-g)/s,(b+d)/s,s/4,(f+h)/s);}
        else{s=2*Math.sqrt(1+i-a-e);bodyToSensor.set((d-b)/s,(c+g)/s,(f+h)/s,s/4);}
        bodyToSensor.normalize();
    }
    private static double at(int axis,double x,double y,double z) {
        return Math.signum(axis)*(Math.abs(axis)==1?x:Math.abs(axis)==2?y:z);
    }
    public void vector(double x,double y,double z,Vec3 out) {
        out.set(at(axes[0],x,y,z),at(axes[1],x,y,z),at(axes[2],x,y,z));
    }
    public void quaternion(double w,double x,double y,double z,Quat out) {
        raw.set(w,x,y,z);out.multiply(raw,bodyToSensor);
    }
}
