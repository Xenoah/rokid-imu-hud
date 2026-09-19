package dev.xenoah.hud.core;

import java.util.Random;

/** Scenarios motivated by the user's screenshot; these are not a replay of measured IMU data. */
public final class WearCalibrationTests {
    private static int passed,failed;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(double got,double expected,double tolerance,String message){
        check(Double.isFinite(got)&&Math.abs(got-expected)<=tolerance,message+": got="+got+" expected="+expected);
    }
    private static void test(String name,Runnable body){
        try{body.run();passed++;System.out.println("PASS\t"+name);}
        catch(AssertionError e){failed++;System.out.println("FAIL\t"+name+": "+e.getMessage());}
    }
    private static final class Input {
        final Exchange exchange=new Exchange();final HudEngine engine=new HudEngine(exchange,1_000_000_000L);
        final Snapshot s=new Snapshot();final Quat base=new Quat().axis(1,0,0,Math.PI/2),body=new Quat(),q=new Quat();
        final Vec3 a=new Vec3(),bias=new Vec3();final Random random=new Random(20260919);
        long t=1_000_000_000L;
        void sample(int i,int poseEvery,boolean yaw,double angle,double speed,double offset,boolean flip){
            t+=5_000_000L;body.axis(yaw?0:1,yaw?1:0,0,angle);q.multiply(base,body);
            if(i%poseEvery==0){double sign=flip&&i%2==0?-1:1;engine.onRotation(t,q.w*sign,q.x*sign,q.y*sign,q.z*sign);}
            double noise=random.nextGaussian()*.002;
            engine.onGyro(t,yaw?0:speed+offset+noise,yaw?speed+offset+noise:0,0);
            body.inverseRotate(0,Config.G*.99,0,a);engine.onAccel(t,a.x,a.y,a.z);exchange.read(s);
        }
    }
    public static void main(String[] args){
        test("Stable native pose with 3.69deg/s gyro offset can calibrate",()->{
            Input f=new Input();double offset=Math.toRadians(3.69);
            for(int i=0;i<500&&!f.s.valid;i++)f.sample(i,1,false,0,0,offset,false);
            check(f.s.valid&&f.s.nativePose,"screenshot-scale offset still blocks READY");
            f.engine.copyGyroBias(f.bias);near(f.bias.x,offset,.001,"estimate observed gyro offset");
            near(f.s.lat,0,.002,"static LAT");near(f.s.longitudinal,0,.002,"static LONG");
            for(int i=0;i<500;i++)f.sample(i,10,false,0,0,offset,false);
            check(f.s.fresh(f.t),"remain live with high offset and sparse native samples");
            near(f.s.pitch,0,.1,"correct bias in short gyro extrapolation");
            near(f.s.longitudinal,0,.003,"gravity removal after calibration");
        });
        test("Sub-degree head sway above the old gyro threshold can calibrate",()->{
            for(int every:new int[]{1,10}){
                Input f=new Input();double amp=Math.toRadians(.32),frequency=2*Math.PI*2;
                for(int i=0;i<500&&!f.s.valid;i++){
                    double t=i*.005,angle=amp*Math.sin(frequency*t),speed=amp*frequency*Math.cos(frequency*t);
                    f.sample(i,every,false,angle,speed,.004,false);
                }
                check(f.s.valid&&f.s.nativePose,"small worn-head motion blocked at poseEvery="+every);
                f.engine.copyGyroBias(f.bias);near(f.bias.x,.004,.003,"do not learn sway as gyro offset");
            }
        });
        test("Small slow real rotation is subtracted from the gyro bias estimate",()->{
            Input f=new Input();double speed=Math.toRadians(.8);
            for(int i=0;i<500&&!f.s.valid;i++)f.sample(i,10,false,speed*i*.005,speed,.004,false);
            check(f.s.valid,"small pose change can complete calibration");
            f.engine.copyGyroBias(f.bias);near(f.bias.x,.004,.001,"actual angular velocity is not bias");
        });
        test("Calibrating during constant yaw demonstrates the documented six-axis ambiguity",()->{
            Input f=new Input();double speed=Math.toRadians(3.69);
            for(int i=0;i<3300;i++)f.sample(i,1,true,speed*i*.005,speed,.004,false);
            // These inputs are also produced by a stationary device with constant gyro bias
            // and an OS pose integrating that bias. There is no independent yaw reference.
            check(f.s.valid&&f.s.nativeRejected&&!f.s.nativePose,"raw keep-still assumption is explicit");
            f.engine.copyGyroBias(f.bias);near(f.bias.y,speed+.004,.002,"constant yaw can contaminate bias if user moves");
        });
        test("Large out-and-back motion cannot hide behind equal endpoint poses",()->{
            Input f=new Input();double amp=Math.toRadians(4),frequency=2*Math.PI;
            for(int i=0;i<3300;i++){
                double time=i*.005;f.sample(i,1,true,amp*Math.sin(frequency*time),amp*frequency*Math.cos(frequency*time),0,false);
            }
            check(!f.s.valid,"large excursions must be checked throughout the window");
        });
        test("Quaternion sign flips do not look like head motion",()->{
            Input f=new Input();
            for(int i=0;i<500&&!f.s.valid;i++)f.sample(i,1,false,0,0,.004,true);
            check(f.s.valid&&f.s.nativePose,"q and -q represent the same attitude");
        });
        test("Fallback estimates a bounded stable raw rate under the keep-still assumption",()->{
            Input f=new Input();
            for(int i=0;i<3300;i++){
                f.t+=5_000_000L;f.engine.onGyro(f.t,0,Math.toRadians(3.69),0);f.engine.onAccel(f.t,0,Config.G,0);
            }
            f.exchange.read(f.s);check(f.s.valid&&!f.s.nativePose,"constant bounded offset must be calibratable");
            f.engine.copyGyroBias(f.bias);near(f.bias.y,Math.toRadians(3.69),.001,"residual offset estimate");
        });
        test("A validated gyro offset survives native-to-fusion handover",()->{
            Input f=new Input();double offset=Math.toRadians(3.69);
            for(int i=0;i<500&&!f.s.valid;i++)f.sample(i,1,false,0,0,offset,false);
            check(f.s.valid,"native calibration first");f.engine.useFallback(f.t);
            for(int i=0;i<500;i++){
                f.t+=5_000_000L;f.engine.onGyro(f.t,offset,0,0);f.engine.onAccel(f.t,0,Config.G*.99,0);
            }
            f.exchange.read(f.s);check(f.s.valid&&!f.s.nativePose,"known offset must not block fallback recalibration");
            near(f.s.pitch,0,.1,"fallback pitch with carried bias");near(f.s.longitudinal,0,.003,"fallback gravity removal");
        });
        System.out.println("WEAR CALIBRATION RESULT\t"+passed+" passed; "+failed+" failed (synthetic only)");
        if(failed!=0)throw new AssertionError(failed+" wearable calibration regressions failed");
    }
}
