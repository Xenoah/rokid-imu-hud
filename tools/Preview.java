import dev.xenoah.hud.core.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/** Renders the production HudRenderer through Java2D, without Android or a browser. */
public final class Preview implements HudCanvas {
    private Graphics2D g;
    private final BufferedImage image;
    private final ArrayDeque<Graphics2D> stack=new ArrayDeque<>();
    Preview(int width,int height){image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);}
    private void style(float width,int alpha){g.setColor(new Color(0,255,0,alpha));g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));}
    public void clear(){g.setColor(Color.BLACK);g.fillRect(0,0,image.getWidth(),image.getHeight());}
    public void save(){stack.push(g);g=(Graphics2D)g.create();}
    public void restore(){g.dispose();g=stack.pop();}
    public void translate(float x,float y){g.translate(x,y);}
    public void scale(float f){g.scale(f,f);}
    public void rotate(float d){g.rotate(Math.toRadians(d));}
    public void clip(float l,float t,float r,float b){g.clip(new Rectangle2D.Float(l,t,r-l,b-t));}
    public void line(float x,float y,float xx,float yy,float w,int a){style(w,a);g.draw(new Line2D.Float(x,y,xx,yy));}
    public void circle(float x,float y,float r,float w,int a,boolean fill){style(w,a);Shape s=new Ellipse2D.Float(x-r,y-r,2*r,2*r);if(fill)g.fill(s);else g.draw(s);}
    public void text(String s,float x,float y,float size,int a){style(1,a);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,Math.round(size)));g.drawString(s,x,y);}
    public void text(char[] s,int n,float x,float y,float size,int a){text(new String(s,0,n),x,y,size,a);}
    public static void main(String[] args)throws Exception{
        File dir=new File(args.length==0?"artifacts":args[0]);dir.mkdirs();
        String[] names={"hud-combined-480x400","hud-level-480x400","hud-horizon-480x400","hud-gmeter-480x400","hud-safe-area-480x640","hud-calibrating-480x400","hud-stale-480x400","hud-calibration-wait-480x400"};
        for(int i=0;i<names.length;i++){
            int height=i==4?640:400;Preview p=new Preview(480,height);Snapshot s=new Snapshot();
            long now=5_000_000_000L;s.timeNs=now;s.accTimeNs=now;s.gyroTimeNs=now;s.attTimeNs=now;
            s.status=Snapshot.Status.LIVE;s.valid=true;s.mode=i==2?2:i==3?3:1;
            s.roll=i==1?0:12.5;s.pitch=i==1?0:3.2;s.lat=i==1?0:.42;s.longitudinal=i==1?0:.71;s.peak=1.28;
            if(i==5||i==7){s.status=i==5?Snapshot.Status.CALIBRATING:Snapshot.Status.WAIT_STILL;s.valid=false;s.progress=.6;
                s.ay=Config.G;s.gx=.004;s.accHz=200;s.gyroHz=200;s.nativePose=true;
                if(i==7)s.calibrationReason=Snapshot.CalibrationReason.ACCEL_MOVING;}
            if(i==6)s.accTimeNs=now-1_000_000_000L;
            new HudRenderer().draw(p,s,now,480,height,false);
            ImageIO.write(p.image,"png",new File(dir,names[i]+".png"));p.g.dispose();
        }
        System.out.println("Rendered "+names.length+" previews using production HudRenderer");
    }
}
