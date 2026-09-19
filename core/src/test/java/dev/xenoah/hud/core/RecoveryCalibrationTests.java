package dev.xenoah.hud.core;

import java.util.Random;

/** Modelled failure modes seen in the video, not a replay of captured sensor samples. */
public final class RecoveryCalibrationTests {
    private static int passed,failed;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(double got,double expected,double tolerance,String message){
        check(Double.isFinite(got)&&Math.abs(got-expected)<=tolerance,message+" got="+got);
    }
    private static void test(String name,Runnable body){
        try{body.run();passed++;System.out.println("PASS\t"+name);}
        catch(AssertionError e){failed++;System.out.println("FAIL\t"+name+": "+e.getMessage());}
    }
    private static final class Input {
        final Exchange exchange=new Exchange();final HudEngine engine=new HudEngine(exchange,1_000_000_000L);
        final Snapshot s=new Snapshot();final Quat base=new Quat().axis(1,0,0,Math.PI/2),body=new Quat(),q=new Quat();
        final Vec3 acc=new Vec3(),bias=new Vec3();final Random random=new Random(10166);
        long t=1_000_000_000L;
        void sample(int axis,double nativeAngle,double gyro,double ax,double ay,double az,int hz,boolean os){
            t+=Math.round(1e9/hz);
            if(os){body.axis(axis==0?1:0,axis==1?1:0,0,nativeAngle);q.multiply(base,body);engine.onRotation(t,q.w,q.x,q.y,q.z);}
            double g=gyro+random.nextGaussian()*.002;
            engine.onGyro(t,axis==0?g:0,axis==1?g:0,0);
            engine.onAccel(t,ax+random.nextGaussian()*.015,ay+random.nextGaussian()*.015,az+random.nextGaussian()*.015);
            exchange.read(s);
        }
    }
    public static void main(String[] args){
        test("Drifting OS pose cannot erase a quiet raw-IMU calibration window",()->{
            for(int axis=0;axis<=1;axis++)for(int hz:new int[]{198,247}){
                Input f=new Input();double bias=Math.toRadians(4.5);
                long firstReady=0;
                for(int i=0;i<hz*5;i++){
                    double phase=4*Math.PI*i/hz,amp=axis==0?Math.toRadians(.32):0;
                    double sway=amp*Math.sin(phase),rate=amp*4*Math.PI*Math.cos(phase);
                    f.body.axis(1,0,0,sway);f.body.inverseRotate(0,Config.G*.99,0,f.acc);
                    f.sample(axis,bias*i/hz+sway,bias+rate,f.acc.x,f.acc.y,f.acc.z,hz,true);
                    if(f.s.valid&&firstReady==0)firstReady=f.t;
                }
                check(f.s.valid&&!f.s.nativePose&&f.s.nativeRejected,"drifting source blocked startup axis="+axis+" hz="+hz);
                check(firstReady-1_000_000_000L<3_000_000_000L,"recovery should finish within 3 seconds of quiet input");
                f.engine.copyGyroBias(f.bias);near(axis==0?f.bias.x:f.bias.y,bias,.002,"estimate residual gyro offset");
                for(int i=0;i<hz*60;i++)f.sample(axis,bias*(5+i/(double)hz),bias,0,Config.G*.99,0,hz,true);
                check(f.s.fresh(f.t)&&!f.s.nativePose,"later native events must not revive rejected source");
                near(f.s.pitch,0,.15,"static pitch after recovery");near(f.s.roll,0,.15,"static roll after recovery");
                near(Math.hypot(f.s.lat,f.s.longitudinal),0,.005,"static horizontal G after recovery");
            }
        });
        test("Raw fusion can estimate a stationary 4.5deg/s offset at startup",()->{
            Input f=new Input();double bias=Math.toRadians(4.5);
            for(int i=0;i<750;i++)f.sample(1,0,bias,0,Config.G,0,247,false);
            check(f.s.valid&&!f.s.nativePose,"raw mean was incorrectly treated as movement before bias estimation");
            f.engine.copyGyroBias(f.bias);near(f.bias.y,bias,.001,"raw offset");
        });
        test("Real pitch movement is rejected by measured gravity direction",()->{
            for(boolean os:new boolean[]{false,true}){
                Input f=new Input();double speed=Math.toRadians(4.5);
                for(int i=0;i<4000;i++){
                    double angle=speed*i/247;f.body.axis(1,0,0,angle);f.body.inverseRotate(0,Config.G,0,f.acc);
                    f.sample(0,angle,speed,f.acc.x,f.acc.y,f.acc.z,247,os);
                }
                check(!f.s.valid&&f.s.status==Snapshot.Status.WAIT_STILL,"tilt must not become gyro bias os="+os);
            }
        });
        test("Recovery waits for motion to stop, then completes without restarting the app",()->{
            Input f=new Input();double bias=Math.toRadians(4.5),speed=.45;
            for(int i=0;i<1000;i++)f.sample(0,speed*i/247,speed,0,Config.G,0,247,true);
            check(!f.s.valid,"fast movement cannot be accepted");
            for(int i=0;i<1000&&!f.s.valid;i++)f.sample(0,4+bias*i/247,bias,0,Config.G,0,247,true);
            check(f.s.valid&&!f.s.nativePose,"recover from movement and drifting OS");
        });
        test("Raw-path recenter re-estimates bias and clears peak and filters",()->{
            Input f=new Input();double bias=Math.toRadians(4.5);
            for(int i=0;i<750;i++)f.sample(0,0,bias,0,Config.G,0,247,false);
            check(f.s.valid,"initial raw calibration");
            for(int i=0;i<200;i++)f.sample(0,0,bias,.4*Config.G,Config.G,-.3*Config.G,247,false);
            check(f.s.peak>.4,"peak before recenter");f.engine.requestRecenter(f.t);
            for(int i=0;i<310;i++)f.sample(0,0,.09,0,Config.G,0,247,false);
            check(f.s.valid&&f.s.status==Snapshot.Status.RECENTERED,"raw recenter completes");
            f.engine.copyGyroBias(f.bias);near(f.bias.x,.09,.001,"updated offset");near(f.s.peak,0,.005,"peak reset");
            near(f.s.pitch,0,.1,"attitude reset");near(f.s.lat,0,.005,"filter reset");
        });
        test("Calibration cannot count an accelerometer gap as quiet observation",()->{
            Input f=new Input();
            for(int i=0;i<100;i++)f.sample(0,0,.004,0,Config.G,0,200,true);
            for(int i=0;i<200;i++){
                f.t+=5_000_000L;f.engine.onRotation(f.t,f.base.w,f.base.x,f.base.y,f.base.z);f.engine.onGyro(f.t,.004,0,0);
            }
            f.sample(0,0,.004,0,Config.G,0,200,true);check(!f.s.valid,"gap cannot satisfy calibration duration");
            for(int i=0;i<240;i++)f.sample(0,0,.004,0,Config.G,0,200,true);
            check(f.s.valid,"fresh complete window recovers");
        });
        test("A stationary offset beyond the native limit can use raw calibration",()->{
            Input f=new Input();double bias=Math.toRadians(6.2);
            for(int i=0;i<1000;i++)f.sample(0,0,bias,0,Config.G,0,247,true);
            check(f.s.valid&&!f.s.nativePose&&f.s.nativeRejected,"native bias gate must not restart forever");
            f.engine.copyGyroBias(f.bias);near(f.bias.x,bias,.002,"raw estimate within its bounded range");
        });
        System.out.println("RECOVERY RESULT\t"+passed+" passed; "+failed+" failed (synthetic only)");
        if(failed!=0)throw new AssertionError(failed+" recovery regressions failed");
    }
}
