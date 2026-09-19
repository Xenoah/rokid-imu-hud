package dev.xenoah.hud.core;

/** Synthetic deadline/quality regressions; no physical-device claims. */
public final class DeadlineCalibrationTests {
    private static int passed,failed;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(double a,double b,double eps,String message){
        check(Double.isFinite(a)&&Math.abs(a-b)<=eps,message+" got="+a);
    }
    private static void test(String name,Runnable body){
        try{body.run();passed++;System.out.println("PASS\t"+name);}
        catch(AssertionError e){failed++;System.out.println("FAIL\t"+name+": "+e.getMessage());}
    }
    private static final class Input {
        final Exchange exchange=new Exchange();final HudEngine engine=new HudEngine(exchange,1_000_000_000L);
        final Snapshot s=new Snapshot();final Quat base=new Quat().axis(1,0,0,Math.PI/2),body=new Quat(),q=new Quat();
        final Vec3 a=new Vec3(),bias=new Vec3();long t=1_000_000_000L;
        void sample(double pitch,double gx,double ax,double ay,double az,int hz,boolean os){
            t+=Math.round(1e9/hz);
            if(os){body.axis(1,0,0,pitch);q.multiply(base,body);engine.onRotation(t,q.w,q.x,q.y,q.z);}
            engine.onGyro(t,gx,0,0);engine.onAccel(t,ax,ay,az);exchange.read(s);
        }
        void quiet(){sample(0,.004,0,Config.G,0,200,false);}
        void busy(){sample(0,.7,0,Config.G,0,200,true);}
    }
    public static void main(String[] args){
        test("Normal startup keeps its one-second calibration and STANDARD quality",()->{
            Input f=new Input();for(int i=0;i<240&&!f.s.valid;i++)f.quiet();
            check(f.s.valid&&f.s.calibrationQuality==Snapshot.CalibrationQuality.STANDARD,"normal source must stay standard");
            check(f.s.calibrationElapsedSec<1.2,"normal startup delay changed");
        });
        test("Ten-second relaxation accepts bounded noise at 198 and 247Hz",()->{
            for(boolean os:new boolean[]{false,true})for(int hz:new int[]{198,247})for(boolean noisyGyro:new boolean[]{false,true}){
                Input f=new Input();long firstReady=0;
                for(int i=0;i<hz*12;i++){
                    double sign=i%2==0?1:-1;
                    f.sample(0,noisyGyro?.17*sign:0,noisyGyro?0:.4*sign,Config.G,0,hz,os);
                    if(f.s.valid){firstReady=f.t;break;}
                }
                check(firstReady>=11_000_000_000L&&firstReady<13_000_000_000L,"relax only after 10s, then finish");
                check(f.s.calibrationQuality==Snapshot.CalibrationQuality.RELAXED,"relaxed result must be labelled");
                near(f.s.lat,0,.001,"initial G zero");
            }
        });
        test("Persistent rotation starts provisionally at 15s without learning motion as bias",()->{
            Input f=new Input();long firstReady=0;
            for(int i=0;i<4000;i++){
                double pitch=.7*(i+1)/200;f.body.axis(1,0,0,pitch);f.body.inverseRotate(0,Config.G,0,f.a);
                f.sample(pitch,.7,f.a.x,f.a.y,f.a.z,200,true);
                if(f.s.valid&&firstReady==0)firstReady=f.t;
            }
            check(firstReady>=16_000_000_000L&&firstReady<=16_005_000_000L,"deadline must use original request time");
            check(f.s.fresh(f.t)&&f.s.calibrationQuality==Snapshot.CalibrationQuality.PROVISIONAL,"persistent approximate quality");
            check(!f.s.nativePose&&f.s.nativeRejected,"late OS events must not restart calibration");
            f.engine.copyGyroBias(f.bias);near(f.bias.norm(),0,1e-12,"motion cannot become newly estimated bias");
            near(Math.hypot(f.s.lat,f.s.longitudinal),0,.01,"gyro still tracks actual motion after approximate start");
        });
        test("Source switches cannot restart the calibration deadline",()->{
            Input f=new Input();
            for(int i=0;i<1980;i++)f.busy(); // 9.9s
            f.engine.useFallback(f.t);
            for(int i=0;i<1000;i++)f.sample(0,.7,0,Config.G,0,200,false); // 14.9s
            for(int i=0;i<30;i++)f.busy(); // Reappearing OS source, same request
            check(f.s.valid&&f.s.calibrationQuality==Snapshot.CalibrationQuality.PROVISIONAL,"handover extended deadline");
            near(f.s.calibrationElapsedSec,15,.006,"elapsed must span source changes");
        });
        test("No, stale, NaN or incorrectly scaled IMU terminates in an error instead of calibration",()->{
            for(int kind=0;kind<6;kind++){
                Input f=new Input();
                for(int i=0;i<3200;i++){
                    f.t+=5_000_000L;
                    if(kind>=2)f.engine.onGyro(f.t,0,0,0);
                    if(kind==1)f.engine.onAccel(f.t,0,Config.G,0);
                    if(kind==3)f.engine.onAccel(f.t,0,1,0);
                    if(kind==4)f.engine.onAccel(f.t,Double.NaN,Config.G,0);
                    if(kind==5&&i<20)f.engine.onAccel(f.t,0,Config.G,0);
                    if(i%100==0)f.engine.tick(f.t);
                }
                f.engine.tick(f.t);f.exchange.read(f.s);
                check(!f.s.valid&&f.s.status==Snapshot.Status.SENSOR_ERROR,"bad source cannot run or wait forever kind="+kind);
                // Explicit RESET after restored streams permits a new ordinary calibration.
                f.engine.requestRecenter(f.t);
                for(int i=0;i<260;i++)f.quiet();
                check(f.s.valid&&f.s.calibrationQuality==Snapshot.CalibrationQuality.STANDARD,"retry after error kind="+kind);
            }
        });
        test("RESET after approximate startup clears peak, filters and the APPROX quality",()->{
            Input f=new Input();for(int i=0;i<3010;i++)f.busy();
            check(f.s.valid&&f.s.calibrationQuality==Snapshot.CalibrationQuality.PROVISIONAL,"approx start");
            for(int i=0;i<150;i++)f.sample(0,0,.4*Config.G,Config.G,-.3*Config.G,200,false);
            check(f.s.peak>.3,"peak before reset");
            f.engine.requestRecenter(f.t);
            for(int i=0;i<260;i++)f.quiet();
            check(f.s.valid&&f.s.status==Snapshot.Status.RECENTERED,"reset completed");
            check(f.s.calibrationQuality==Snapshot.CalibrationQuality.STANDARD,"successful ordinary reset removes approximate marker");
            near(f.s.peak,0,.003,"peak clear");near(f.s.pitch,0,.1,"pitch clear");near(f.s.longitudinal,0,.003,"filter clear");
        });
        test("A provisional recenter retains previously committed sensor biases",()->{
            Input f=new Input();for(int i=0;i<240;i++)f.sample(0,.02,0,Config.G+.05,0,200,false);
            check(f.s.valid,"initial standard calibration");f.engine.requestRecenter(f.t);
            for(int i=0;i<3010;i++)f.sample(0,.7,0,Config.G+.05,0,200,false);
            check(f.s.valid&&f.s.calibrationQuality==Snapshot.CalibrationQuality.PROVISIONAL,"bounded recenter");
            f.engine.copyGyroBias(f.bias);near(f.bias.x,.02,1e-9,"committed gyro bias retained");
            f.engine.copyAccelBias(f.bias);near(f.bias.y,.05,1e-9,"committed accelerometer bias retained");
        });
        test("Watchdog can start provisionally from fresh data without another accel event",()->{
            Input f=new Input();for(int i=0;i<2998;i++)f.busy();
            check(!f.s.valid,"still before 15s");f.t+=20_000_000L;f.engine.tick(f.t);f.exchange.read(f.s);
            check(f.s.valid&&f.s.fresh(f.t)&&f.s.calibrationQuality==Snapshot.CalibrationQuality.PROVISIONAL,"watchdog deadline did not finish");
        });
        System.out.println("DEADLINE RESULT\t"+passed+" passed; "+failed+" failed (synthetic only)");
        if(failed!=0)throw new AssertionError(failed+" deadline regressions failed");
    }
}
