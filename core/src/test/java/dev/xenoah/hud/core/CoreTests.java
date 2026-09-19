package dev.xenoah.hud.core;

import java.util.Random;

/** Deterministic synthetic IMU tests; never label these as tests on physical Rokid hardware. */
public final class CoreTests {
    private static int passed;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void near(double value,double expected,double tolerance,String message){
        check(Double.isFinite(value)&&Math.abs(value-expected)<=tolerance,message+" actual="+value+" expected="+expected);
    }
    private static void test(String name,Runnable body){body.run();passed++;System.out.println("PASS\t"+name);}
    private static final class Fixture {
        final Exchange exchange=new Exchange();
        final HudEngine engine=new HudEngine(exchange,1_000_000_000L);
        final Snapshot s=new Snapshot();
        final Quat body=new Quat(),base=new Quat().axis(1,0,0,Math.PI/2),nativeQ=new Quat();
        final Quat yaw=new Quat(),pitch=new Quat(),roll=new Quat(),tmp=new Quat();
        final Vec3 raw=new Vec3(),worldA=new Vec3();
        double bx,by,bz;
        long t=1_000_000_000L;
        void pose(double p,double r,double y){
            yaw.axis(0,1,0,Math.toRadians(y));pitch.axis(1,0,0,Math.toRadians(p));roll.axis(0,0,1,Math.toRadians(-r));
            tmp.multiply(yaw,pitch);body.multiply(tmp,roll);nativeQ.multiply(base,body);
        }
        void nativeSample(double wx,double wy,double wz){
            t+=5_000_000L;
            engine.onRotation(t,nativeQ.w,nativeQ.x,nativeQ.y,nativeQ.z);
            engine.onGyro(t,0.004,-0.002,0.003);
            body.inverseRotate(wx,wy+Config.G,wz,raw);
            engine.onAccel(t,raw.x+bx,raw.y+by,raw.z+bz);exchange.read(s);
        }
        void nativeSamples(int n,double ax,double ay,double az){for(int i=0;i<n;i++)nativeSample(ax,ay,az);}
        void ready(){pose(0,0,0);nativeSamples(240,0,0,0);check(s.valid,"calibrated");}
        void rawSample(double gx,double gy,double gz,double ax,double ay,double az){
            t+=5_000_000L;engine.onGyro(t,gx,gy,gz);engine.onAccel(t,ax,ay,az);exchange.read(s);
        }
    }
    public static void main(String[] args){
        test("Static native quaternion: G approximately zero",()->{
            Fixture f=new Fixture();f.bx=.025;f.by=-.04;f.bz=.03;f.ready();
            near(f.s.lat,0,1e-6,"lat");near(f.s.longitudinal,0,1e-6,"long");
            Vec3 bias=new Vec3();f.engine.copyAccelBias(bias);near(bias.x,.025,1e-8,"acc bias x");
            f.engine.copyGyroBias(bias);near(bias.x,.004,1e-8,"gyro bias x");
        });
        test("Right and left tilt signs",()->{
            Fixture f=new Fixture();f.ready();f.pose(0,25,0);f.nativeSamples(50,0,0,0);near(f.s.roll,25,.01,"right");
            near(f.s.lat,0,.001,"right tilt gravity");f.pose(0,-25,0);f.nativeSamples(50,0,0,0);near(f.s.roll,-25,.01,"left");
        });
        test("Look up and down signs",()->{
            Fixture f=new Fixture();f.ready();f.pose(20,0,0);f.nativeSamples(50,0,0,0);near(f.s.pitch,20,.01,"up");
            near(f.s.longitudinal,0,.001,"up tilt gravity");f.pose(-20,0,0);f.nativeSamples(50,0,0,0);near(f.s.pitch,-20,.01,"down");
        });
        test("Forward braking and lateral acceleration",()->{
            Fixture f=new Fixture();f.ready();f.nativeSamples(100,.42*Config.G,0,-.71*Config.G);
            near(f.s.lat,.42,1e-4,"right acceleration");near(f.s.longitudinal,.71,1e-4,"forward acceleration");
            f.nativeSamples(100,-.42*Config.G,0,.71*Config.G);near(f.s.lat,-.42,1e-4,"left acceleration");near(f.s.longitudinal,-.71,1e-4,"braking");
            near(f.s.peak,Math.hypot(.42,.71),1e-4,"filtered horizontal resultant peak");
        });
        test("Quaternion reference transform follows reset frame, not head local axes",()->{
            Fixture f=new Fixture();f.ready();f.pose(12,18,63);f.engine.requestRecenter(f.t);f.nativeSamples(240,0,0,0);
            near(f.s.roll,0,.001,"reset roll");near(f.s.pitch,0,.001,"reset pitch");near(f.s.peak,0,1e-6,"reset peak");
            Quat reference=new Quat().set(f.body);reference.rotate(.5*Config.G,0,-.8*Config.G,f.worldA);
            f.pose(-8,-15,110);f.nativeSamples(150,f.worldA.x,f.worldA.y,f.worldA.z);
            near(f.s.lat,.5,1e-5,"lat in reference");near(f.s.longitudinal,.8,1e-5,"long in reference");
        });
        test("Pure yaw does not change horizon, even after a tilted reset",()->{
            Fixture f=new Fixture();f.ready();f.pose(17,21,0);f.engine.requestRecenter(f.t);f.nativeSamples(230,0,0,0);
            f.pose(17,21,150);f.nativeSamples(100,0,0,0);near(f.s.pitch,0,1e-6,"yaw independent pitch");near(f.s.roll,0,1e-6,"yaw independent roll");
        });
        test("Gravity removal with bias over random orientations",()->{
            Fixture f=new Fixture();f.bx=.02;f.by=.015;f.bz=-.035;f.ready();Random random=new Random(42);
            for(int i=0;i<100;i++){
                f.pose(random.nextDouble()*140-70,random.nextDouble()*300-150,random.nextDouble()*360);
                f.nativeSamples(10,0,0,0);near(f.s.rawLat,0,1e-6,"gravity lat");near(f.s.rawLong,0,1e-6,"gravity long");
            }
        });
        test("Moving calibration is rejected and recovers when still",()->{
            Fixture f=new Fixture();f.pose(0,0,0);
            for(int i=0;i<3300;i++){
                f.t+=5_000_000L;f.engine.onRotation(f.t,f.nativeQ.w,f.nativeQ.x,f.nativeQ.y,f.nativeQ.z);
                f.engine.onGyro(f.t,.2,0,0);f.engine.onAccel(f.t,0,Config.G,0);
            }
            f.exchange.read(f.s);check(!f.s.valid,"movement cannot mark ready");check(f.s.status==Snapshot.Status.WAIT_STILL,"wait status after timeout");
            f.nativeSamples(230,0,0,0);check(f.s.valid,"recovers after stillness");
        });
        test("4Hz filter: bounded delay and vibration attenuation",()->{
            Fixture f=new Fixture();f.ready();f.nativeSamples(10,Config.G,0,0);
            check(f.s.lat>.65&&f.s.lat<.8,"50ms step response");
            for(int i=0;i<200;i++)f.nativeSample(Math.sin(2*Math.PI*30*i*.005)*Config.G,0,0);
            double max=0;for(int i=0;i<200;i++){f.nativeSample(Math.sin(2*Math.PI*30*i*.005)*Config.G,0,0);max=Math.max(max,Math.abs(f.s.lat));}
            check(max<.16,"30Hz jitter attenuation");
        });
        test("Stale, reversed and NaN samples never masquerade as fresh",()->{
            Fixture f=new Fixture();f.ready();check(!f.s.fresh(f.t+300_000_000L),"stale data");
            long last=f.s.accTimeNs;f.engine.onAccel(f.t-1,123,123,123);f.engine.onAccel(f.t+1,Double.NaN,0,0);
            f.exchange.read(f.s);check(last==f.s.accTimeNs,"invalid samples ignored");
        });
        test("Fallback static one-hour drift with fixed bias and noise",()->{
            Fixture f=new Fixture();Random random=new Random(11);
            for(int i=0;i<240;i++)f.rawSample(.007,-.003,.004,0,Config.G+.03,0);
            check(f.s.valid&&!f.s.nativePose,"fallback ready");double maxTilt=0,maxG=0;
            for(int i=0;i<720_000;i++){
                f.rawSample(.007+random.nextGaussian()*.0003,-.003+random.nextGaussian()*.0003,.004+random.nextGaussian()*.0003,
                    random.nextGaussian()*.006,Config.G+.03+random.nextGaussian()*.006,random.nextGaussian()*.006);
                maxTilt=Math.max(maxTilt,Math.max(Math.abs(f.s.pitch),Math.abs(f.s.roll)));
                maxG=Math.max(maxG,Math.hypot(f.s.lat,f.s.longitudinal));
            }
            check(maxTilt<.1,"fallback drift deg="+maxTilt);check(maxG<.005,"fallback static G="+maxG);
            System.out.printf(java.util.Locale.US,"METRIC\t1h synthetic max tilt %.6f deg; max horizontal G %.6f%n",maxTilt,maxG);
        });
        test("Fallback pitch and roll integration signs",()->{
            Fixture f=new Fixture();for(int i=0;i<240;i++)f.rawSample(0,0,0,0,Config.G,0);
            Vec3 a=new Vec3();Quat q=new Quat();
            for(int i=1;i<=100;i++){double angle=Math.toRadians(20*i/100.0);q.axis(1,0,0,angle);q.inverseRotate(0,Config.G,0,a);f.rawSample(Math.toRadians(40),0,0,a.x,a.y,a.z);}
            for(int i=0;i<80;i++)f.rawSample(0,0,0,a.x,a.y,a.z);
            near(f.s.pitch,20,.3,"fallback up");
            Fixture right=new Fixture();for(int i=0;i<240;i++)right.rawSample(0,0,0,0,Config.G,0);
            for(int i=1;i<=100;i++){q.axis(0,0,1,Math.toRadians(-20*i/100.0));q.inverseRotate(0,Config.G,0,a);right.rawSample(0,0,Math.toRadians(-40),a.x,a.y,a.z);}
            for(int i=0;i<80;i++)right.rawSample(0,0,0,a.x,a.y,a.z);near(right.s.roll,20,.3,"fallback right");
        });
        test("Fallback rejects large sustained linear acceleration as gravity",()->{
            Fixture f=new Fixture();for(int i=0;i<240;i++)f.rawSample(0,0,0,0,Config.G,0);
            for(int i=0;i<1000;i++)f.rawSample(0,0,0,.5*Config.G,Config.G,-.6*Config.G);
            near(f.s.pitch,0,.001,"no false pitch");near(f.s.lat,.5,.001,"fallback lat");near(f.s.longitudinal,.6,.001,"fallback forward");
        });
        test("Native to fallback switch forces a new reference",()->{
            Fixture f=new Fixture();f.ready();f.engine.useFallback(f.t);f.exchange.read(f.s);check(!f.s.valid,"invalidate old frame");
            for(int i=0;i<250;i++)f.rawSample(0,0,0,0,Config.G,0);
            check(f.s.valid&&!f.s.nativePose,"fallback recovers");near(f.s.peak,0,1e-6,"new reference peak");
        });
        test("Signed axis permutation transforms vectors and quaternion consistently",()->{
            AxisMap map=new AxisMap(2,-1,3);Vec3 a=new Vec3(),b=new Vec3();Quat q=new Quat();
            map.vector(2,3,4,a);near(a.x,3,0,"remap x");near(a.y,-2,0,"remap y");
            map.quaternion(1,0,0,0,q);q.rotate(a.x,a.y,a.z,b);
            near(b.x,2,1e-6,"quaternion x");near(b.y,3,1e-6,"quaternion y");near(b.z,4,1e-6,"quaternion z");
            boolean refused=false;try{new AxisMap(-1,2,3);}catch(IllegalArgumentException expected){refused=true;}check(refused,"reflections refused");
        });
        test("Viewport preserves the requested 480x400 and official safe area",()->{
            Viewport v=new Viewport();v.fit(480,400);near(v.scale,1,0,"400 scale");near(v.y,0,0,"400 y");
            v.fit(480,640);near(v.scale,1,0,"640 scale");near(v.y,120,0,"640 y");check(v.y>=80&&v.y+400<=560,"safe area");
        });
        System.out.println("RESULT\t"+passed+" tests passed (synthetic data; no hardware attached)");
    }
}
