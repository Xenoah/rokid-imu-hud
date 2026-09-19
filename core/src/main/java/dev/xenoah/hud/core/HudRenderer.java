package dev.xenoah.hud.core;

/** 480x400 original line-art HUD; no Strings/Paths allocated per frame. */
public final class HudRenderer {
    private static final String[] PITCH_LABELS={"-60","-50","-40","-30","-20","-10","0","10","20","30","40","50","60"};
    private final char[] digits=new char[24];
    private final Viewport viewport=new Viewport();
    private final float[] trailX=new float[12],trailY=new float[12];
    private int trailCount,trailIndex;
    private long trailTime;
    public void draw(HudCanvas c,Snapshot s,long now,int width,int height,boolean debug) {
        c.clear();viewport.fit(width,height);c.save();c.translate(viewport.x,viewport.y);c.scale(viewport.scale);
        c.clip(0,0,480,400);
        c.text("ROKID / INERTIAL",18,23,13,190);
        c.text(s.mode==1?"HUD + G":s.mode==2?"HORIZON":"G METER",350,23,13,190);
        boolean fresh=s.fresh(now);
        boolean calibration=s.status==Snapshot.Status.CALIBRATING||s.status==Snapshot.Status.WAIT_STILL;
        if(calibration||s.status==Snapshot.Status.SENSOR_ERROR) {
            trailCount=0;
            String message=s.status==Snapshot.Status.SENSOR_ERROR?"SENSOR UNAVAILABLE":s.status==Snapshot.Status.WAIT_STILL?"CALIBRATION WAIT":"CALIBRATING";
            c.text(message,240-message.length()*7.5f,174,25,255);
            c.text(s.status==Snapshot.Status.SENSOR_ERROR?"RESTART HUD":"KEEP HEAD STILL",98,205,20,230);
            c.line(100,229,380,229,2,100);c.line(100,229,(float)(100+280*s.progress),229,3,255);
            if(calibration){
                String reason=calibrationMessage(s.calibrationReason);
                c.text(reason,240-reason.length()*5.1f,261,17,255);
                c.text("ACC",55,290,13,190);
                number(c,Math.sqrt(s.ax*s.ax+s.ay*s.ay+s.az*s.az)/Config.G,2,95,290,17,false);
                c.text("G",151,290,13,190);c.text("GYRO",225,290,13,190);
                number(c,Math.toDegrees(Math.sqrt(s.gx*s.gx+s.gy*s.gy+s.gz*s.gz)),2,283,290,17,false);
                c.text("D/S",354,290,13,190);
                c.text("A",55,316,13,190);number(c,s.accHz,0,75,316,15,false);
                c.text("G",140,316,13,190);number(c,s.gyroHz,0,160,316,15,false);
                c.text("HZ",210,316,13,190);c.text(s.nativePose?"OS QUAT":"IMU FUSION",281,316,13,190);
                c.text("POSE",55,339,13,190);
                if(s.nativePose)number(c,s.calibrationPoseRange,1,104,339,15,false);
                else c.text("--",104,339,15,190);
                c.text("DEG",151,339,13,190);c.text("TILT",225,339,13,190);
                number(c,s.calibrationTiltRange,1,283,339,15,false);c.text("DEG",354,339,13,190);
                c.text("GYRO MEAN",55,360,12,190);
                number(c,Math.toDegrees(s.calibrationGyroMean),2,147,360,13,false);c.text("D/S",209,360,12,190);
            }
        } else if(!fresh) {
            c.text("IMU STALE",155,184,25,255);
            c.text("WAIT FOR SENSOR DATA",108,214,17,210);
            trailCount=0;
        } else {
            if(s.mode!=3) horizon(c,s,s.mode==2);
            if(s.mode!=2) meter(c,s,now,s.mode==3);
            if(s.status==Snapshot.Status.READY||s.status==Snapshot.Status.RECENTERED) {
                c.text(s.status==Snapshot.Status.READY?"READY":"RECENTERED",s.status==Snapshot.Status.READY?207:172,192,22,255);
                trailCount=0;
            }
        }
        if(debug&&!calibration) {
            c.text("A",18,358,12,180);number(c,s.accHz,0,32,358,12,false);
            c.text("G",80,358,12,180);number(c,s.gyroHz,0,94,358,12,false);
            c.text(s.nativePose?"OS QUAT":"MAHONY",155,358,12,180);
        }
        c.text("2-FINGER DOUBLE: ZERO",18,383,12,180);
        c.text("TAP: MODE",365,383,12,180);
        c.restore();
    }
    private static String calibrationMessage(Snapshot.CalibrationReason reason){
        switch(reason){
            case WAIT_GYRO:return "WAITING FOR GYRO";
            case POSE_DELAY:return "WAITING FOR ATTITUDE";
            case GYRO_MOVING:return "LAST: ROTATION DETECTED";
            case ATTITUDE_MOVING:return "LAST: ATTITUDE CHANGED";
            case GYRO_BIAS:return "GYRO OFFSET TOO HIGH";
            case ACCEL_MOVING:return "LAST: MOTION / VIBRATION";
            case ACCEL_SCALE:return "CHECK ACCEL SIGNAL";
            case POSE_MISMATCH:return "SWITCHING TO IMU FUSION";
            case POSE_UNSTABLE:return "OS UNSTABLE - IMU CAL";
            case ACCEL_BIAS:return "ACCEL OFFSET TOO HIGH";
            default:return "SAMPLING - KEEP STILL";
        }
    }
    private void horizon(HudCanvas c,Snapshot s,boolean solo) {
        float cy=solo?206:164;
        c.text("ROLL",18,51,13,190);number(c,s.roll,1,18,77,25,true);
        c.text("PITCH",366,51,13,190);number(c,s.pitch,1,366,77,25,true);
        // Fixed roll scale with moving pointer. Positive right-bank gives pointer to right.
        final float arcCy=146,arcRadius=100;
        for(int a=-60;a<=60;a+=10) {
            double rad=Math.toRadians(a);float x=(float)Math.sin(rad),y=(float)Math.cos(rad);
            float len=a%30==0?11:6;
            c.line(240+x*arcRadius,arcCy-y*arcRadius,240+x*(arcRadius+len),arcCy-y*(arcRadius+len),2,200);
        }
        double rr=Math.toRadians(Math.max(-65,Math.min(65,s.roll)));
        float px=240+(float)Math.sin(rr)*(arcRadius-10),py=arcCy-(float)Math.cos(rr)*(arcRadius-10);
        c.line(px-5,py+6,px,py,2.5f,255);c.line(px,py,px+5,py+6,2.5f,255);
        float clipTop=110,clipBottom=solo?338:228;
        c.save();c.clip(32,clipTop,448,clipBottom);c.translate(240,cy);
        // Canvas y points down. Right head tilt: apparent world horizon rises on right.
        c.rotate((float)-s.roll);c.translate(0,(float)s.pitch*3.1f);
        for(int a=-60;a<=60;a+=10) {
            float y=-a*3.1f,half=a==0?180:a%20==0?67:48;
            double radians=Math.toRadians(-s.roll);
            c.line(-half,y,-20,y,a==0?2.5f:2,230);c.line(20,y,half,y,a==0?2.5f:2,230);
            if(a!=0) {
                c.line(-half,y,-half,y+(a>0?5:-5),2,210);
                c.line(half,y,half,y+(a>0?5:-5),2,210);
                String label=PITCH_LABELS[a/10+6];
                double labelY=cy+Math.sin(radians)*(half+8)+Math.cos(radians)*(y+s.pitch*3.1+5);
                double labelEndY=labelY+Math.sin(radians)*label.length()*9;
                if(Math.min(labelY,labelEndY)-14>clipTop&&Math.max(labelY,labelEndY)+3<clipBottom)
                    c.text(label,half+8,y+5,14,230);
            }
        }
        c.restore();
        // Screen-fixed aircraft marker; empty center for the scene beyond the waveguide.
        c.line(203,cy,222,cy,3,255);c.line(222,cy,229,cy+6,3,255);
        c.line(251,cy+6,258,cy,3,255);c.line(258,cy,277,cy,3,255);
        c.circle(240,cy,3,2,255,false);
    }
    private void meter(HudCanvas c,Snapshot s,long now,boolean solo) {
        float cx=solo?166:90,cy=solo?198:300,r=solo?105:49;
        if(now-trailTime>50_000_000L) {
            trailTime=now;trailX[trailIndex]=(float)s.lat;trailY[trailIndex]=(float)s.longitudinal;
            trailIndex=(trailIndex+1)%trailX.length;trailCount=Math.min(trailCount+1,trailX.length);
        }
        for(int i=1;i<=4;i++)c.circle(cx,cy,r*i/4,1.5f,i==4?220:110,false);
        c.line(cx-r,cy,cx+r,cy,1.5f,180);c.line(cx,cy-r,cx,cy+r,1.5f,180);
        c.text("FWD",cx-12,cy-r-7,12,210);c.text("BRAKE",cx-17,cy+r+15,11,190);
        c.text("L",cx-r-14,cy+4,12,210);c.text("R",cx+r+7,cy+4,12,210);
        c.text("0.5G STEPS / +/-2G",solo?85:181,solo?337:358,11,180);
        for(int j=0;j<trailCount;j++) {
            int at=(trailIndex-trailCount+j+trailX.length)%trailX.length;
            double dx=trailX[at]/Config.G_RANGE,dy=trailY[at]/Config.G_RANGE,n=Math.hypot(dx,dy),k=n>1?1/n:1;
            c.circle(cx+(float)(r*dx*k),cy-(float)(r*dy*k),1.5f,1,35+j*11,true);
        }
        double dx=s.lat/Config.G_RANGE,dy=s.longitudinal/Config.G_RANGE,n=Math.hypot(dx,dy),k=n>1?1/n:1;
        c.circle(cx+(float)(r*dx*k),cy-(float)(r*dy*k),4.5f,2,255,true);
        float tx=solo?305:181,top=solo?133:271,step=solo?60:33;
        float nx=solo?tx:tx+89,ny=solo?top+25:top;
        c.text("LAT G",tx,top,14,180);number(c,s.lat,2,nx,ny,solo?25:21,true);
        c.text("LONG G",tx,top+step,14,180);number(c,s.longitudinal,2,nx,ny+step,solo?25:21,true);
        c.text("PEAK G",tx,top+2*step,14,180);number(c,s.peak,2,nx,ny+2*step,solo?25:21,false);
        if(n>1)c.text("OVER RANGE",solo?130:330,solo?355:347,12,255);
    }
    private void number(HudCanvas c,double value,int decimals,float x,float y,float size,boolean sign) {
        if(!Double.isFinite(value)){c.text("--",x,y,size,255);return;}
        value=Math.max(-999,Math.min(999,value));int at=0,mult=decimals==2?100:decimals==1?10:1;
        long scaled=Math.round(Math.abs(value)*mult);
        if(sign||value<0)digits[at++]=value<0&&scaled!=0?'-':'+';
        int start=at;long whole=scaled/mult;
        do{digits[at++]=(char)('0'+whole%10);whole/=10;}while(whole>0);
        for(int i=start,j=at-1;i<j;i++,j--){char tmp=digits[i];digits[i]=digits[j];digits[j]=tmp;}
        if(decimals>0){digits[at++]='.';if(decimals==2)digits[at++]=(char)('0'+scaled%100/10);digits[at++]=(char)('0'+scaled%10);}
        c.text(digits,at,x,y,size,255);
    }
}
