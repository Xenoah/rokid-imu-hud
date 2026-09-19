package dev.xenoah.hud.core;

import java.util.Random;

/** Synthetic delivery/noise regression cases; not measurements from Rokid hardware. */
public final class CalibrationTests {
    private static int passed,failed;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void test(String name,Runnable body){
        try{body.run();passed++;System.out.println("PASS\t"+name);}
        catch(AssertionError e){failed++;System.out.println("FAIL\t"+name+": "+e.getMessage());}
    }
    private static final class Input {
        final Exchange exchange=new Exchange();
        final HudEngine engine=new HudEngine(exchange,1_000_000_000L);
        final Snapshot s=new Snapshot();
        final Quat base=new Quat().axis(1,0,0,Math.PI/2);
        long t=1_000_000_000L;
        void sample(int i,int poseEvery,double noise,Random random,boolean badPose){
            t+=5_000_000L;
            if(i%poseEvery==0){
                if(badPose)engine.onRotation(t,1,0,0,0);
                else engine.onRotation(t,base.w,base.x,base.y,base.z);
            }
            engine.onGyro(t,.004,-.002,.003);
            engine.onAccel(t,random.nextGaussian()*noise,Config.G+random.nextGaussian()*noise,random.nextGaussian()*noise);
            exchange.read(s);
        }
    }
    public static void main(String[] args){
        test("20Hz OS attitude with 200Hz accel/gyro can finish calibration",()->{
            Input f=new Input();Random r=new Random(71);
            for(int i=0;i<700&&!f.s.valid;i++)f.sample(i,10,0,r,false);
            check(f.s.valid,"stationary asynchronous streams never became READY");
            check(f.s.nativePose,"usable OS quaternion must remain preferred");
        });
        test("Stationary 0.08m/s2 per-axis noise does not repeatedly erase calibration",()->{
            Input f=new Input();Random r=new Random(91);
            for(int i=0;i<700&&!f.s.valid;i++)f.sample(i,1,.08,r,false);
            check(f.s.valid,"stationary noise never became READY");
        });
        test("Inconsistent OS gravity frame recovers through raw IMU fusion",()->{
            Input f=new Input();Random r=new Random(31);
            for(int i=0;i<1000&&!f.s.valid;i++)f.sample(i,1,0,r,true);
            check(f.s.valid&&!f.s.nativePose,"inconsistent OS quaternion causes endless calibration");
            check(Math.abs(f.s.lat)<.005&&Math.abs(f.s.longitudinal)<.005,"fallback static G");
            check(f.engine.nativePoseRejected(),"reject incompatible source for this session");
            for(int i=0;i<300;i++)f.sample(i,1,0,r,true);
            check(f.s.valid&&!f.s.nativePose,"bad native events must not restart calibration");
        });
        test("20/25/50Hz attitude, reordered accel/gyro and noise calibrate",()->{
            for(int interval:new int[]{4,8,10})for(boolean accelFirst:new boolean[]{false,true}){
                Input f=new Input();Random r=new Random(125+interval);
                for(int i=0;i<500&&!f.s.valid;i++){
                    f.t+=5_000_000L;
                    if(i%interval==0)f.engine.onRotation(f.t,f.base.w,f.base.x,f.base.y,f.base.z);
                    if(accelFirst)f.engine.onAccel(f.t,r.nextGaussian()*.08,Config.G+r.nextGaussian()*.08,r.nextGaussian()*.08);
                    f.engine.onGyro(f.t,.004+r.nextGaussian()*.006,-.002+r.nextGaussian()*.006,.003+r.nextGaussian()*.006);
                    if(!accelFirst)f.engine.onAccel(f.t,r.nextGaussian()*.08,Config.G+r.nextGaussian()*.08,r.nextGaussian()*.08);
                    f.exchange.read(f.s);
                }
                check(f.s.valid&&f.s.nativePose,"rate/order combination failed: interval="+interval+" accelFirst="+accelFirst);
                check(f.t<3_000_000_000L,"stationary calibration took over 2 seconds");
            }
        });
        test("Low-rate native attitude stays usable during pitch motion",()->{
            Input f=new Input();Random r=new Random(8);
            for(int i=0;i<240;i++)f.sample(i,10,0,r,false);
            check(f.s.valid,"ready before motion");
            Quat body=new Quat(),q=new Quat();Vec3 a=new Vec3();
            for(int i=1;i<=100;i++){
                f.t+=5_000_000L;body.axis(1,0,0,Math.toRadians(.2*i));q.multiply(f.base,body);
                if(i%10==0)f.engine.onRotation(f.t,q.w,q.x,q.y,q.z);
                f.engine.onGyro(f.t,Math.toRadians(40)+.004,-.002,.003);
                body.inverseRotate(0,Config.G,0,a);f.engine.onAccel(f.t,a.x,a.y,a.z);f.exchange.read(f.s);
                check(f.s.fresh(f.t),"live G must not become stale between native samples at i="+i);
                check(Math.abs(f.s.rawLong)<.006,"prediction must remove gravity during pitch");
            }
            check(Math.abs(f.s.pitch-20)<.05,"pitch sign/angle with sparse native samples");
        });
        test("A stopped native source can fall back and finish calibration",()->{
            Input f=new Input();f.engine.onRotation(f.t,f.base.w,f.base.x,f.base.y,f.base.z);
            for(int i=0;i<500;i++){
                f.t+=5_000_000L;f.engine.onGyro(f.t,0,0,0);f.engine.onAccel(f.t,0,Config.G,0);f.exchange.read(f.s);
            }
            check(f.s.valid&&!f.s.nativePose&&f.s.nativeRejected,"stale native source must not block raw IMU");
            check(f.engine.nativeFailureReason()==Snapshot.CalibrationReason.POSE_DELAY,"report stale native reason");
        });
        test("Missing gyro and wrong accelerometer scale cannot become READY",()->{
            Input noGyro=new Input(),wrongScale=new Input();
            for(int i=0;i<3300;i++){
                noGyro.t+=5_000_000L;noGyro.engine.onAccel(noGyro.t,0,Config.G,0);
                wrongScale.t+=5_000_000L;wrongScale.engine.onGyro(wrongScale.t,0,0,0);wrongScale.engine.onAccel(wrongScale.t,0,1,0);
            }
            noGyro.exchange.read(noGyro.s);wrongScale.exchange.read(wrongScale.s);
            check(!noGyro.s.valid&&noGyro.s.calibrationReason==Snapshot.CalibrationReason.WAIT_GYRO,"missing gyro must remain identified");
            check(!wrongScale.s.valid&&wrongScale.s.calibrationReason==Snapshot.CalibrationReason.ACCEL_SCALE,"wrong units must not be silently accepted");
        });
        test("Sustained vibration is rejected and calibration recovers after it stops",()->{
            Input f=new Input();
            for(int i=0;i<3300;i++){
                f.t+=5_000_000L;f.engine.onRotation(f.t,f.base.w,f.base.x,f.base.y,f.base.z);
                f.engine.onGyro(f.t,0,0,0);f.engine.onAccel(f.t,i%2==0?.4:-.4,Config.G,0);
            }
            f.exchange.read(f.s);check(!f.s.valid&&f.s.status==Snapshot.Status.WAIT_STILL,"vibration cannot force READY at timeout");
            check(f.s.calibrationReason==Snapshot.CalibrationReason.ACCEL_MOVING,"vibration reason");
            Random r=new Random(111);for(int i=0;i<500&&!f.s.valid;i++)f.sample(i,1,.04,r,false);
            check(f.s.valid,"calibration recovers when vibration stops");
        });
        System.out.println("CALIBRATION RESULT\t"+passed+" passed; "+failed+" failed (synthetic only)");
        if(failed!=0)throw new AssertionError(failed+" calibration regressions failed");
    }
}
